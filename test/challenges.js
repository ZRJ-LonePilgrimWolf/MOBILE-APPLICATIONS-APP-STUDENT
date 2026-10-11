// Run:  node test/challenges.js        (server must be running on :3000, DB = ict361_lab)
// Needs the same .env as the server (JWT_SECRET, DB password in database.js).
require('dotenv').config({ path: require('path').join(__dirname, '..', '.env') });
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const db = require('../database').promise();

const API = process.env.API || 'http://localhost:3000/api';
const token = jwt.sign({ account_id: 1, username: '100000001', role: 'lecturer' }, process.env.JWT_SECRET, { expiresIn: '10m' });
const post = (body) => fetch(`${API}/students`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body)
}).then(async r => ({ status: r.status, body: await r.json() }));

let seq = 0;
async function newAccount() {                       // fake student account, created straight in the DB
    seq++;
    const u = `T${Date.now()}${seq}`.slice(0, 20);
    const [r] = await db.query("INSERT INTO accounts (username,password,email,role) VALUES (?,?,?,'student')",
        [u, await bcrypt.hash('x', 4), `${u}@test.local`]);
    return r.insertId;
}
let num = 900000000;
const nextNumber = () => String(++num);
async function reset(groupId, fill) {               // wipe test students, then fill group with `fill` students
    await db.query("DELETE FROM students WHERE student_number >= '900000000'");
    await db.query("DELETE FROM operations WHERE operation_id LIKE 'test-%'");
    await db.query("DELETE FROM accounts WHERE username LIKE 'T%' AND role='student'");
    for (let i = 0; i < fill; i++) {
        await db.query('INSERT INTO students (account_id,student_name,student_number,program_id,lab_group_id) VALUES (?,?,?,1,?)',
            [await newAccount(), 'Seed Student', nextNumber(), groupId]);
    }
}
const count = async (g) => (await db.query('SELECT COUNT(*) n FROM students WHERE lab_group_id=? AND is_deleted=0', [g]))[0][0].n;
const ok = (c, m) => { console.log(`${c ? 'PASS' : 'FAIL'}  ${m}`); if (!c) process.exitCode = 1; };

(async () => {
    const [[g]] = await db.query("SELECT lab_group_id id FROM labgroup WHERE group_name='G01'");

    console.log('\nChallenge 1: two requests race for the last place in G01 (20 rounds)');
    let bad = 0;
    for (let round = 1; round <= 20; round++) {
        await reset(g.id, 14);
        const a = await newAccount(), b = await newAccount();
        const [r1, r2] = await Promise.all([
            post({ account_id: a, student_name: 'Race One', student_number: nextNumber(), program_id: 1, lab_group_id: g.id }),
            post({ account_id: b, student_name: 'Race Two', student_number: nextNumber(), program_id: 1, lab_group_id: g.id })
        ]);
        const statuses = [r1.status, r2.status].sort().join(',');
        const full = [r1, r2].filter(r => r.body.code === 'GROUP_FULL').length;
        const final = await count(g.id);
        if (statuses !== '201,409' || full !== 1 || final !== 15) { bad++; console.log(`  round ${round}: ${statuses}, final=${final}`); }
    }
    ok(bad === 0, `Exactly one 201 and one GROUP_FULL, final count 15 in all 20 rounds (bad rounds: ${bad})`);

    console.log('\nChallenge 2: same operation_id sent twice (response was "lost")');
    await reset(g.id, 3);
    const acc = await newAccount(), sn = nextNumber(), op = 'test-op-1';
    const first = await post({ operation_id: op, account_id: acc, student_name: 'Retry Student', student_number: sn, program_id: 1, lab_group_id: g.id });
    const second = await post({ operation_id: op, account_id: acc, student_name: 'Retry Student', student_number: sn, program_id: 1, lab_group_id: g.id });
    const [rows] = await db.query('SELECT COUNT(*) n FROM students WHERE student_number=?', [sn]);
    ok(first.status === 201, 'first attempt created the student');
    ok(second.status === 201 && second.body.student_id === first.body.student_id, 'retry returned the SAME result (same student_id)');
    ok(rows[0].n === 1, 'only ONE student row exists');
    console.log('  Restart the server now and run this block again with the same operation_id to prove it survives restarts (the answer is stored in MySQL).');

    console.log('\nDuplicate student number');
    const dup = await post({ account_id: await newAccount(), student_name: 'Dup Student', student_number: sn, program_id: 1, lab_group_id: g.id });
    ok(dup.status === 409 && dup.body.code === 'DUPLICATE', 'second student with same number rejected (DUPLICATE)');

    await reset(g.id, 0);
    process.exit();
})().catch(e => { console.error(e); process.exit(1); });
