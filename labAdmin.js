// Lecturers manage lab groups and programmes.
//   POST   /api/lecturer/groups        body: { group_name }
//   DELETE /api/lecturer/groups/:id
//   POST   /api/lecturer/programs      body: { program_name }
//   DELETE /api/lecturer/programs/:id
// The lists themselves come from GET /api/reference (reference.js).
const express = require('express');
const db = require('./database');
const { authenticateToken, requireRole } = require('./middleware');

const router = express.Router();
const pool = db.promise();
const lecturerOnly = [authenticateToken, requireRole('lecturer')];

const GROUP_RE = /^[A-Za-z0-9][A-Za-z0-9 _-]{0,19}$/;             // e.g. G15, Group 15
const PROGRAM_RE = /^[A-Za-z][A-Za-z &(),'-]{1,99}$/;              // e.g. Computer Engineering

function clean(s) { return String(s || '').trim().replace(/\s+/g, ' '); }

// Delete one row, but only if no student (current or deleted) still points at it.
async function safeDelete(res, { table, idCol, studentCol, id, label }) {
    if (!Number.isInteger(id) || id <= 0) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Invalid id' });
    }
    let conn;
    try {
        conn = await pool.getConnection();
        await conn.beginTransaction();
        const [rows] = await conn.query(
            `SELECT ${idCol} FROM ${table} WHERE ${idCol} = ? FOR UPDATE`, [id]);
        if (!rows.length) {
            await conn.rollback();
            return res.status(404).json({ code: 'NOT_FOUND', message: `${label} not found` });
        }
        const [[{ n }]] = await conn.query(
            `SELECT COUNT(*) AS n FROM students WHERE ${studentCol} = ?`, [id]);
        if (n > 0) {
            await conn.rollback();
            return res.status(409).json({ code: 'IN_USE',
                message: `This ${label.toLowerCase()} still has ${n} student(s). Move them first.` });
        }
        await conn.query(`DELETE FROM ${table} WHERE ${idCol} = ?`, [id]);
        await conn.commit();
        res.json({ message: `${label} deleted` });
    } catch (e) {
        if (conn) { try { await conn.rollback(); } catch (_) {} }
        if (e.errno === 1451) {   // still referenced by another table (e.g. a pending request)
            return res.status(409).json({ code: 'IN_USE',
                message: `This ${label.toLowerCase()} is still used elsewhere and cannot be deleted.` });
        }
        console.error(`delete ${table} error:`, e.message);
        res.status(500).json({ code: 'SERVER_ERROR', message: `Could not delete the ${label.toLowerCase()}` });
    } finally {
        if (conn) conn.release();
    }
}

// Add one row with a unique name.
async function safeAdd(res, { table, nameCol, idCol, name, re, label, hint }) {
    if (!re.test(name)) {
        return res.status(400).json({ code: 'VALIDATION', message: hint });
    }
    try {
        const [r] = await pool.query(`INSERT INTO ${table} (${nameCol}) VALUES (?)`, [name]);
        res.status(201).json({ message: `${label} added`, [idCol]: r.insertId, [nameCol]: name });
    } catch (e) {
        if (e.errno === 1062) {
            return res.status(409).json({ code: 'DUPLICATE', message: `${label} "${name}" already exists` });
        }
        console.error(`add ${table} error:`, e.message);
        res.status(500).json({ code: 'SERVER_ERROR', message: `Could not add the ${label.toLowerCase()}` });
    }
}

router.post('/lecturer/groups', lecturerOnly, (req, res) =>
    safeAdd(res, {
        table: 'labgroup', nameCol: 'group_name', idCol: 'lab_group_id',
        name: clean((req.body || {}).group_name).toUpperCase(),
        re: GROUP_RE, label: 'Lab group',
        hint: 'Group name: letters/numbers, up to 20 characters (e.g. G15)'
    }));

router.delete('/lecturer/groups/:id', lecturerOnly, (req, res) =>
    safeDelete(res, {
        table: 'labgroup', idCol: 'lab_group_id', studentCol: 'lab_group_id',
        id: Number(req.params.id), label: 'Lab group'
    }));

router.post('/lecturer/programs', lecturerOnly, (req, res) =>
    safeAdd(res, {
        table: 'program', nameCol: 'program_name', idCol: 'program_id',
        name: clean((req.body || {}).program_name),
        re: PROGRAM_RE, label: 'Programme',
        hint: 'Programme name: letters and spaces, 2 to 100 characters'
    }));

router.delete('/lecturer/programs/:id', lecturerOnly, (req, res) =>
    safeDelete(res, {
        table: 'program', idCol: 'program_id', studentCol: 'program_id',
        id: Number(req.params.id), label: 'Programme'
    }));

module.exports = router;