const express = require('express');
const crypto = require('crypto');
const pool = require('../db');
const router = express.Router();

function hashPayload(obj) {
  return crypto.createHash('sha256').update(JSON.stringify(obj)).digest('hex');
}

router.post('/students/:id/edit', async (req, res) => {
  const { operationId, accountId, baseVersion, payload } = req.body;
  const studentId = req.params.id;
  const incomingHash = hashPayload(payload);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [existing] = await conn.query('SELECT * FROM operations WHERE operation_id = ?FOR UPDATE', [operationId]);
    if (existing.length > 0) {
      if (existing[0].payload_hash !== incomingHash) {
        await conn.rollback();
        return res.status(409).json({ error: 'OPERATION_ID_REUSED_DIFFERENT_CONTENT' });
      }
      await conn.commit();
      return res.status(200).json(JSON.parse(existing[0].result_json));
    }

    const [rows] = await conn.query('SELECT * FROM students WHERE id = ? FOR UPDATE', [studentId]);
    if (rows.length === 0 || rows[0].deleted_at) {
      await conn.query(
        `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, status) VALUES (?,?,?,?,?,?)`,
        [operationId, accountId, studentId, 'EDIT', incomingHash, 'REJECTED']
      );
      await conn.commit();
      return res.status(410).json({ error: 'STUDENT_DELETED' });
    }
    if (rows[0].version !== baseVersion) {
      await conn.query(
        `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, status) VALUES (?,?,?,?,?,?)`,
        [operationId, accountId, studentId, 'EDIT', incomingHash, 'CONFLICT']
      );
      await conn.commit();
      return res.status(409).json({ error: 'VERSION_CONFLICT', current: rows[0] });
    }

    await conn.query(
      `UPDATE students SET first_name=?, last_name=?, version = version + 1 WHERE id = ?`,
      [payload.firstName, payload.lastName, studentId]
    );
    const result = { studentId, newVersion: baseVersion + 1 };

    await conn.query(
      `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, result_json, status) VALUES (?,?,?,?,?,?,?)`,
      [operationId, accountId, studentId, 'EDIT', incomingHash, JSON.stringify(result), 'APPLIED']
    );

    await conn.commit();
    res.status(200).json(result);
  } catch (err) {
    await conn.rollback();
    res.status(500).json({ error: err.message });
  } finally {
    conn.release();
  }
});

router.post('/students/:id/delete', async (req, res) => {
  const { operationId, accountId, baseVersion } = req.body;
  const studentId = req.params.id;
  const incomingHash = hashPayload({ action: 'delete' });

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [existing] = await conn.query('SELECT * FROM operations WHERE operation_id = ?', [operationId]);
    if (existing.length > 0) {
      if (existing[0].payload_hash !== incomingHash) {
        await conn.rollback();
        return res.status(409).json({ error: 'OPERATION_ID_REUSED_DIFFERENT_CONTENT' });
      }
      await conn.commit();
      return res.status(200).json(existing[0].result_json);
    }

    const [rows] = await conn.query('SELECT * FROM students WHERE id = ? FOR UPDATE', [studentId]);
    if (rows.length === 0 || rows[0].deleted_at) {
      await conn.query(
        `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, status) VALUES (?,?,?,?,?,?)`,
        [operationId, accountId, studentId, 'DELETE', incomingHash, 'REJECTED']
      );
      await conn.commit();
      return res.status(410).json({ error: 'STUDENT_ALREADY_DELETED' });
    }
    if (rows[0].version !== baseVersion) {
      await conn.query(
        `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, status) VALUES (?,?,?,?,?,?)`,
        [operationId, accountId, studentId, 'DELETE', incomingHash, 'CONFLICT']
      );
      await conn.commit();
      return res.status(409).json({ error: 'VERSION_CONFLICT', current: rows[0] });
    }

    await conn.query(
      `UPDATE students SET deleted_at = NOW(), version = version + 1 WHERE id = ?`,
      [studentId]
    );
    const result = { studentId, deleted: true };

    await conn.query(
      `INSERT INTO operations (operation_id, account_id, student_id, type, payload_hash, result_json, status) VALUES (?,?,?,?,?,?,?)`,
      [operationId, accountId, studentId, 'DELETE', incomingHash, JSON.stringify(result), 'APPLIED']
    );

    await conn.commit();
    res.status(200).json(result);
  } catch (err) {
    await conn.rollback();
    res.status(500).json({ error: err.message });
  } finally {
    conn.release();
  }
});

module.exports = router;