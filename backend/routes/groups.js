const express = require('express');
const pool = require('../db');
const router = express.Router();

router.post('/groups/:id/join', async (req, res) => {
  const { studentId } = req.body;
  const groupId = req.params.id;

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // Atomic check-and-reserve: this single statement is what prevents
    // the race condition. If member_count is already at capacity, the
    // WHERE clause fails to match, affectedRows is 0, and nothing changes.
    const [result] = await conn.query(
      'UPDATE groups_table SET member_count = member_count + 1 WHERE id = ? AND member_count < capacity',
      [groupId]
    );

    if (result.affectedRows === 0) {
      await conn.rollback();
      return res.status(409).json({ error: 'GROUP_FULL' });
    }

    await conn.query('UPDATE students SET group_id = ? WHERE id = ?', [groupId, studentId]);

    await conn.commit();
    res.status(200).json({ status: 'JOINED' });
  } catch (err) {
    await conn.rollback();
    res.status(500).json({ error: err.message });
  } finally {
    conn.release();
  }
});

module.exports = router;