// A lecturer reads and sets their own display name (shown to students and other lecturers).
const express = require('express');
const db = require('./database');
const { nameRegex, authenticateToken, requireRole } = require('./middleware');

const router = express.Router();
const pool = db.promise();

router.get('/lecturer/me', authenticateToken, requireRole('lecturer'), async (req, res) => {
    try {
        const [[a]] = await pool.query(
            'SELECT account_id, username, email, full_name FROM accounts WHERE account_id = ?', [req.user.account_id]);
        if (!a) return res.status(404).json({ code: 'NOT_FOUND', message: 'Account not found' });
        res.json(a);
    } catch (e) {
        console.error('GET /lecturer/me failed:', e.message);
        res.status(500).json({ message: 'Database error' });
    }
});

router.put('/lecturer/me', authenticateToken, requireRole('lecturer'), async (req, res) => {
    const full_name = String((req.body || {}).full_name || '').trim().replace(/\s+/g, ' ');
    if (!nameRegex.test(full_name)) {
        return res.status(400).json({ code: 'VALIDATION', field: 'full_name', message: 'Name must be 2-100 letters (spaces, hyphens and apostrophes allowed)' });
    }
    try {
        await pool.query('UPDATE accounts SET full_name = ? WHERE account_id = ?', [full_name, req.user.account_id]);
        res.json({ message: 'Name saved', full_name });
    } catch (e) {
        console.error('PUT /lecturer/me failed:', e.message);
        res.status(500).json({ message: 'Database error' });
    }
});

module.exports = router;