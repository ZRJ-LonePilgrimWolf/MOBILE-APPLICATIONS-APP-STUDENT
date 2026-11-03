// Delete my own account (students and lecturers).
//   POST /api/me/delete     body: { password }
// The account is anonymised, not erased, so old chat messages, receipts and
// requests still point at a valid row. It can never log in again.
const express = require('express');
const crypto = require('crypto');
const bcrypt = require('bcrypt');
const db = require('./database');
const { authenticateToken } = require('./middleware');

const router = express.Router();
const pool = db.promise();

router.post('/me/delete', authenticateToken, async (req, res) => {
    const accountId = req.user && req.user.account_id;
    const password = String((req.body || {}).password || '');
    if (!accountId) return res.status(401).json({ code: 'AUTH', message: 'Please log in again' });
    if (!password) return res.status(400).json({ code: 'VALIDATION', message: 'Enter your password to confirm' });

    let conn;
    try {
        // A random password nobody knows, so the account can never log in again.
        const unusable = await bcrypt.hash(crypto.randomBytes(32).toString('hex'), 10);

        conn = await pool.getConnection();
        await conn.beginTransaction();

        const [rows] = await conn.query(
            'SELECT account_id, role, email, password FROM accounts WHERE account_id = ? FOR UPDATE',
            [accountId]);
        if (!rows.length || String(rows[0].email || '').startsWith('deleted_')) {
            await conn.rollback();
            return res.status(404).json({ code: 'NOT_FOUND', message: 'This account no longer exists' });
        }

        const ok = await bcrypt.compare(password, rows[0].password);
        if (!ok) {
            await conn.rollback();
            return res.status(401).json({ code: 'WRONG_PASSWORD', message: 'Password is incorrect' });
        }

        if (rows[0].role === 'student') {
            await conn.query(
                'UPDATE students SET is_deleted = 1, version = version + 1 WHERE account_id = ?',
                [accountId]);
        }
        await conn.query('DELETE FROM chat_keys WHERE account_id = ?', [accountId]);
        await conn.query(
            `UPDATE accounts
                SET username  = CONCAT('deleted_', account_id),
                    email     = CONCAT('deleted_', account_id, '@pending.local'),
                    full_name = NULL,
                    password  = ?
              WHERE account_id = ?`,
            [unusable, accountId]);

        await conn.commit();
        res.json({ message: 'Your account has been deleted' });
    } catch (e) {
        if (conn) { try { await conn.rollback(); } catch (_) {} }
        console.error('delete account error:', e.message);
        res.status(500).json({ code: 'SERVER_ERROR', message: 'Could not delete the account right now' });
    } finally {
        if (conn) conn.release();
    }
});

module.exports = router;