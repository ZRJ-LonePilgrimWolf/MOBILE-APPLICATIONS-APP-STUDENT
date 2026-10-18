// Password reset by emailed 6-digit code.
// Email goes out over HTTPS (Brevo API) because Render's free tier blocks SMTP ports.
// Safe rules: code tied to the username, stored hashed, expires in 15 min, max 5 wrong tries,
// deleted accounts cannot be reset, and the answer never reveals whether an account exists.
const express = require('express');
const bcrypt = require('bcrypt');
const crypto = require('crypto');
const db = require('./database');
require('dotenv').config();

const router = express.Router();
const pool = db.promise();

const CODE_MINUTES = 15;
const MAX_ATTEMPTS = 5;
const RESEND_SECONDS = 60;
const GENERIC = 'If that account has an email address, a 6-digit code has been sent to it. Check your inbox and spam folder.';

const hashCode = (accountId, code) =>
    crypto.createHash('sha256').update(`${accountId}:${code}`).digest('hex');

async function sendEmail(to, subject, text) {
    if (process.env.BREVO_API_KEY && process.env.EMAIL_FROM) {
        const r = await fetch('https://api.brevo.com/v3/smtp/email', {
            method: 'POST',
            headers: { 'api-key': process.env.BREVO_API_KEY, 'Content-Type': 'application/json', accept: 'application/json' },
            body: JSON.stringify({
                sender: { name: 'Nexa', email: process.env.EMAIL_FROM },
                to: [{ email: to }],
                subject,
                textContent: text
            })
        });
        if (!r.ok) throw new Error(`Brevo ${r.status}: ${(await r.text()).slice(0, 200)}`);
        return;
    }
    if (process.env.EMAIL_USER && process.env.EMAIL_PASSWORD) {      // Gmail app password (works on a laptop only)
        const nodemailer = require('nodemailer');
        const t = nodemailer.createTransport({ service: 'gmail', auth: { user: process.env.EMAIL_USER, pass: process.env.EMAIL_PASSWORD } });
        await t.sendMail({ from: `"Nexa" <${process.env.EMAIL_USER}>`, to, subject, text });
        return;
    }
    throw new Error('No email service configured (set BREVO_API_KEY and EMAIL_FROM)');
}

// STEP 1: ask for a code
router.post('/forgot-password', async (req, res) => {
    const username = String((req.body && req.body.username) || '').trim();
    if (!username) return res.status(400).json({ code: 'VALIDATION', field: 'username', message: 'Enter your username' });
    try {
        const [rows] = await pool.query('SELECT account_id, username, email, password FROM accounts WHERE username = ?', [username]);
        const a = rows[0];
        // Unknown user, deleted account or placeholder email: same answer, nothing sent
        if (!a || !a.email || a.email.endsWith('@pending.local') || String(a.password).startsWith('DISABLED:')) {
            return res.json({ message: GENERIC });
        }
        const [[recent]] = await pool.query(
            'SELECT COUNT(*) AS n FROM password_reset_codes WHERE account_id = ? AND created_at > NOW() - INTERVAL ? SECOND',
            [a.account_id, RESEND_SECONDS]);
        if (recent.n > 0) return res.json({ message: 'A code was sent less than a minute ago. Check your email, or wait a minute and ask again.' });

        const code = String(crypto.randomInt(100000, 1000000));
        await pool.query('DELETE FROM password_reset_codes WHERE account_id = ?', [a.account_id]);
        await pool.query(
            'INSERT INTO password_reset_codes (account_id, reset_code, expires_at, attempts) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? MINUTE), 0)',
            [a.account_id, hashCode(a.account_id, code), CODE_MINUTES]);
        try {
            await sendEmail(a.email, 'Your Nexa password reset code',
                `Hello ${a.username},\n\nYour Nexa password reset code is: ${code}\n\n` +
                `It expires in ${CODE_MINUTES} minutes. If you did not ask for this, ignore this email - your password stays the same.\n\nNexa`);
        } catch (e) {
            await pool.query('DELETE FROM password_reset_codes WHERE account_id = ?', [a.account_id]);
            console.error('Reset email failed:', e.message);
            return res.status(503).json({ code: 'EMAIL_FAILED', message: 'Could not send the email right now. Please try again later.' });
        }
        res.json({ message: GENERIC });
    } catch (e) {
        console.error('forgot-password failed:', e.message);
        res.status(500).json({ message: 'Server error' });
    }
});

// STEP 2: code + new password
router.post('/reset-password', async (req, res) => {
    const b = req.body || {};
    const username = String(b.username || '').trim();
    const code = String(b.reset_code || '').trim();
    const pw = String(b.new_password || '');
    if (!username) return res.status(400).json({ code: 'VALIDATION', field: 'username', message: 'Enter your username' });
    if (!/^\d{6}$/.test(code)) return res.status(400).json({ code: 'VALIDATION', field: 'reset_code', message: 'The code has exactly 6 digits' });
    if (pw.length < 6) return res.status(400).json({ code: 'VALIDATION', field: 'new_password', message: 'Password must be at least 6 characters' });

    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        const [rows] = await conn.query(
            `SELECT r.id, r.account_id, r.reset_code, r.attempts, (r.expires_at > NOW()) AS valid, a.password
               FROM password_reset_codes r JOIN accounts a ON a.account_id = r.account_id
              WHERE a.username = ? ORDER BY r.id DESC LIMIT 1 FOR UPDATE`, [username]);
        const r = rows[0];
        if (!r || String(r.password).startsWith('DISABLED:')) {
            await conn.rollback();
            return res.status(400).json({ code: 'INVALID_CODE', field: 'reset_code', message: 'That code is wrong or has expired. Ask for a new code.' });
        }
        if (!Number(r.valid) || r.attempts >= MAX_ATTEMPTS) {
            await conn.query('DELETE FROM password_reset_codes WHERE id = ?', [r.id]);
            await conn.commit();
            return res.status(400).json({ code: 'CODE_EXPIRED', field: 'reset_code', message: 'That code has expired or was tried too many times. Ask for a new code.' });
        }
        if (hashCode(r.account_id, code) !== r.reset_code) {
            await conn.query('UPDATE password_reset_codes SET attempts = attempts + 1 WHERE id = ?', [r.id]);
            await conn.commit();
            const left = MAX_ATTEMPTS - r.attempts - 1;
            return res.status(400).json({ code: 'INVALID_CODE', field: 'reset_code',
                message: left > 0 ? `Wrong code. ${left} ${left === 1 ? 'try' : 'tries'} left.` : 'Wrong code. Ask for a new code.' });
        }
        await conn.query('UPDATE accounts SET password = ? WHERE account_id = ?', [await bcrypt.hash(pw, 10), r.account_id]);
        await conn.query('DELETE FROM password_reset_codes WHERE account_id = ?', [r.account_id]);
        await conn.commit();
        res.json({ message: 'Password changed. You can now log in with your new password.' });
    } catch (e) {
        try { await conn.rollback(); } catch (_) {}
        console.error('reset-password failed:', e.message);
        res.status(500).json({ message: 'Server error' });
    } finally {
        conn.release();
    }
});

module.exports = router;