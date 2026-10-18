const mysql = require('mysql2');

// Laptop: nothing set -> localhost / root / ict361_lab (same as before)
// Online (Render): DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, DB_SSL=true
const useSsl = process.env.DB_SSL === 'true';

const db = mysql.createPool({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT) || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'ict361_lab',
    connectionLimit: 10,
    ssl: useSsl ? { minVersion: 'TLSv1.2', rejectUnauthorized: true } : undefined
});

db.query('SELECT 1', (err) => {
    if (err) { console.log('Database connection failed:', err.message); return; }
    console.log('Database connected!' + (useSsl ? ' (secure SSL)' : ''));
});

module.exports = db;