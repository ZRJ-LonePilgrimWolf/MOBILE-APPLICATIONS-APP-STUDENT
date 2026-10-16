// Student profile + lecturer approvals.
//
//   GET  /student/me                 (student)   the logged-in student's own real profile
//   GET  /approvals                  (lecturer)  self-registered students waiting for approval + groups with counts
//   POST /approvals/:id/approve      (lecturer)  approve (optionally moving them to another lab group first)
//   POST /approvals/:id/reject       (lecturer)  reject: frees their lab-group place
//
// Every change runs in a transaction and locks the student row, so two lecturers cannot handle
// the same request at once, and an approved/rejected request can never come back as "pending".
const express = require('express');
const db = require('./database');
const { authenticateToken, requireRole, MAX_LAB_GROUP_MEMBERS } = require('./middleware');

const router = express.Router();
const pool = db.promise();

class Reject extends Error {
    constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

function send(res, e) {
    if (e instanceof Reject) return res.status(e.status).json({ code: e.code, message: e.message });
    console.error('approvals error:', e.message);
    return res.status(500).json({ code: 'SERVER_ERROR', message: 'Something went wrong on the server' });
}

// ---------------------------------------------------------------- student: my profile

router.get('/student/me', authenticateToken, async (req, res) => {
    try {
        if (req.user.role !== 'student') throw new Reject(403, 'NOT_STUDENT', 'Only students have a student profile');
        const [rows] = await pool.query(
            `SELECT s.student_id, s.student_name, s.student_number, s.approval_status, s.is_deleted,
                    s.lab_group_id, a.username, a.email, p.program_name, g.group_name,
                    (SELECT COUNT(*) FROM students x
                      WHERE x.lab_group_id = s.lab_group_id AND x.is_deleted = 0) AS group_members
               FROM students s
               JOIN accounts a  ON a.account_id = s.account_id
               LEFT JOIN program p  ON p.program_id = s.program_id
               LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id
              WHERE s.account_id = ?`, [req.user.account_id]);
        if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'No student profile is linked to this account');
        const s = rows[0];
        res.json({ ...s, group_members: Number(s.group_members), max_members: MAX_LAB_GROUP_MEMBERS });
    } catch (e) { send(res, e); }
});

// ---------------------------------------------------------------- lecturer: pending approvals

router.get('/approvals', authenticateToken, requireRole('lecturer'), async (req, res) => {
    try {
        const [pending] = await pool.query(
            `SELECT s.student_id, s.student_name, s.student_number, s.lab_group_id, s.created_at,
                    a.email, p.program_name, g.group_name
               FROM students s
               JOIN accounts a  ON a.account_id = s.account_id
               LEFT JOIN program p  ON p.program_id = s.program_id
               LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id
              WHERE s.approval_status = 'PENDING' AND s.is_deleted = 0
              ORDER BY s.created_at, s.student_id`);
        const [groups] = await pool.query(
            `SELECT g.lab_group_id, g.group_name,
                    (SELECT COUNT(*) FROM students s
                      WHERE s.lab_group_id = g.lab_group_id AND s.is_deleted = 0) AS member_count
               FROM labgroup g ORDER BY g.group_name`);
        res.json({
            count: pending.length,
            pending,
            groups: groups.map(g => ({ ...g, member_count: Number(g.member_count), max_members: MAX_LAB_GROUP_MEMBERS }))
        });
    } catch (e) { send(res, e); }
});

// Locks the student row and checks it is still waiting. Returns the row.
async function lockPending(conn, studentId) {
    const [rows] = await conn.query(
        'SELECT student_id, student_name, lab_group_id, approval_status, is_deleted FROM students WHERE student_id = ? FOR UPDATE',
        [studentId]);
    if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'Student not found');
    const s = rows[0];
    if (s.is_deleted || s.approval_status !== 'PENDING') {
        throw new Reject(409, 'ALREADY_HANDLED', `This request was already handled (${String(s.approval_status).toLowerCase()})`);
    }
    return s;
}

router.post('/approvals/:id/approve', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const studentId = Number(req.params.id);
    const newGroup = req.body && req.body.lab_group_id ? Number(req.body.lab_group_id) : null;
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const s = await lockPending(conn, studentId);

        // Optional: move to another lab group first (same 15-student rule, group row locked while counting)
        if (newGroup && newGroup !== s.lab_group_id) {
            const [g] = await conn.query('SELECT lab_group_id, group_name FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [newGroup]);
            if (!g.length) throw new Reject(400, 'GROUP_NOT_FOUND', 'Lab group not found');
            const [[{ n }]] = await conn.query(
                'SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [newGroup]);
            if (n >= MAX_LAB_GROUP_MEMBERS) {
                throw new Reject(409, 'GROUP_FULL', `${g[0].group_name} is full (${MAX_LAB_GROUP_MEMBERS} students). Choose another group.`);
            }
            await conn.query('UPDATE students SET lab_group_id = ? WHERE student_id = ?', [newGroup, studentId]);
        }

        await conn.query(
            "UPDATE students SET approval_status = 'APPROVED', version = version + 1 WHERE student_id = ?", [studentId]);
        await conn.commit();
        res.json({ code: 'APPROVED', message: `${s.student_name} approved` });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

router.post('/approvals/:id/reject', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const studentId = Number(req.params.id);
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const s = await lockPending(conn, studentId);
        // is_deleted = 1 frees their place in the lab group (places only count active students)
        await conn.query(
            "UPDATE students SET approval_status = 'REJECTED', is_deleted = 1, version = version + 1 WHERE student_id = ?",
            [studentId]);
        await conn.commit();
        res.json({ code: 'REJECTED', message: `${s.student_name}'s registration rejected` });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

module.exports = router;