// Chat for students and lecturers.
//
// ROOMS (group chats)                         who can read and write
//   uni            Whole university           every student + every lecturer
//   prog:<id>      One programme              students of that programme + every lecturer
//   grp:<id>       One lab group              students of that lab group + every lecturer
//   staff:uni      Staff room (university)    lecturers only
//   staff:prog:<id> Staff room (programme)    lecturers only
//   staff:grp:<id>  Staff room (lab group)    lecturers only
// Room messages are stored as text. They are protected by login, role and membership checks on EVERY request.
//
// PRIVATE MESSAGES (1-to-1, any student or lecturer)
// The phone encrypts each message BEFORE sending (end-to-end). The server only stores and forwards
// the encrypted package; it has no key and cannot read it.
const express = require('express');
const db = require('./database');
const { authenticateToken } = require('./middleware');

const router = express.Router();
const pool = db.promise();

const MAX_TEXT = 2000;      // longest message, in characters
const PAGE = 100;           // most messages returned in one request
const ROOM_RULE = /^(staff:)?(uni|prog:\d+|grp:\d+)$/;
const B64 = /^[A-Za-z0-9+/=]+$/;

router.use('/chat', authenticateToken);

class Reject extends Error {
    constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

// Who is calling? Read fresh from MySQL every time, so a deleted student or a changed group counts immediately.
async function whoAmI(req) {
    const [rows] = await pool.query(
        `SELECT a.account_id, a.role, a.username, s.student_id, s.student_name,
                s.program_id, s.lab_group_id, s.is_deleted
           FROM accounts a
           LEFT JOIN students s ON s.account_id = a.account_id
          WHERE a.account_id = ?`, [req.user.account_id]);
    const me = rows[0];
    if (!me) throw new Reject(401, 'SESSION_INVALID', 'Your account no longer exists. Please log in again.');
    if (me.role === 'student' && (!me.student_id || me.is_deleted)) {
        throw new Reject(403, 'NOT_ACTIVE', 'Only active students can use chat.');
    }
    return me;
}

// The membership rule. Lecturers may open every room; students only their own three, never a staff room.
function canUseRoom(me, room) {
    if (!ROOM_RULE.test(room)) return false;
    if (me.role === 'lecturer') return true;
    if (room.startsWith('staff:')) return false;
    if (room === 'uni') return true;
    if (room === `prog:${me.program_id}`) return true;
    if (room === `grp:${me.lab_group_id}`) return true;
    return false;
}

function send(res, e) {
    if (e instanceof Reject) return res.status(e.status).json({ code: e.code, message: e.message });
    console.error('chat error:', e.message);
    return res.status(500).json({ code: 'SERVER_ERROR', message: 'Chat is not available right now' });
}

const SENDER_COLUMNS = `m.message_id, m.sender_id, m.body, m.created_at, a.role AS sender_role,
                        COALESCE(s.student_name, a.username) AS sender_name`;
const SENDER_JOINS = `JOIN accounts a ON a.account_id = m.sender_id
                      LEFT JOIN students s ON s.account_id = a.account_id`;

// ---------------------------------------------------------------- rooms

// The rooms this person may use, with readable titles.
router.get('/chat/rooms', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const [programs] = await pool.query('SELECT program_id, program_name FROM program ORDER BY program_name');
        const [groups] = await pool.query('SELECT lab_group_id, group_name FROM labgroup ORDER BY group_name');
        const rooms = [];
        const add = (room, title, audience) => rooms.push({ room, title, audience });

        add('uni', 'Whole University', 'All students and lecturers');
        programs.forEach(p => {
            if (canUseRoom(me, `prog:${p.program_id}`)) add(`prog:${p.program_id}`, p.program_name, 'Programme: students and lecturers');
        });
        groups.forEach(g => {
            if (canUseRoom(me, `grp:${g.lab_group_id}`)) add(`grp:${g.lab_group_id}`, `Lab group ${g.group_name}`, 'Lab group: students and lecturers');
        });
        if (me.role === 'lecturer') {
            add('staff:uni', 'Staff room: University', 'Lecturers only');
            programs.forEach(p => add(`staff:prog:${p.program_id}`, `Staff room: ${p.program_name}`, 'Lecturers only'));
            groups.forEach(g => add(`staff:grp:${g.lab_group_id}`, `Staff room: Lab group ${g.group_name}`, 'Lecturers only'));
        }
        res.json({ rooms });
    } catch (e) { send(res, e); }
});

// Messages in one room. ?after=<message_id> returns only newer ones (used for refreshing).
router.get('/chat/rooms/:room/messages', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const room = req.params.room;
        if (!canUseRoom(me, room)) throw new Reject(403, 'NOT_ALLOWED', 'You are not a member of this chat room');
        const after = Math.max(0, Number(req.query.after) || 0);
        const [rows] = await pool.query(
            `SELECT ${SENDER_COLUMNS}
               FROM chat_messages m ${SENDER_JOINS}
              WHERE m.room = ? AND m.message_id > ?
              ORDER BY m.message_id DESC
              LIMIT ?`, [room, after, PAGE]);
        res.json({ messages: rows.reverse() });
    } catch (e) { send(res, e); }
});

router.post('/chat/rooms/:room/messages', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const room = req.params.room;
        if (!canUseRoom(me, room)) throw new Reject(403, 'NOT_ALLOWED', 'You are not a member of this chat room');
        const text = String((req.body || {}).text || '').trim();
        if (!text) throw new Reject(400, 'VALIDATION', 'Type a message first');
        if (text.length > MAX_TEXT) throw new Reject(400, 'VALIDATION', `Messages can be at most ${MAX_TEXT} characters`);
        const [r] = await pool.query(
            'INSERT INTO chat_messages (room, sender_id, body, is_encrypted) VALUES (?, ?, ?, 0)',
            [room, me.account_id, text]);
        res.status(201).json({ message_id: r.insertId });
    } catch (e) { send(res, e); }
});

// ---------------------------------------------------------------- people + keys

// Everyone this person can message privately: active students and lecturers (not themselves).
router.get('/chat/contacts', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const [rows] = await pool.query(
            `SELECT a.account_id, a.role, COALESCE(s.student_name, a.username) AS name,
                    pr.program_name, g.group_name,
                    (k.account_id IS NOT NULL) AS has_key
               FROM accounts a
               LEFT JOIN students s  ON s.account_id = a.account_id
               LEFT JOIN program pr  ON pr.program_id = s.program_id
               LEFT JOIN labgroup g  ON g.lab_group_id = s.lab_group_id
               LEFT JOIN chat_keys k ON k.account_id = a.account_id
              WHERE a.account_id <> ?
                AND a.email NOT LIKE '%@pending.local'
                AND (a.role = 'lecturer' OR (s.student_id IS NOT NULL AND s.is_deleted = 0))
              ORDER BY a.role, name`, [me.account_id]);
        res.json({ contacts: rows.map(r => ({ ...r, has_key: Number(r.has_key) })) });
    } catch (e) { send(res, e); }
});

// The phone uploads ITS OWN public key (never the private one).
router.put('/chat/keys', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const key = String((req.body || {}).public_key || '');
        if (key.length < 200 || key.length > 2000 || !B64.test(key)) {
            throw new Reject(400, 'VALIDATION', 'Invalid public key');
        }
        await pool.query(
            `INSERT INTO chat_keys (account_id, public_key) VALUES (?, ?)
             ON DUPLICATE KEY UPDATE public_key = VALUES(public_key)`, [me.account_id, key]);
        res.json({ ok: true });
    } catch (e) { send(res, e); }
});

router.get('/chat/keys/:id', async (req, res) => {
    try {
        await whoAmI(req);
        const [rows] = await pool.query('SELECT account_id, public_key FROM chat_keys WHERE account_id = ?', [Number(req.params.id)]);
        if (!rows.length) throw new Reject(404, 'NO_KEY', 'This person has not opened Chat yet, so a secure key is not available');
        res.json(rows[0]);
    } catch (e) { send(res, e); }
});

// ---------------------------------------------------------------- private (end-to-end encrypted)

function validPayload(p) {
    if (!p || typeof p !== 'object') return false;
    for (const f of ['iv', 'ct', 'k_to', 'k_from']) {
        if (typeof p[f] !== 'string' || !p[f] || p[f].length > 12000 || !B64.test(p[f])) return false;
    }
    return true;
}

async function otherPerson(req, me) {
    const otherId = Number(req.params.id);
    if (!otherId || otherId === me.account_id) throw new Reject(400, 'VALIDATION', 'Choose someone else to message');
    const [rows] = await pool.query('SELECT account_id FROM accounts WHERE account_id = ?', [otherId]);
    if (!rows.length) throw new Reject(404, 'NOT_FOUND', 'That person was not found');
    return otherId;
}

router.get('/chat/dm/:id/messages', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const other = await otherPerson(req, me);
        const after = Math.max(0, Number(req.query.after) || 0);
        const [rows] = await pool.query(
            `SELECT ${SENDER_COLUMNS}
               FROM chat_messages m ${SENDER_JOINS}
              WHERE m.room IS NULL
                AND ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?))
                AND m.message_id > ?
              ORDER BY m.message_id DESC
              LIMIT ?`, [me.account_id, other, other, me.account_id, after, PAGE]);
        res.json({ messages: rows.reverse() });
    } catch (e) { send(res, e); }
});

router.post('/chat/dm/:id/messages', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const other = await otherPerson(req, me);
        const payload = (req.body || {}).payload;
        if (!validPayload(payload)) throw new Reject(400, 'VALIDATION', 'Private messages must be encrypted on the phone');
        const [r] = await pool.query(
            'INSERT INTO chat_messages (room, sender_id, recipient_id, body, is_encrypted) VALUES (NULL, ?, ?, ?, 1)',
            [me.account_id, other, JSON.stringify(payload)]);
        res.status(201).json({ message_id: r.insertId });
    } catch (e) { send(res, e); }
});

// ---------------------------------------------------------------- unread counts

// How many unread messages this person has: per room, per private chat, and in total.
// Unread = messages newer than the last one they marked as read, and not written by them.
router.get('/chat/unread', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const [roomRows] = await pool.query(
            `SELECT m.room, COUNT(*) AS n
               FROM chat_messages m
               LEFT JOIN chat_reads r ON r.account_id = ? AND r.conv_key = m.room
              WHERE m.room IS NOT NULL
                AND m.sender_id <> ?
                AND m.message_id > COALESCE(r.last_read_id, 0)
              GROUP BY m.room`, [me.account_id, me.account_id]);
        const [dmRows] = await pool.query(
            `SELECT m.sender_id, COUNT(*) AS n
               FROM chat_messages m
               LEFT JOIN chat_reads r ON r.account_id = ? AND r.conv_key = CONCAT('dm:', m.sender_id)
              WHERE m.room IS NULL
                AND m.recipient_id = ?
                AND m.message_id > COALESCE(r.last_read_id, 0)
              GROUP BY m.sender_id`, [me.account_id, me.account_id]);

        const rooms = {};
        const people = {};
        let total = 0;
        roomRows.forEach(r => {
            if (canUseRoom(me, r.room)) { rooms[r.room] = Number(r.n); total += Number(r.n); }   // only rooms they belong to
        });
        dmRows.forEach(r => { people[r.sender_id] = Number(r.n); total += Number(r.n); });
                   
        res.json({ total, rooms, people });
    } catch (e) { send(res, e); }
});

// "I have now seen up to message N in this chat."  conv = room key, or 'dm:<other account id>'
router.post('/chat/read', async (req, res) => {
    try {
        const me = await whoAmI(req);
        const conv = String((req.body || {}).conv || '');
        const lastId = Math.max(0, Number((req.body || {}).last_id) || 0);
        const isPrivate = /^dm:\d+$/.test(conv);
        if (!isPrivate && !canUseRoom(me, conv)) throw new Reject(403, 'NOT_ALLOWED', 'You are not a member of this chat');
        if (!lastId) throw new Reject(400, 'VALIDATION', 'last_id is required');
        // GREATEST: never move backwards (an older request arriving late cannot "un-read" messages)
        await pool.query(
            `INSERT INTO chat_reads (account_id, conv_key, last_read_id) VALUES (?, ?, ?)
             ON DUPLICATE KEY UPDATE last_read_id = GREATEST(last_read_id, VALUES(last_read_id))`,
            [me.account_id, conv, lastId]);
        res.json({ ok: true });
    } catch (e) { send(res, e); }
});
module.exports = router;