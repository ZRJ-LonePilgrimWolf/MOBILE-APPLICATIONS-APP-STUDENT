// Run:  node test/registration.js     (server running on :3000)
require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const db = require('../database').promise();
const API = process.env.API || 'http://localhost:3000/api';
const token = jwt.sign({ account_id: 1, username: '100000001', role: 'lecturer' }, process.env.JWT_SECRET, { expiresIn: '10m' });
const call = (path, body, auth) => fetch(`${API}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body)
}).then(async r => ({ status: r.status, body: await r.json() }));
const ok = (c, m) => { console.log(`${c ? 'PASS' : 'FAIL'}  ${m}`); if (!c) process.exitCode = 1; };
let n = 910000000; const next = () => String(++n);
const base = (num, extra = {}) => ({ username: num, password: 'secret1', email: `${num}@test.local`, student_name: 'Test Person', student_number: num, program_id: 1, lab_group_id: 1, ...extra });

async function wipe() {                              // only touches 91xxxxxxx / 92xxxxxxx test rows
    await db.query("DELETE FROM students WHERE student_number LIKE '91%' OR student_number LIKE '92%'");
    await db.query("DELETE FROM accounts WHERE username LIKE '91%' OR username LIKE '92%' OR username LIKE 'lectest%'");
}
(async () => {
    await wipe();
    const [[g]] = await db.query("SELECT lab_group_id id FROM labgroup WHERE group_name='G02'");

    console.log('\nStudent registration');
    const a = next();
    let r = await call('/register/student', base(a, { lab_group_id: g.id }));
    ok(r.status === 201, 'new student registers (201)');
    r = await call('/register/student', base(a, { lab_group_id: g.id, username: a + 'x' }));
    ok(r.status === 409 && r.body.code === 'ALREADY_REGISTERED', 'same student number again is rejected (ALREADY_REGISTERED)');
    r = await call('/register/student', base(next(), { student_name: 'Bad123' }));
    ok(r.status === 400, 'invalid name rejected, with a message: ' + r.body.message);

    console.log('\nLecturer added the student first, student registers later (LINK, not a second profile)');
    const b = next();
    r = await call('/students', { student_name: 'Linked Person', student_number: b, program_id: 1, lab_group_id: g.id }, true);
    ok(r.status === 201, 'lecturer adds student with no account yet (201)');
    r = await call('/register/student', base(b, { student_name: 'Someone Else', lab_group_id: g.id }));
    ok(r.status === 403 && r.body.code === 'OWNERSHIP_FAILED', 'wrong name cannot claim the profile (403)');
    r = await call('/register/student', base(b, { student_name: 'linked person', lab_group_id: g.id }));
    ok(r.status === 200 && r.body.code === 'LINKED', 'right name claims the profile (200 LINKED)');
    const [[c]] = await db.query('SELECT COUNT(*) n FROM students WHERE student_number = ?', [b]);
    ok(c.n === 1, 'still exactly ONE profile for that number');

    console.log('\nLecturer registration');
    r = await call('/register/lecturer', { username: 'lectest1', password: 'secret1', email: 'l@test.local' });
    ok(r.status === 403, 'no access code -> refused (403)');
    if (process.env.LECTURER_SIGNUP_CODE) {
        r = await call('/register/lecturer', { username: 'lectest1', password: 'secret1', email: 'l@test.local', lecturer_code: process.env.LECTURER_SIGNUP_CODE });
        ok(r.status === 201, 'correct access code -> lecturer created (201)');
    } else console.log('SKIP  set LECTURER_SIGNUP_CODE in .env to test the success case');

    console.log('\nLast place in G03 taken by two REGISTRATIONS at once (10 rounds)');
    const [[g3]] = await db.query("SELECT lab_group_id id FROM labgroup WHERE group_name='G03'");
    let bad = 0;
    for (let round = 1; round <= 10; round++) {
        await wipe();
        for (let i = 0; i < 14; i++) {
            const num = String(920000000 + i);
            const [acc] = await db.query("INSERT INTO accounts (username,password,email,role) VALUES (?,?,?, 'student')", [num, await bcrypt.hash('x', 4), `${num}@t.local`]);
            await db.query('INSERT INTO students (account_id,student_name,student_number,program_id,lab_group_id) VALUES (?,?,?,1,?)', [acc.insertId, 'Seed Student', num, g3.id]);
        }
        const x = String(910000100 + round * 2), y = String(910000101 + round * 2);
        const [r1, r2] = await Promise.all([call('/register/student', base(x, { lab_group_id: g3.id })), call('/register/student', base(y, { lab_group_id: g3.id }))]);
        const [[cnt]] = await db.query('SELECT COUNT(*) n FROM students WHERE lab_group_id=? AND is_deleted=0', [g3.id]);
        const st = [r1.status, r2.status].sort().join(',');
        const full = [r1, r2].filter(q => q.body.code === 'GROUP_FULL').length;
        if (st !== '201,409' || full !== 1 || cnt.n !== 15) { bad++; console.log(`  round ${round}: ${st}, final=${cnt.n}`); }
    }
    ok(bad === 0, `exactly one registration wins the last place, final count 15 in all 10 rounds (bad: ${bad})`);
    await wipe();
    process.exit();
})().catch(e => { console.error(e); process.exit(1); });
