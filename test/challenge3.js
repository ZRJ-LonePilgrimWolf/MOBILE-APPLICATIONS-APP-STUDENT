// Run: node test/challenge3.js   (server running on :3000)
require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const db = require('../database').promise();
const API = process.env.API || 'http://localhost:3000/api';
const tok = (role, id) => jwt.sign({ account_id: id, username: 'x', role }, process.env.JWT_SECRET, { expiresIn: '10m' });
const L = tok('lecturer', 1);
const call = (m, p, body, t = L) => fetch(API + p, { method: m, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` }, body: body ? JSON.stringify(body) : undefined })
    .then(async r => ({ status: r.status, body: await r.json().catch(() => ({})) }));
const ok = (c, m) => { console.log(`${c ? 'PASS' : 'FAIL'}  ${m}`); if (!c) process.exitCode = 1; };
let n = 0, num = 910000000;
async function mk(group, name = 'Test Person') {
    const u = `U${Date.now()}${++n}`.slice(0, 20);
    const [a] = await db.query("INSERT INTO accounts (username,password,email,role) VALUES (?,?,?,'student')", [u, await bcrypt.hash('x', 4), u + '@t.local']);
    const [s] = await db.query('INSERT INTO students (account_id,student_name,student_number,program_id,lab_group_id) VALUES (?,?,?,1,?)', [a.insertId, name, String(++num), group.id ?? group]);
    return { id: s.insertId, number: String(num), account: a.insertId };
}
(async () => {
    await db.query("DELETE FROM students WHERE student_number BETWEEN '910000000' AND '919999999'");
    await db.query("DELETE FROM accounts WHERE username LIKE 'U1%' AND role='student'");
    await db.query("DELETE FROM operations WHERE operation_id LIKE 'c3-%'");
    const [[g1]] = await db.query("SELECT lab_group_id id FROM labgroup WHERE group_name='G01'");
    const [[g2]] = await db.query("SELECT lab_group_id id FROM labgroup WHERE group_name='G02'");
    const body = (s, extra = {}) => ({ student_name: 'Test Person', student_number: s.number, program_id: 1, lab_group_id: g1.id, ...extra });

    // Challenge 3: two lecturers edit same version
    const a = await mk(g1);
    const r1 = await call('PUT', `/students/${a.id}`, body(a, { student_name: 'Lecturer One', base_version: 1 }));
    const r2 = await call('PUT', `/students/${a.id}`, body(a, { student_name: 'Lecturer Two', base_version: 1 }));
    ok(r1.status === 200 && r2.status === 409 && r2.body.code === 'CONFLICT', `stale edit gets CONFLICT (${r1.status}, ${r2.status})`);
    ok(r2.body.current && r2.body.current.student_name === 'Lecturer One', 'conflict returns current server record');

    // edit of deleted student
    const d = await mk(g1);
    await call('DELETE', `/students/${d.id}`);
    const r3 = await call('PUT', `/students/${d.id}`, body(d, { base_version: 1 }));
    ok(r3.status === 410, `editing deleted student refused (${r3.status})`);

    // repeated delete releases one place only
    const before = (await db.query('SELECT COUNT(*) n FROM students WHERE lab_group_id=? AND is_deleted=0', [g1.id]))[0][0].n;
    const e = await mk(g1);
    await call('DELETE', `/students/${e.id}`, { operation_id: 'c3-del-1' });
    await call('DELETE', `/students/${e.id}`, { operation_id: 'c3-del-1' });
    await call('DELETE', `/students/${e.id}`);
    const after = (await db.query('SELECT COUNT(*) n FROM students WHERE lab_group_id=? AND is_deleted=0', [g1.id]))[0][0].n;
    ok(before === after, `3 deletes released exactly one place (before=${before}, after=${after})`);

    // failed transfer keeps old group
    await db.query("DELETE FROM students WHERE student_number BETWEEN '910000000' AND '919999999'");
    for (let i = 0; i < 15; i++) await mk(g2.id);
    const m = await mk(g1);
    const r4 = await call('PUT', `/students/${m.id}`, { student_name: 'Test Person', student_number: m.number, program_id: 1, lab_group_id: g2.id });
    const [[row]] = await db.query('SELECT lab_group_id FROM students WHERE student_id=?', [m.id]);
    ok(r4.status === 409 && r4.body.code === 'GROUP_FULL' && row.lab_group_id === g1.id, `full target group refused, student stays in old group (${r4.status})`);

    // duplicate number
    const x = await mk(g1), y = await mk(g1);
    const r5 = await call('PUT', `/students/${y.id}`, { student_name: 'Test Person', student_number: x.number, program_id: 1, lab_group_id: g1.id });
    ok(r5.status === 409 && r5.body.code === 'DUPLICATE', `duplicate number refused (${r5.status})`);

    // retry with same operation_id: same answer; reuse with different content refused
    const z = await mk(g1);
    const p1 = await call('PUT', `/students/${z.id}`, body(z, { student_name: 'Retry Name', base_version: 1, operation_id: 'c3-put-1' }));
    const p2 = await call('PUT', `/students/${z.id}`, body(z, { student_name: 'Retry Name', base_version: 1, operation_id: 'c3-put-1' }));
    const p3 = await call('PUT', `/students/${z.id}`, body(z, { student_name: 'Other Name', base_version: 1, operation_id: 'c3-put-1' }));
    const [[zv]] = await db.query('SELECT version FROM students WHERE student_id=?', [z.id]);
    ok(p1.status === 200 && p2.status === 200 && zv.version === 2, `retry replayed, version bumped once (v=${zv.version})`);
    ok(p3.status === 422 && p3.body.code === 'OPERATION_ID_REUSED', `reused ID with different content refused (${p3.status})`);

    // permissions
    const s = await call('GET', '/students', null, tok('student', z.account));
    const s2 = await call('PUT', `/students/${z.id}`, body(z), tok('student', z.account));
    ok(s.status === 403 && s2.status === 403, `student token blocked from roster/edit (${s.status}, ${s2.status})`);

    // cleanup
    await db.query("DELETE FROM students WHERE student_number BETWEEN '910000000' AND '919999999'");
    await db.query("DELETE FROM accounts WHERE username LIKE 'U1%' AND role='student'");
    process.exit();
})();
