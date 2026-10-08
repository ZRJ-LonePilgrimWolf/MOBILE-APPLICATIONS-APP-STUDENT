const fetch = require('node-fetch');
const crypto = require('crypto');
const mysql = require('mysql2/promise');

const GROUP_ID = 2; // CHANGE 3 BACK TO 2

async function resetFixture(conn) {
  await conn.query("UPDATE groups_table SET member_count = 0 WHERE id = ?", [GROUP_ID]);
}

async function runOnce(conn, i) {
  await resetFixture(conn);
  const [r1, r2] = await Promise.all([
    fetch(`http://localhost:3000/api/groups/${GROUP_ID}/join`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ studentId: 101 })
    }),
    fetch(`http://localhost:3000/api/groups/${GROUP_ID}/join`, {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({ studentId: 102 })
    }),
  ]);
  const codes = [r1.status, r2.status].sort();
  const [count] = await conn.query('SELECT member_count FROM groups_table WHERE id=?', [GROUP_ID]);
  const ok = JSON.stringify(codes) === JSON.stringify([200,200]) && count[0].member_count === 2;
  console.log(`Run ${i+1}: codes=${JSON.stringify(codes)} count=${count[0].member_count} ${ok ? 'PASS' : 'FAIL'}`);
}

(async () => {
  const conn = await mysql.createConnection({
    host: 'localhost', user: 'root', password: '', database: 'lab_registration'
  });
  for (let i = 0; i < 20; i++) await runOnce(conn, i);
  process.exit();
})();