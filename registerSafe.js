// Safe registration. Mounted BEFORE auth.js in index.js, so these routes replace the old register routes.
//  - Student: group is LOCKED while we count + insert (the 15-limit cannot be beaten by two people at once)
//  - Student already added by the lecturer: the new account is LINKED to that profile (never a second profile)
//  - Lecturer: needs the secret access code (LECTURER_SIGNUP_CODE in .env), so not just anyone can become a lecturer
const express = require('express');
const bcrypt = require('bcrypt');
const db = require('./database');
const { nameRegex, studentNumberRegex, MAX_LAB_GROUP_MEMBERS } = require('./middleware');

const router = express.Router();
const pool = db.promise();
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

class Reject extends Error {
    constructor(status, body) { super(body.message); this.status = status; this.body = body; }
}

function dupToReject(e) {
    const m = e.sqlMessage || '';
    if (m.includes('username')) return new Reject(409, { code: 'USERNAME_TAKEN', message: 'That username is already taken' });
    return new Reject(409, { code: 'DUPLICATE', message: 'Student number already exists' });
}

router.post('/register/student', async (req, res) => {
    const { username, password, email, student_name, student_number, program_id, lab_group_id } = req.body || {};

    if (!username || !password || !email || !student_name || !student_number || !program_id || !lab_group_id) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Please provide all required fields' });
    }
    if (!nameRegex.test(student_name)) return res.status(400).json({ code: 'VALIDATION', message: 'Student name must contain letters and spaces only' });
    if (!studentNumberRegex.test(student_number)) return res.status(400).json({ code: 'VALIDATION', message: 'Student number must contain exactly 9 digits' });
    if (!emailRegex.test(email)) return res.status(400).json({ code: 'VALIDATION', message: 'Enter a valid email address' });
    if (String(password).length < 6) return res.status(400).json({ code: 'VALIDATION', message: 'Password must be at least 6 characters' });

    const conn = await pool.getConnection();
    try {
        const hash = await bcrypt.hash(String(password), 10);
        await conn.beginTransaction();
        let status, body;
        try {
            // 1. Lock the group so nobody else can take a place while we decide.
            const [g] = await conn.query('SELECT lab_group_id FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [lab_group_id]);
            if (g.length === 0) throw new Reject(400, { code: 'GROUP_NOT_FOUND', message: 'Lab group not found' });
            const [p] = await conn.query('SELECT program_id FROM program WHERE program_id = ?', [program_id]);
            if (p.length === 0) throw new Reject(400, { code: 'PROGRAM_NOT_FOUND', message: 'Programme not found' });

            // 2. Does this student number already exist?
            const [ex] = await conn.query(
                `SELECT s.student_id, s.student_name, s.is_deleted, a.account_id, a.email
                 FROM students s JOIN accounts a ON a.account_id = s.account_id
                 WHERE s.student_number = ? FOR UPDATE`, [student_number]);

            if (ex.length > 0) {
                const s = ex[0];
                if (s.is_deleted) throw new Reject(409, { code: 'NUMBER_RESERVED', message: 'This student number is reserved. Ask your lecturer.' });
                if (!String(s.email).endsWith('@pending.local')) throw new Reject(409, { code: 'ALREADY_REGISTERED', message: 'This student number already has an account. Please log in.' });
                // Added by the lecturer, not claimed yet: verify ownership by name, then LINK (no second profile, no new place used).
                if (String(s.student_name).trim().toLowerCase() !== String(student_name).trim().toLowerCase()) {
                    throw new Reject(403, { code: 'OWNERSHIP_FAILED', message: 'Name does not match the record your lecturer entered' });
                }
                try {
                    await conn.query('UPDATE accounts SET username = ?, password = ?, email = ? WHERE account_id = ?', [username, hash, email, s.account_id]);
                } catch (e) { if (e.code === 'ER_DUP_ENTRY') throw dupToReject(e); throw e; }
                status = 200;
                body = { code: 'LINKED', message: 'Account linked to your existing student record', student_id: s.student_id };
            } else {
                // 3. Brand-new student: count AFTER the lock, deleted students do not take a place.
                const [[{ n }]] = await conn.query('SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [lab_group_id]);
                if (n >= MAX_LAB_GROUP_MEMBERS) {
                    throw new Reject(409, { code: 'GROUP_FULL', message: `This lab group is full. A lab group can have a maximum of ${MAX_LAB_GROUP_MEMBERS} students.` });
                }
                try {
                    const [a] = await conn.query("INSERT INTO accounts (username, password, email, role) VALUES (?, ?, ?, 'student')", [username, hash, email]);
                    const [r] = await conn.query(
                        'INSERT INTO students (account_id, student_name, student_number, program_id, lab_group_id) VALUES (?, ?, ?, ?, ?)',
                        [a.insertId, student_name, student_number, program_id, lab_group_id]);
                    status = 201;
                    body = { code: 'CREATED', message: 'Student registered successfully', student_id: r.insertId };
                } catch (e) { if (e.code === 'ER_DUP_ENTRY') throw dupToReject(e); throw e; }
            }
        } catch (e) {
            if (!(e instanceof Reject)) throw e;
            await conn.rollback();
            return res.status(e.status).json(e.body);
        }
        await conn.commit();
        return res.status(status).json(body);
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        console.error('register student failed:', e.message);
        return res.status(500).json({ code: 'SERVER_ERROR', message: 'Could not register' });
    } finally {
        conn.release();
    }
});

router.post('/register/lecturer', async (req, res) => {
    const { username, password, email, lecturer_code } = req.body || {};
    const secret = process.env.LECTURER_SIGNUP_CODE;
    if (!secret) return res.status(403).json({ code: 'DISABLED', message: 'Lecturer self-registration is disabled' });
    if (lecturer_code !== secret) return res.status(403).json({ code: 'BAD_CODE', message: 'Wrong lecturer access code' });
    if (!username || !password || !email) return res.status(400).json({ code: 'VALIDATION', message: 'Username, password and email are required' });
    if (!emailRegex.test(email)) return res.status(400).json({ code: 'VALIDATION', message: 'Enter a valid email address' });
    if (String(password).length < 6) return res.status(400).json({ code: 'VALIDATION', message: 'Password must be at least 6 characters' });
    try {
        const hash = await bcrypt.hash(String(password), 10);
        await pool.query("INSERT INTO accounts (username, password, email, role) VALUES (?, ?, ?, 'lecturer')", [username, hash, email]);
        return res.status(201).json({ code: 'CREATED', message: 'Lecturer account created successfully' });
    } catch (e) {
        if (e.code === 'ER_DUP_ENTRY') return res.status(409).json({ code: 'USERNAME_TAKEN', message: 'That username is already taken' });
        console.error('register lecturer failed:', e.message);
        return res.status(500).json({ code: 'SERVER_ERROR', message: 'Could not register' });
    }
});

module.exports = router;
