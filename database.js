const mysql = require('mysql2');

const db = mysql.createPool({
    host: 'localhost',
       user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'ict361_lab',
    connectionLimit: 10
});

db.query('SELECT 1', (err) => {
    if (err) { console.log('Database connection failed:', err.message); return; }
    console.log('Database connected!');
});

module.exports = db;