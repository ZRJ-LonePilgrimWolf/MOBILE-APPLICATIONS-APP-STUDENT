// Lecturers export and import students, lab groups and programmes as CSV.
// Excel and Google Sheets open and save CSV directly.
//   GET  /api/lecturer/export/students | groups | programs   -> CSV file
//   POST /api/lecturer/import/students | groups | programs   body: { csv: "..." }
// Import ADDS new rows and UPDATES existing students. It NEVER deletes anything.
const express = require('express');
const db = require('./database');
const mw = require('./middleware');
const { authenticateToken, requireRole } = mw;

const router = express.Router();
const pool = db.promise();
const lecturerOnly = [authenticateToken, requireRole('lecturer')];
const MAX_MEMBERS = mw.MAX_LAB_GROUP_MEMBERS || 16;

const NUMBER_RE = /^[0-9]{5,15}$/;
const NAME_RE = /^[A-Za-z][A-Za-z .'-]{0,99}$/;
const GROUP_RE = /^[A-Za-z0-9][A-Za-z0-9 _-]{0,19}$/;
const PROGRAM_RE = /^[A-Za-z][A-Za-z &(),'-]{1,99}$/;

function clean(s) { return String(s == null ? '' : s).trim().replace(/\s+/g, ' '); }

// ---------------- CSV writing ----------------
function cell(v) {
    let s = v == null ? '' : String(v);
    if (/^[=+\-@]/.test(s)) s = "'" + s;   // stops spreadsheet formulas (CSV injection)
    return /[",;\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
}
function sendCsv(res, filename, header, rows) {
    const text = [header, ...rows].map(r => r.map(cell).join(',')).join('\r\n') + '\r\n';
    res.set('Content-Type', 'text/csv; charset=utf-8');
    res.set('Content-Disposition', `attachment; filename="${filename}"`);
    res.send('\uFEFF' + text);   // BOM so Excel shows names with accents correctly
}

// ---------------- CSV reading ----------------
function parseCsv(text) {
    text = String(text || '').replace(/^\uFEFF/, '');
    const firstLine = text.split(/\r?\n/, 1)[0] || '';
    const delim = (firstLine.split(';').length > firstLine.split(',').length) ? ';' : ',';  // Excel in some regions uses ;
    const rows = []; let row = []; let f = ''; let q = false;
    for (let i = 0; i < text.length; i++) {
        const c = text[i];
        if (q) {
            if (c === '"') { if (text[i + 1] === '"') { f += '"'; i++; } else q = false; }
            else f += c;
        } else if (c === '"') q = true;
        else if (c === delim) { row.push(f); f = ''; }
        else if (c === '\n' || c === '\r') {
            if (c === '\r' && text[i + 1] === '\n') i++;
            row.push(f); f = ''; rows.push(row); row = [];
        } else f += c;
    }
    if (f !== '' || row.length) { row.push(f); rows.push(row); }
    return rows.filter(r => r.some(x => String(x).trim() !== ''));
}

// Header names the lecturer may use (case does not matter).
const ALIASES = {
    student_number: ['student_number', 'student number', 'number', 'student no', 'student_no', 'computer number'],
    student_name:   ['student_name', 'student name', 'name', 'full name', 'full_name'],
    programme:      ['programme', 'program', 'program_name', 'programme_name'],
    lab_group:      ['lab_group', 'lab group', 'group', 'group_name', 'labgroup'],
};

function readTable(csv) {
    const rows = parseCsv(csv);
    if (rows.length < 2) return null;
    const head = rows[0].map(h => clean(h).replace(/^'/, '').toLowerCase());
    const col = {};
    for (const [key, names] of Object.entries(ALIASES)) {
        const i = head.findIndex(h => names.includes(h));
        if (i >= 0) col[key] = i;
    }
    const data = rows.slice(1).map((r, n) => {
        const o = { row: n + 2 };   // row number as the lecturer sees it in Excel
        for (const k of Object.keys(col)) o[k] = clean(r[col[k]]).replace(/^'/, '');
        return o;
    });
    return { col, data };
}

function skip(result, row, reason) { result.skipped.push({ row, reason }); }
function fail(res, e, what) {
    console.error(`${what} error:`, e.message);
    res.status(500).json({ code: 'SERVER_ERROR', message: `Could not ${what} right now` });
}

// ---------------- EXPORT ----------------
router.get('/lecturer/export/students', lecturerOnly, async (req, res) => {
    try {
        const [rows] = await pool.query(
            `SELECT s.student_number, s.student_name, pr.program_name, g.group_name,
                    IF(a.email LIKE '%@pending.local', 'not registered', 'registered') AS status
               FROM students s
               JOIN accounts a ON a.account_id = s.account_id
               LEFT JOIN program pr ON pr.program_id = s.program_id
               LEFT JOIN labgroup g ON g.lab_group_id = s.lab_group_id
              WHERE s.is_deleted = 0
              ORDER BY g.group_name, s.student_name`);
        sendCsv(res, 'nexa-students.csv',
            ['student_number', 'student_name', 'programme', 'lab_group', 'status'],
            rows.map(r => [r.student_number, r.student_name, r.program_name, r.group_name, r.status]));
    } catch (e) { fail(res, e, 'export students'); }
});

router.get('/lecturer/export/groups', lecturerOnly, async (req, res) => {
    try {
        const [rows] = await pool.query(
            `SELECT g.group_name, COUNT(s.student_id) AS members
               FROM labgroup g
               LEFT JOIN students s ON s.lab_group_id = g.lab_group_id AND s.is_deleted = 0
              GROUP BY g.lab_group_id, g.group_name
              ORDER BY g.group_name`);
        sendCsv(res, 'nexa-groups.csv', ['group_name', 'members', 'max_members'],
            rows.map(r => [r.group_name, r.members, MAX_MEMBERS]));
    } catch (e) { fail(res, e, 'export groups'); }
});

router.get('/lecturer/export/programs', lecturerOnly, async (req, res) => {
    try {
        const [rows] = await pool.query(
            `SELECT pr.program_name, COUNT(s.student_id) AS students
               FROM program pr
               LEFT JOIN students s ON s.program_id = pr.program_id AND s.is_deleted = 0
              GROUP BY pr.program_id, pr.program_name
              ORDER BY pr.program_id`);
        sendCsv(res, 'nexa-programmes.csv', ['program_name', 'students'],
            rows.map(r => [r.program_name, r.students]));
    } catch (e) { fail(res, e, 'export programmes'); }
});

// ---------------- IMPORT ----------------
async function lookups(conn) {
    const [g] = await conn.query('SELECT lab_group_id, group_name FROM labgroup');
    const [p] = await conn.query('SELECT program_id, program_name FROM program');
    return {
        groups: new Map(g.map(x => [String(x.group_name).toLowerCase(), x.lab_group_id])),
        programs: new Map(p.map(x => [String(x.program_name).toLowerCase(), x.program_id])),
    };
}

async function isFull(conn, groupId) {
    const [[{ n }]] = await conn.query(
        'SELECT COUNT(*) AS n FROM students WHERE lab_group_id = ? AND is_deleted = 0', [groupId]);
    return n >= MAX_MEMBERS;
}

// Groups / programmes: add the ones that do not exist yet; existing ones are left as they are.
async function importNames(conn, data, result, { key, table, nameCol, re, upper, label }) {
    for (const r of data) {
        let name = r[key] || '';
        if (upper) name = name.toUpperCase();
        if (!name) { skip(result, r.row, `empty ${label} name`); continue; }
        if (!re.test(name)) { skip(result, r.row, `"${name}" is not a valid ${label} name`); continue; }
        try {
            const [x] = await conn.query(`INSERT IGNORE INTO ${table} (${nameCol}) VALUES (?)`, [name]);
            if (x.affectedRows) result.added++; else result.unchanged++;
        } catch (e) {
            console.error(`import ${table} row ${r.row}:`, e.message);
            skip(result, r.row, 'could not be saved');
        }
    }
}

// Students: new numbers become unregistered official-list students; existing ones are updated.
async function importStudents(conn, data, result) {
    const { groups, programs } = await lookups(conn);
    const seen = new Set();
    for (const r of data) {
        const num = r.student_number || '';
        const name = r.student_name || '';
        const progName = r.programme || '';
        const grpName = (r.lab_group || '').toUpperCase();

        if (!NUMBER_RE.test(num)) { skip(result, r.row, 'student number must be digits only'); continue; }
        if (seen.has(num)) { skip(result, r.row, `student number ${num} appears twice in the file`); continue; }
        seen.add(num);
        if (name && !NAME_RE.test(name)) { skip(result, r.row, `name "${name}": letters only`); continue; }

        const programId = progName ? programs.get(progName.toLowerCase()) : null;
        if (progName && !programId) { skip(result, r.row, `programme "${progName}" does not exist (add it first)`); continue; }
        const groupId = grpName ? groups.get(grpName.toLowerCase()) : null;
        if (grpName && !groupId) { skip(result, r.row, `lab group "${grpName}" does not exist (add it first)`); continue; }

        try {
            await conn.beginTransaction();
            const [ex] = await conn.query(
                `SELECT student_id, student_name, program_id, lab_group_id
                   FROM students WHERE student_number = ? FOR UPDATE`, [num]);

            if (ex.length) {
                // Existing student: update only what the file changes (blank cells keep the old value).
                const s = ex[0];
                const newName = name || s.student_name;
                const newProg = programId || s.program_id;
                const newGroup = groupId || s.lab_group_id;
                if (newName === s.student_name && newProg === s.program_id && newGroup === s.lab_group_id) {
                    await conn.rollback(); result.unchanged++; continue;
                }
                if (newGroup !== s.lab_group_id && await isFull(conn, newGroup)) {
                    await conn.rollback(); skip(result, r.row, `${grpName} is full (max ${MAX_MEMBERS})`); continue;
                }
                await conn.query(
                    `UPDATE students SET student_name = ?, program_id = ?, lab_group_id = ?, version = version + 1
                      WHERE student_id = ?`, [newName, newProg, newGroup, s.student_id]);
                await conn.commit(); result.updated++;
            } else {
                // New student: same as the official class list (unregistered until they sign up).
                if (!name || !programId || !groupId) {
                    await conn.rollback(); skip(result, r.row, 'a new student needs a name, programme and lab group'); continue;
                }
                if (await isFull(conn, groupId)) {
                    await conn.rollback(); skip(result, r.row, `${grpName} is full (max ${MAX_MEMBERS})`); continue;
                }
                const [a] = await conn.query(
                    "INSERT INTO accounts (username, password, email, role) VALUES (?, 'x', ?, 'student')",
                    ['pending_' + num, 'pending_' + num + '@pending.local']);
                await conn.query(
                    `INSERT INTO students (account_id, student_name, student_number, program_id, lab_group_id)
                     VALUES (?, ?, ?, ?, ?)`, [a.insertId, name, num, programId, groupId]);
                await conn.commit(); result.added++;
            }
        } catch (e) {
            try { await conn.rollback(); } catch (_) {}
            console.error(`import students row ${r.row}:`, e.message);
            skip(result, r.row, e.errno === 1062 ? 'clashes with an existing account' : 'could not be saved');
        }
    }
}

const NEEDS = {
    students: ['student_number'],
    groups: ['lab_group'],
    programs: ['programme'],
};

router.post('/lecturer/import/:type', lecturerOnly, async (req, res) => {
    const type = req.params.type;
    if (!NEEDS[type]) return res.status(404).json({ code: 'NOT_FOUND', message: 'Unknown import type' });

    const table = readTable((req.body || {}).csv);
    if (!table) return res.status(400).json({ code: 'VALIDATION', message: 'The file is empty or has no rows under the header' });
    const missing = NEEDS[type].filter(k => table.col[k] === undefined);
    if (missing.length) {
        return res.status(400).json({ code: 'VALIDATION',
            message: `The file needs a "${missing[0]}" column. Tip: export first and edit that file.` });
    }
    if (table.data.length > 2000) {
        return res.status(400).json({ code: 'VALIDATION', message: 'Import at most 2000 rows at a time' });
    }

    const result = { added: 0, updated: 0, unchanged: 0, skipped: [] };
    let conn;
    try {
        conn = await pool.getConnection();
        if (type === 'students') {
            await importStudents(conn, table.data, result);
        } else if (type === 'groups') {
            await importNames(conn, table.data, result,
                { key: 'lab_group', table: 'labgroup', nameCol: 'group_name', re: GROUP_RE, upper: true, label: 'group' });
        } else {
            await importNames(conn, table.data, result,
                { key: 'programme', table: 'program', nameCol: 'program_name', re: PROGRAM_RE, upper: false, label: 'programme' });
        }
        res.json({
            message: `Added ${result.added}, updated ${result.updated}, unchanged ${result.unchanged}, skipped ${result.skipped.length}`,
            added: result.added, updated: result.updated, unchanged: result.unchanged,
            skipped: result.skipped.slice(0, 50),
        });
    } catch (e) {
        fail(res, e, `import ${type}`);
    } finally {
        if (conn) conn.release();
    }
});

module.exports = router;