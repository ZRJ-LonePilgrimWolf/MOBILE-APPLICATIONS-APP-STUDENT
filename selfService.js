// Student self-service + lecturer handling of student requests.
//
// STUDENT (only ever their OWN record, found from the login token - never from the request body)
//   PUT  /student/me                       edit own name and programme   body: { student_name, program_id, base_version }
//   GET  /student/me/requests              own group-change / number-correction requests
//   POST /student/me/requests              new request   body: { type:'GROUP_CHANGE', lab_group_id, reason }
//                                                              or { type:'NUMBER_CORRECTION', new_number, reason }
//   POST /student/me/requests/:id/cancel   cancel own PENDING request
//
// LECTURER
//   GET  /requests?status=PENDING          requests with student + group details and group counts
//   POST /requests/:id/approve             apply it (group change obeys the 15-student limit)
//   POST /requests/:id/reject              body: { note }
//
// Every change runs in a transaction with row locks (FOR UPDATE), so two lecturers or two phones
// can never apply the same request twice or squeeze a 16th student into a group.
const express = require('express');
const db = require('./database');
const { authenticateToken, requireRole, MAX_LAB_GROUP_MEMBERS, nameRegex, studentNumberRegex } = require('./middleware');

const router = express.Router();
const pool = db.promise();

class Reject extends Error {
    constructor(status, code, message, extra) { super(message); this.status = status; this.code = code; this.extra = extra; }
}

function send(res, e) {
    if (e instanceof Reject) return res.status(e.status).json({ code: e.code, message: e.message, ...(e.extra || {}) });
    console.error('selfService error:', e.message);
    return res.status(500).json({ code: 'SERVER_ERROR', message: 'Something went wrong on the server' });
}

const onlyStudent = (req, res, next) =>
    req.user && req.user.role === 'student'
        ? next()
        : res.status(403).json({ code: 'NOT_STUDENT', message: 'Only students can do this' });

// The logged-in student's own row, locked for the rest of the transaction.
async function lockMe(conn, accountId) {
    const [rows] = await conn.query(
        'SELECT * FROM students WHERE account_id = ? FOR UPDATE', [accountId]);
    if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'No student profile is linked to this account');
    const s = rows[0];
    if (s.is_deleted || s.approval_status === 'REJECTED') {
        throw new Reject(403, 'NOT_ACTIVE', 'This student account is not active');
    }
    return s;
}

async function currentProfile(conn, studentId) {
    const [rows] = await conn.query(
        `SELECT s.student_id, s.student_name, s.student_number, s.program_id, s.lab_group_id, s.version,
                p.program_name, g.group_name
           FROM students s
           LEFT JOIN program p  ON p.program_id = s.program_id
           LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id
          WHERE s.student_id = ?`, [studentId]);
    return rows[0];
}

// ======================================================================= STUDENT

router.put('/student/me', authenticateToken, onlyStudent, async (req, res) => {
    const body = req.body || {};
    const name = String(body.student_name || '').trim();
    const programId = Number(body.program_id);
    const baseVersion = body.base_version;

    if (!nameRegex.test(name)) {
        return res.status(400).json({ code: 'VALIDATION', field: 'student_name',
            message: 'Name must be 2-100 letters (spaces, hyphens and apostrophes allowed)' });
    }
    if (!Number.isInteger(programId) || programId <= 0) {
        return res.status(400).json({ code: 'VALIDATION', field: 'program_id', message: 'Choose a programme' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const [p] = await conn.query('SELECT program_id FROM program WHERE program_id = ?', [programId]);
        if (!p.length) throw new Reject(400, 'VALIDATION', 'Unknown programme', { field: 'program_id' });

        const me = await lockMe(conn, req.user.account_id);

        // Someone (e.g. a lecturer) changed this record after the phone loaded it: don't overwrite their work.
        if (baseVersion !== undefined && baseVersion !== null && Number(baseVersion) !== me.version) {
            throw new Reject(409, 'CONFLICT', 'Your details were changed by someone else. Review the current values and try again.',
                { current: await currentProfile(conn, me.student_id) });
        }

        await conn.query(
            'UPDATE students SET student_name = ?, program_id = ?, version = version + 1 WHERE student_id = ?',
            [name, programId, me.student_id]);
        const updated = await currentProfile(conn, me.student_id);
        await conn.commit();
        res.json({ code: 'UPDATED', message: 'Your details were saved', student: updated });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

router.get('/student/me/requests', authenticateToken, onlyStudent, async (req, res) => {
    try {
        const [rows] = await pool.query(
            `SELECT r.request_id, r.type, r.status, r.reason, r.lecturer_note, r.created_at, r.decided_at,
                    r.requested_group_id, g.group_name AS requested_group_name, r.requested_number
               FROM student_requests r
               JOIN students s ON s.student_id = r.student_id
               LEFT JOIN labgroup g ON g.lab_group_id = r.requested_group_id
              WHERE s.account_id = ?
              ORDER BY r.created_at DESC, r.request_id DESC
              LIMIT 50`, [req.user.account_id]);
        res.json({ requests: rows });
    } catch (e) { send(res, e); }
});

router.post('/student/me/requests', authenticateToken, onlyStudent, async (req, res) => {
    const body = req.body || {};
    const type = String(body.type || '');
    const reason = String(body.reason || '').trim().slice(0, 300);

    if (type !== 'GROUP_CHANGE' && type !== 'NUMBER_CORRECTION') {
        return res.status(400).json({ code: 'VALIDATION', field: 'type', message: 'Unknown request type' });
    }
    if (reason.length < 5) {
        return res.status(400).json({ code: 'VALIDATION', field: 'reason', message: 'Please give a short reason (at least 5 characters)' });
    }

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const me = await lockMe(conn, req.user.account_id);   // lock = one request at a time per student

        const [open] = await conn.query(
            "SELECT request_id FROM student_requests WHERE student_id = ? AND type = ? AND status = 'PENDING'",
            [me.student_id, type]);
        if (open.length) throw new Reject(409, 'ALREADY_PENDING', 'You already have a pending request of this type');

        let groupId = null, newNumber = null;
        if (type === 'GROUP_CHANGE') {
            groupId = Number(body.lab_group_id);
            const [g] = await conn.query('SELECT lab_group_id FROM labgroup WHERE lab_group_id = ?', [groupId]);
            if (!g.length) throw new Reject(400, 'VALIDATION', 'Choose a lab group', { field: 'lab_group_id' });
            if (groupId === me.lab_group_id) throw new Reject(400, 'VALIDATION', 'You are already in that group', { field: 'lab_group_id' });
        } else {
            newNumber = String(body.new_number || '').trim();          // trim outer spaces only
            if (!studentNumberRegex.test(newNumber)) {
                throw new Reject(400, 'VALIDATION', 'Student number must be exactly 9 digits', { field: 'new_number' });
            }
            if (newNumber === me.student_number) throw new Reject(400, 'VALIDATION', 'That is already your number', { field: 'new_number' });
            // Numbers of deleted students stay reserved too, so this checks every row.
            const [used] = await conn.query('SELECT 1 FROM students WHERE student_number = ?', [newNumber]);
            if (used.length) throw new Reject(409, 'NUMBER_TAKEN', 'That student number is already in use', { field: 'new_number' });
        }

        const [r] = await conn.query(
            `INSERT INTO student_requests (student_id, type, requested_group_id, requested_number, reason)
             VALUES (?, ?, ?, ?, ?)`, [me.student_id, type, groupId, newNumber, reason]);
        await conn.commit();
        res.status(201).json({ code: 'REQUESTED', message: 'Request sent to your lecturer', request_id: r.insertId });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

router.post('/student/me/requests/:id/cancel', authenticateToken, onlyStudent, async (req, res) => {
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const [rows] = await conn.query(
            `SELECT r.request_id, r.status FROM student_requests r
               JOIN students s ON s.student_id = r.student_id
              WHERE r.request_id = ? AND s.account_id = ? FOR UPDATE`,
            [Number(req.params.id), req.user.account_id]);
        if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'Request not found');   // also hides other students' requests
        if (rows[0].status !== 'PENDING') throw new Reject(409, 'ALREADY_HANDLED', 'This request was already handled');
        await conn.query("UPDATE student_requests SET status = 'CANCELLED', decided_at = NOW() WHERE request_id = ?", [rows[0].request_id]);
        await conn.commit();
        res.json({ code: 'CANCELLED', message: 'Request cancelled' });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

// ======================================================================= LECTURER

router.get('/requests', authenticateToken, requireRole('lecturer'), async (req, res) => {
    try {
        const status = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].includes(req.query.status) ? req.query.status : 'PENDING';
        const [rows] = await pool.query(
            `SELECT r.request_id, r.type, r.status, r.reason, r.created_at, r.requested_number,
                    r.requested_group_id, rg.group_name AS requested_group_name,
                    s.student_id, s.student_name, s.student_number, s.lab_group_id, cg.group_name AS current_group_name,
                    (SELECT COUNT(*) FROM students x WHERE x.lab_group_id = r.requested_group_id AND x.is_deleted = 0) AS requested_group_members
               FROM student_requests r
               JOIN students s ON s.student_id = r.student_id
               LEFT JOIN labgroup rg ON rg.lab_group_id = r.requested_group_id
               LEFT JOIN labgroup cg ON cg.lab_group_id = s.lab_group_id
              WHERE r.status = ?
              ORDER BY r.created_at, r.request_id
              LIMIT 200`, [status]);
        res.json({
            count: rows.length,
            max_members: MAX_LAB_GROUP_MEMBERS,
            requests: rows.map(r => ({ ...r, requested_group_members: Number(r.requested_group_members) }))
        });
    } catch (e) { send(res, e); }
});

async function lockPendingRequest(conn, id) {
    const [rows] = await conn.query('SELECT * FROM student_requests WHERE request_id = ? FOR UPDATE', [id]);
    if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'Request not found');
    if (rows[0].status !== 'PENDING') throw new Reject(409, 'ALREADY_HANDLED', `This request was already ${rows[0].status.toLowerCase()}`);
    return rows[0];
}

router.post('/requests/:id/approve', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const r = await lockPendingRequest(conn, Number(req.params.id));

        if (r.type === 'GROUP_CHANGE') {
            // Same lock order as every other group change: group row first, then the student row.
            const [g] = await conn.query('SELECT group_name FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [r.requested_group_id]);
            if (!g.length) throw new Reject(400, 'GROUP_NOT_FOUND', 'Lab group not found');
            const [srows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [r.student_id]);
            const s = srows[0];
            if (!s || s.is_deleted) throw new Reject(410, 'DELETED', 'This student was deleted');
            if (s.lab_group_id !== r.requested_group_id) {
                const [[{ n }]] = await conn.query(
                    'SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [r.requested_group_id]);
                if (n >= MAX_LAB_GROUP_MEMBERS) {
                    // Nothing changes: the student keeps the old group and the request stays PENDING.
                    throw new Reject(409, 'GROUP_FULL', `${g[0].group_name} is full (${MAX_LAB_GROUP_MEMBERS} students). The student keeps the current group.`);
                }
                await conn.query('UPDATE students SET lab_group_id = ?, version = version + 1 WHERE student_id = ?',
                    [r.requested_group_id, s.student_id]);
            }
        } else {
            const [srows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [r.student_id]);
            const s = srows[0];
            if (!s || s.is_deleted) throw new Reject(410, 'DELETED', 'This student was deleted');
            try {
                await conn.query('UPDATE students SET student_number = ?, version = version + 1 WHERE student_id = ?',
                    [r.requested_number, s.student_id]);
            } catch (e) {
                if (e.code === 'ER_DUP_ENTRY') throw new Reject(409, 'NUMBER_TAKEN', 'That student number is now used by another student');
                throw e;
            }
        }

        await conn.query(
            "UPDATE student_requests SET status = 'APPROVED', decided_by = ?, decided_at = NOW() WHERE request_id = ?",
            [req.user.account_id, r.request_id]);
        await conn.commit();
        res.json({ code: 'APPROVED', message: 'Request approved' });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

router.post('/requests/:id/reject', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const note = String((req.body || {}).note || '').trim().slice(0, 300) || null;
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const r = await lockPendingRequest(conn, Number(req.params.id));
        await conn.query(
            "UPDATE student_requests SET status = 'REJECTED', lecturer_note = ?, decided_by = ?, decided_at = NOW() WHERE request_id = ?",
            [note, req.user.account_id, r.request_id]);
        await conn.commit();
        res.json({ code: 'REJECTED', message: 'Request rejected' });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        send(res, e);
    } finally { conn.release(); }
});

module.exports = router;
