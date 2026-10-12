// Safe "create student": fixes Challenge 1 (last place) and Challenge 2 (retry).
// Mounted BEFORE students.js in index.js, so it handles POST /api/students instead of the old route.
const express = require('express');
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const db = require('./database');
const receipts = require('./receipts');
const {
    nameRegex, studentNumberRegex, MAX_LAB_GROUP_MEMBERS,
    authenticateToken, requireRole
} = require('./middleware');

const router = express.Router();
const pool = db.promise();

// Throw this to stop and send a clean business-rule answer.
class Reject extends Error {
    constructor(status, body) { super(body.message); this.status = status; this.body = body; }
}

async function findOperation(conn, operationId) {
    const [rows] = await conn.query(
        'SELECT http_status, response_body FROM operations WHERE operation_id = ?', [operationId]);
    return rows[0];
}

router.post('/students', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const { operation_id, account_id, student_name, student_number, program_id, lab_group_id } = req.body;

    if (!student_name || !student_number || !program_id || !lab_group_id) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Please provide all required fields' });
    }
    if (!nameRegex.test(student_name)) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Student name must contain letters and spaces only' });
    }
    if (!studentNumberRegex.test(student_number)) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Student number must contain exactly 9 digits' });
    }

    const reqHash = receipts.hashOf({ account_id: account_id || null, student_name, student_number, program_id, lab_group_id });
    const conn = await pool.getConnection();
    try {
        // Retry of an operation we already finished? Give back the SAME answer, do nothing new.
        const replay = await receipts.replay(conn, operation_id, reqHash);
        if (replay) return res.status(replay.status).json(replay.body);

        await conn.beginTransaction();
        let status, body;
        try {
            // 1. LOCK the group row. A second request for the same group waits here until we commit.
            const [groups] = await conn.query(
                'SELECT lab_group_id FROM labgroup WHERE lab_group_id = ? FOR UPDATE', [lab_group_id]);
            if (groups.length === 0) throw new Reject(404, { code: 'GROUP_NOT_FOUND', message: 'Lab group not found' });

            // 2. Count AFTER the lock, so the number is trustworthy. Deleted students do not take a place.
            const [[{ n }]] = await conn.query(
                'SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [lab_group_id]);
            if (n >= MAX_LAB_GROUP_MEMBERS) {
                throw new Reject(409, { code: 'GROUP_FULL',
                    message: `This lab group is full. A lab group can have a maximum of ${MAX_LAB_GROUP_MEMBERS} students.` });
            }

            // 3. Insert. UNIQUE(student_number) in the database is the final guard against duplicates.
            try {
                // Lecturer added a student with no account yet: create a locked account (random password).
                // The student claims it later by registering.
                let accId = account_id;
                if (!accId) {
                    const [a] = await conn.query(
                        "INSERT INTO accounts (username, password, email, role) VALUES (?, ?, ?, 'student')",
                        [student_number, await bcrypt.hash(crypto.randomUUID(), 8), `${student_number}@pending.local`]);
                    accId = a.insertId;
                }
                const [r] = await conn.query(
                    `INSERT INTO students (account_id, student_name, student_number, program_id, lab_group_id)
                     VALUES (?, ?, ?, ?, ?)`,
                    [accId, student_name, student_number, program_id, lab_group_id]);
                status = 201;
                body = { message: 'Student created successfully', student_id: r.insertId, version: 1 };
            } catch (e) {
                if (e.code === 'ER_DUP_ENTRY') {
                    throw new Reject(409, { code: 'DUPLICATE', message: 'Student number already exists' });
                }
                if (e.code === 'ER_NO_REFERENCED_ROW_2') {
                    throw new Reject(400, { code: 'VALIDATION', message: 'account_id or program_id does not exist' });
                }
                throw e;
            }
        } catch (e) {
            if (!(e instanceof Reject)) throw e;
            status = e.status; body = e.body;
        }

        // 4. Save the answer with the operation_id, in the SAME transaction as the insert.
        if (operation_id) {
            await conn.query(
                'INSERT INTO operations (operation_id, account_id, http_status, response_body) VALUES (?, ?, ?, ?)',
                [operation_id, req.user.account_id, status, receipts.pack(body, reqHash)]);
        }
        await conn.commit();
        return res.status(status).json(body);
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        // Two copies of the SAME operation arrived at once: the loser lands here. Return the winner's answer.
        if (e.code === 'ER_DUP_ENTRY' && operation_id) {
            const again = await receipts.replay(conn, operation_id, reqHash);
            if (again) return res.status(again.status).json(again.body);
        }
        console.error('create student failed:', e.message);
        return res.status(500).json({ code: 'SERVER_ERROR', message: 'Could not create student' });
    } finally {
        conn.release();
    }
});

module.exports = router;
