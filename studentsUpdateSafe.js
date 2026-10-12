// Safe edit / transfer / delete + filtered reads. Mount BEFORE students.js.
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
         s.version, s.is_deleted, a.email, p.program_name, g.group_name
  FROM students s
  JOIN accounts a ON a.account_id = s.account_id
  LEFT JOIN program p ON p.program_id = s.program_id
  LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id`;

// ---------- READS (hide deleted, show version) ----------
router.get('/students', authenticateToken, requireRole('lecturer'), (req, res) => {
    const search = (req.query.search || '').trim();
    const group = (req.query.labgroup || '').trim();
    const program = (req.query.program || '').trim();
    const where = ['s.is_deleted = 0'];
    const params = [];
    if (search) { where.push('(s.student_name LIKE ? OR s.student_number LIKE ?)'); params.push(`%${search}%`, `%${search}%`); }
    if (group) { where.push('g.group_name LIKE ?'); params.push(`%${group}%`); }
    if (program) { where.push('p.program_name LIKE ?'); params.push(`%${program}%`); }
    db.query(SELECT_STUDENT + ' WHERE ' + where.join(' AND ') + ' ORDER BY s.student_name', params, (err, rows) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        res.json({ count: rows.length, students: rows });   // the Android app reads { count, students }
    });
});

router.get('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    db.query(SELECT_STUDENT + ' WHERE s.student_id = ? AND s.is_deleted = 0', [req.params.id], (err, rows) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        if (!rows.length) return res.status(404).json({ code: 'NOT_FOUND', message: 'Student not found' });
        res.json(rows[0]);
    });
});

router.get('/labgroups', authenticateToken, (req, res) => {
    db.query(`SELECT g.lab_group_id, g.group_name,
              (SELECT COUNT(*) FROM students s WHERE s.lab_group_id = g.lab_group_id AND s.is_deleted = 0) AS member_count
              FROM labgroup g ORDER BY g.group_name`, (err, rows) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        res.json(rows.map(r => ({ ...r, max_members: MAX_LAB_GROUP_MEMBERS })));
    });
});

// ---------- shared transaction runner ----------
async function runOp(req, res, work) {
    const operation_id = req.body && req.body.operation_id;
    const hash = receipts.hashOf({ m: req.method, id: req.params.id, b: { ...req.body, operation_id: undefined } });
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
        if (e.code === 'ER_DUP_ENTRY') return res.status(409).json({ code: 'DUPLICATE', message: 'Student number already exists' });
        console.error(e);
        return res.status(500).json({ message: 'Server error' });
    } finally { conn.release(); }
}

// ---------- PUT: edit / transfer ----------
router.put('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    const { student_name, student_number, program_id, lab_group_id, base_version } = req.body;
    if (!student_name || !student_number || !program_id || !lab_group_id) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Please provide all required fields' });
    }
    if (!nameRegex.test(student_name)) return res.status(400).json({ code: 'VALIDATION', message: 'Invalid student name' });
    if (!studentNumberRegex.test(String(student_number))) return res.status(400).json({ code: 'VALIDATION', message: 'Student number must be 9 digits' });

    return runOp(req, res, async (conn) => {
        const id = req.params.id;
        const [cur0] = await conn.query('SELECT lab_group_id FROM students WHERE student_id = ?', [id]);
        if (!cur0.length) throw new Reject(404, { code: 'NOT_FOUND', message: 'Student not found' });

        // lock both groups in ID order (avoids deadlock)
        const ids = [...new Set([cur0[0].lab_group_id, Number(lab_group_id)])].sort((a, b) => a - b);
        for (const g of ids) {
            const [r] = await conn.query('SELECT lab_group_id FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [g]);
            if (!r.length) throw new Reject(400, { code: 'VALIDATION', message: 'Lab group does not exist' });
        }
        const [rows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [id]);
        const s = rows[0];
        if (s.is_deleted) throw new Reject(410, { code: 'DELETED', message: 'This student was deleted' });

        if (base_version !== undefined && Number(base_version) !== s.version) {
            const [cur] = await conn.query(SELECT_STUDENT + ' WHERE s.student_id = ?', [id]);
            throw new Reject(409, { code: 'CONFLICT', message: 'Someone else changed this student', current: cur[0] });
        }
        if (Number(lab_group_id) !== s.lab_group_id) {
            const [[c]] = await conn.query('SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [lab_group_id]);
            if (c.n >= MAX_LAB_GROUP_MEMBERS) throw new Reject(409, { code: 'GROUP_FULL', message: 'Target group is full; student stays in the old group' });
        }
        const [dup] = await conn.query('SELECT student_id FROM students WHERE student_number = ? AND student_id <> ?', [student_number, id]);
        if (dup.length) throw new Reject(409, { code: 'DUPLICATE', message: 'Student number already exists' });

        await conn.query(`UPDATE students SET student_name=?, student_number=?, program_id=?, lab_group_id=?, version=version+1 WHERE student_id=?`,
            [student_name, student_number, program_id, lab_group_id, id]);
        const [after] = await conn.query(SELECT_STUDENT + ' WHERE s.student_id = ?', [id]);
        return { status: 200, body: { message: 'Student updated', student: after[0] } };
    });
});

// ---------- DELETE: soft delete, idempotent ----------
router.delete('/students/:id', authenticateToken, requireRole('lecturer'), (req, res) => {
    return runOp(req, res, async (conn) => {
        const id = req.params.id;
        const [rows] = await conn.query('SELECT * FROM students WHERE student_id = ? FOR UPDATE', [id]);
        if (!rows.length) throw new Reject(404, { code: 'NOT_FOUND', message: 'Student not found' });
        if (rows[0].is_deleted) return { status: 200, body: { message: 'Already deleted', student_id: Number(id) } };
        await conn.query('UPDATE students SET is_deleted=1, version=version+1 WHERE student_id=?', [id]);
        await conn.query("UPDATE accounts SET password = CONCAT('DISABLED:', password) WHERE account_id=? AND password NOT LIKE 'DISABLED:%'", [rows[0].account_id]);
        return { status: 200, body: { message: 'Student deleted', student_id: Number(id) } };
    });
});

module.exports = router;
