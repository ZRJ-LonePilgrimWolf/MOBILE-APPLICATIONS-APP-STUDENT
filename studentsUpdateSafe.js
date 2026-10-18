// Safe edit / transfer / delete + filtered, paged reads for the LECTURER. Mount BEFORE students.js.
//
//   GET    /students        ?search=&program_id=&lab_group_id=(id|unassigned)&page=1&page_size=20
//                           -> { count, total, page, page_size, has_more, students:[...] }
//   GET    /students/:id    one active student
//   GET    /labgroups       groups with member counts (+ an "Unassigned" total)
//   PUT    /students/:id    edit / transfer / unassign   body: { student_name, student_number, program_id,
//                                                               lab_group_id (null = Unassigned), base_version, operation_id }
//   DELETE /students/:id    soft delete (idempotent)    body/query: { operation_id }
//
// Every change runs in a transaction with row locks, obeys the 15-student limit, checks the record
// version (no silent overwrite) and stores an operation receipt so a retried request acts only once.
const express = require('express');
const db = require('./database');
const receipts = require('./receipts');
const { nameRegex, studentNumberRegex, MAX_LAB_GROUP_MEMBERS, authenticateToken, requireRole } = require('./middleware');

const router = express.Router();
const pool = db.promise();

class Reject extends Error {
    constructor(status, body) { super(body.message); this.status = status; this.body = body; }
}

const SELECT_STUDENT = `
  SELECT s.student_id, s.account_id, s.student_name, s.student_number, s.program_id, s.lab_group_id,
         s.version, s.is_deleted, s.approval_status, a.email, p.program_name, g.group_name
  FROM students s
  JOIN accounts a ON a.account_id = s.account_id
  LEFT JOIN program p ON p.program_id = s.program_id
  LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id`;

const escapeLike = (s) => s.replace(/[\\%_]/g, (c) => '\\' + c);

// ---------- READS (hide deleted, show version) ----------
router.get('/students', authenticateToken, requireRole('lecturer'), async (req, res) => {
    try {
        const q = req.query;
        const search = String(q.search || '').trim().slice(0, 100);
        const where = ['s.is_deleted = 0'];
        const params = [];

        if (search) {
            const like = escapeLike(search);
            where.push('(s.student_name LIKE ? OR s.student_number LIKE ?)');
            params.push(`%${like}%`, `${like}%`);
        }
        // New exact filters (used by the app)
        if (q.program_id) { where.push('s.program_id = ?'); params.push(Number(q.program_id)); }
        if (q.lab_group_id === 'unassigned') { where.push('s.lab_group_id IS NULL'); }
        else if (q.lab_group_id) { where.push('s.lab_group_id = ?'); params.push(Number(q.lab_group_id)); }
        // Old name-based filters (kept so older app builds still work)
        if (q.labgroup) { where.push('g.group_name LIKE ?'); params.push(`%${escapeLike(String(q.labgroup))}%`); }
        if (q.program) { where.push('p.program_name LIKE ?'); params.push(`%${escapeLike(String(q.program))}%`); }

        const pageSize = Math.min(Math.max(parseInt(q.page_size, 10) || 20, 1), 100);
        const page = Math.max(parseInt(q.page, 10) || 1, 1);
        const whereSql = ' WHERE ' + where.join(' AND ');

        const [[{ total }]] = await pool.query(
            `SELECT COUNT(*) AS total FROM students s
               LEFT JOIN program p ON p.program_id = s.program_id
               LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id` + whereSql, params);
        const [rows] = await pool.query(
            SELECT_STUDENT + whereSql + ' ORDER BY s.student_name, s.student_id LIMIT ? OFFSET ?',
            [...params, pageSize, (page - 1) * pageSize]);

        res.json({
            count: rows.length,
            total: Number(total),
            page,
            page_size: pageSize,
            has_more: page * pageSize < Number(total),
            students: rows
        });
    } catch (e) {
        console.error('GET /students failed:', e.message);
        res.status(500).json({ message: 'Database error' });
    }
});

router.get('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    db.query(SELECT_STUDENT + ' WHERE s.student_id = ? AND s.is_deleted = 0', [req.params.id], (err, rows) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        if (!rows.length) return res.status(404).json({ code: 'NOT_FOUND', message: 'Student not found' });
        res.json(rows[0]);
    });
});

router.get('/labgroups', authenticateToken, async (req, res) => {
    try {
        const [rows] = await pool.query(`SELECT g.lab_group_id, g.group_name,
              (SELECT COUNT(*) FROM students s WHERE s.lab_group_id = g.lab_group_id AND s.is_deleted = 0) AS member_count
              FROM labgroup g ORDER BY g.group_name`);
        const [[{ n }]] = await pool.query('SELECT COUNT(*) AS n FROM students WHERE lab_group_id IS NULL AND is_deleted = 0');
        res.json({
            max_members: MAX_LAB_GROUP_MEMBERS,
            unassigned: Number(n),
            groups: rows.map(r => ({ ...r, member_count: Number(r.member_count), max_members: MAX_LAB_GROUP_MEMBERS }))
        });
    } catch (e) {
        res.status(500).json({ message: 'Database error' });
    }
});

// ---------- shared transaction runner ----------
async function runOp(req, res, work) {
    const operation_id = (req.body && req.body.operation_id) || req.query.operation_id;
    const hash = receipts.hashOf({ m: req.method, id: req.params.id, b: { ...(req.body || {}), operation_id: undefined } });
    const conn = await pool.getConnection();
    try {
        const replay = await receipts.replay(conn, operation_id, hash);
        if (replay) return res.status(replay.status).json(replay.body);
        await conn.beginTransaction();
        let status, body;
        try {
            ({ status, body } = await work(conn));
        } catch (e) {
            if (!(e instanceof Reject)) throw e;
            status = e.status; body = e.body;
        }
        if (operation_id) {
            await conn.query('INSERT INTO operations (operation_id, account_id, http_status, response_body) VALUES (?,?,?,?)',
                [operation_id, req.user.account_id, status, receipts.pack(body, hash)]);
        }
        await conn.commit();
        return res.status(status).json(body);
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        if (e.code === 'ER_DUP_ENTRY' && operation_id) {
            const again = await receipts.replay(conn, operation_id, hash);
            if (again) return res.status(again.status).json(again.body);
        }
        if (e.code === 'ER_DUP_ENTRY') return res.status(409).json({ code: 'DUPLICATE', field: 'student_number', message: 'Student number already exists' });
        console.error(e);
        return res.status(500).json({ message: 'Server error' });
    } finally { conn.release(); }
}

// ---------- PUT: edit / transfer / unassign ----------
router.put('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    const body = req.body || {};
    const student_name = String(body.student_name || '').trim();
    const student_number = String(body.student_number || '').trim();   // trim outer spaces only
    const program_id = Number(body.program_id);
    const newGroup = (body.lab_group_id === null || body.lab_group_id === '' || body.lab_group_id === undefined)
        ? null : Number(body.lab_group_id);
    const base_version = body.base_version;

    if (!nameRegex.test(student_name)) {
        return res.status(400).json({ code: 'VALIDATION', field: 'student_name', message: 'Name must be 2-100 letters (spaces, hyphens and apostrophes allowed)' });
    }
    if (!studentNumberRegex.test(student_number)) {
        return res.status(400).json({ code: 'VALIDATION', field: 'student_number', message: 'Student number must be exactly 9 digits' });
    }
    if (!Number.isInteger(program_id) || program_id <= 0) {
        return res.status(400).json({ code: 'VALIDATION', field: 'program_id', message: 'Choose a programme' });
    }

    return runOp(req, res, async (conn) => {
        const id = Number(req.params.id);
        const [cur0] = await conn.query('SELECT lab_group_id FROM students WHERE student_id = ?', [id]);
        if (!cur0.length) throw new Reject(404, { code: 'NOT_FOUND', message: 'Student not found' });

        const [p] = await conn.query('SELECT program_id FROM program WHERE program_id = ?', [program_id]);
        if (!p.length) throw new Reject(400, { code: 'VALIDATION', field: 'program_id', message: 'Unknown programme' });

        // Lock the old and new groups in ID order (same order everywhere = no deadlock)
        const ids = [...new Set([cur0[0].lab_group_id, newGroup].filter(g => g !== null))].sort((a, b) => a - b);
        for (const g of ids) {
            const [r] = await conn.query('SELECT lab_group_id FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [g]);
            if (!r.length) throw new Reject(400, { code: 'VALIDATION', field: 'lab_group_id', message: 'Lab group does not exist' });
        }
        const [rows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [id]);
        const s = rows[0];
        if (s.is_deleted) throw new Reject(410, { code: 'DELETED', message: 'This student was deleted' });

        if (base_version !== undefined && base_version !== null && Number(base_version) !== s.version) {
            const [cur] = await conn.query(SELECT_STUDENT + ' WHERE s.student_id = ?', [id]);
            throw new Reject(409, { code: 'CONFLICT', message: 'Someone else changed this student', current: cur[0] });
        }
        if (newGroup !== null && newGroup !== s.lab_group_id) {
            const [[c]] = await conn.query('SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [newGroup]);
            if (c.n >= MAX_LAB_GROUP_MEMBERS) {
                throw new Reject(409, { code: 'GROUP_FULL', field: 'lab_group_id', message: `That group is full (${MAX_LAB_GROUP_MEMBERS} students). The student stays in the old group.` });
            }
        }
        // Numbers stay reserved even after deletion, so check every row
        const [dup] = await conn.query('SELECT student_id FROM students WHERE student_number = ? AND student_id <> ?', [student_number, id]);
        if (dup.length) throw new Reject(409, { code: 'DUPLICATE', field: 'student_number', message: 'Student number already exists' });

        await conn.query(`UPDATE students SET student_name=?, student_number=?, program_id=?, lab_group_id=?, version=version+1 WHERE student_id=?`,
            [student_name, student_number, program_id, newGroup, id]);
        const [after] = await conn.query(SELECT_STUDENT + ' WHERE s.student_id = ?', [id]);
        return { status: 200, body: { message: 'Student updated', student: after[0] } };
    });
});

// ---------- DELETE: soft delete, idempotent ----------
router.delete('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    return runOp(req, res, async (conn) => {
        const id = Number(req.params.id);
        const [rows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [id]);
        if (!rows.length) throw new Reject(404, { code: 'NOT_FOUND', message: 'Student not found' });
        if (rows[0].is_deleted) return { status: 200, body: { message: 'Already deleted', student_id: id } };
        // Hide the record, release its place (counts ignore deleted rows), keep the number reserved, disable the login
        await conn.query('UPDATE students SET is_deleted=1, version=version+1 WHERE student_id=?', [id]);
        await conn.query("UPDATE accounts SET password = CONCAT('DISABLED:', password) WHERE account_id=? AND password NOT LIKE 'DISABLED:%'", [rows[0].account_id]);
        return { status: 200, body: { message: 'Student deleted', student_id: id } };
    });
});

module.exports = router;
