// Public reference data for the registration screen (no login needed):
// the real programmes and lab groups from MySQL, with their real IDs and how full each group is.
const express = require('express');
const db = require('./database');
const { MAX_LAB_GROUP_MEMBERS } = require('./middleware');

const router = express.Router();
const pool = db.promise();

router.get('/reference', async (req, res) => {
    try {
        const [programs] = await pool.query(
            'SELECT program_id, program_name FROM program ORDER BY program_name');
        const [groups] = await pool.query(
            `SELECT g.lab_group_id, g.group_name,
                    (SELECT COUNT(*) FROM students s
                      WHERE s.lab_group_id = g.lab_group_id AND s.is_deleted = 0) AS member_count
               FROM labgroup g
              ORDER BY g.group_name`);
        res.json({
            programs,
            groups: groups.map(g => ({ ...g, member_count: Number(g.member_count), max_members: MAX_LAB_GROUP_MEMBERS }))
        });
    } catch (e) {
        console.error('reference failed:', e.message);
        res.status(500).json({ message: 'Could not load programmes and lab groups' });
    }
});

module.exports = router;