const mysql = require('mysql2');

const db = mysql.createConnection({
    host: 'localhost',
    user: 'Timo',
    password: '7777',
    database: 'Backend'
});

db.connect((err) => {
    if (err) {
        console.log('Database connection failed:', err.message);
        return;
    }

    console.log('Database connected!');
});

module.exports = db;