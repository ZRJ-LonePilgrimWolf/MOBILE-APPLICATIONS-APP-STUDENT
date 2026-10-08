const express = require('express');
const mysql = require('mysql2');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const nodemailer = require('nodemailer');
require('dotenv').config();

const app = express();

app.use(express.json());


// =====================================================
// JWT SECRET
// =====================================================

const SECRET = process.env.JWT_SECRET;


// =====================================================
// VALIDATION
// =====================================================

const nameRegex = /^[A-Za-z\s]+$/;
const studentNumberRegex = /^\d{9}$/;


// =====================================================
// DATABASE CONNECTION
// =====================================================

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


// =====================================================
// EMAIL CONFIGURATION
// =====================================================

const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: process.env.EMAIL_USER,
        pass: process.env.EMAIL_PASSWORD
    }
});


// =====================================================
// LAB GROUP CAPACITY
// =====================================================

const MAX_LAB_GROUP_MEMBERS = 15;

function checkLabGroupCapacity(
    labGroupId,
    currentStudentId,
    callback
) {

    let sql = `
        SELECT COUNT(*) AS member_count
        FROM students
        WHERE lab_group_id = ?
    `;

    const values = [labGroupId];

    if (currentStudentId) {
        sql += ` AND student_id != ?`;
        values.push(currentStudentId);
    }

    db.query(
        sql,
        values,
        (err, results) => {

            if (err) {
                return callback(err);
            }

            const memberCount =
                results[0].member_count;

            if (
                memberCount >=
                MAX_LAB_GROUP_MEMBERS
            ) {
                return callback(null, false);
            }

            callback(null, true);
        }
    );
}


// =====================================================
// AUTHENTICATION MIDDLEWARE
// =====================================================

function authenticateToken(req, res, next) {

    const authHeader =
        req.headers['authorization'];

    const token =
        authHeader &&
        authHeader.split(' ')[1];

    if (!token) {
        return res.status(401).json({
            message:
                'Access denied. Please login first.'
        });
    }

    jwt.verify(
        token,
        SECRET,
        (err, user) => {

            if (err) {
                return res.status(403).json({
                    message:
                        'Invalid or expired token.'
                });
            }

            req.user = user;

            next();
        }
    );
}


// =====================================================
// ROLE MIDDLEWARE
// =====================================================

function requireRole(role) {

    return (req, res, next) => {

        if (!req.user) {
            return res.status(401).json({
                message:
                    'Authentication required.'
            });
        }

        if (req.user.role !== role) {
            return res.status(403).json({
                message:
                    'Access denied. You do not have permission.'
            });
        }

        next();
    };
}


// =====================================================
// REGISTER STUDENT
// =====================================================

app.post(
    '/api/register/student',
    async (req, res) => {

        const {
            username,
            password,
            email,
            student_name,
            student_number,
            program_id,
            lab_group_id
        } = req.body;


        if (
            !username ||
            !password ||
            !email ||
            !student_name ||
            !student_number ||
            !program_id ||
            !lab_group_id
        ) {

            return res.status(400).json({
                message:
                    'Please provide all required fields'
            });
        }


        if (!nameRegex.test(student_name)) {

            return res.status(400).json({
                message:
                    'Student name must contain letters and spaces only'
            });
        }


        if (!studentNumberRegex.test(student_number)) {

            return res.status(400).json({
                message:
                    'Student number must contain exactly 9 digits'
            });
        }


        const checkStudentSql = `
            SELECT student_id
            FROM students
            WHERE student_number = ?
        `;


        db.query(
            checkStudentSql,
            [student_number],
            async (err, results) => {

                if (err) {
                    return res.status(500).json({
                        error: err.message
                    });
                }


                if (results.length > 0) {

                    return res.status(409).json({
                        message:
                            'Student number already exists'
                    });
                }


                checkLabGroupCapacity(
                    lab_group_id,
                    null,
                    async (
                        capacityErr,
                        available
                    ) => {

                        if (capacityErr) {
                            return res.status(500).json({
                                error:
                                    capacityErr.message
                            });
                        }


                        if (!available) {

                            return res.status(409).json({
                                message:
                                    'This lab group is full. A lab group can have a maximum of 15 students.'
                            });
                        }


                        try {

                            const hashedPassword =
                                await bcrypt.hash(
                                    password,
                                    10
                                );


                            const accountSql = `
                                INSERT INTO accounts
                                (
                                    username,
                                    password,
                                    email,
                                    role
                                )
                                VALUES (?, ?, ?, 'student')
                            `;


                            db.query(
                                accountSql,
                                [
                                    username,
                                    hashedPassword,
                                    email
                                ],
                                (err, accountResult) => {

                                    if (err) {

                                        if (
                                            err.code ===
                                            'ER_DUP_ENTRY'
                                        ) {

                                            return res.status(409).json({
                                                message:
                                                    'Username or email already exists'
                                            });
                                        }

                                        return res.status(500).json({
                                            error:
                                                err.message
                                        });
                                    }


                                    const accountId =
                                        accountResult.insertId;


                                    const studentSql = `
                                        INSERT INTO students
                                        (
                                            account_id,
                                            student_name,
                                            student_number,
                                            program_id,
                                            lab_group_id
                                        )
                                        VALUES (?, ?, ?, ?, ?)
                                    `;


                                    db.query(
                                        studentSql,
                                        [
                                            accountId,
                                            student_name,
                                            student_number,
                                            program_id,
                                            lab_group_id
                                        ],
                                        (err, studentResult) => {

                                            if (err) {

                                                if (
                                                    err.code ===
                                                    'ER_DUP_ENTRY'
                                                ) {

                                                    return res.status(409).json({
                                                        message:
                                                            'Student number already exists'
                                                    });
                                                }

                                                return res.status(500).json({
                                                    error:
                                                        err.message
                                                });
                                            }


                                            res.status(201).json({
                                                message:
                                                    'Student registered successfully',

                                                account_id:
                                                    accountId,

                                                student_id:
                                                    studentResult.insertId,

                                                role:
                                                    'student'
                                            });
                                        }
                                    );
                                }
                            );

                        } catch (error) {

                            res.status(500).json({
                                error:
                                    error.message
                            });
                        }
                    }
                );
            }
        );
    }
);


// =====================================================
// REGISTER LECTURER
// =====================================================

app.post(
    '/api/register/lecturer',
    async (req, res) => {

        const {
            username,
            password,
            email
        } = req.body;


        if (
            !username ||
            !password ||
            !email
        ) {

            return res.status(400).json({
                message:
                    'Username, password and email are required'
            });
        }


        try {

            const hashedPassword =
                await bcrypt.hash(
                    password,
                    10
                );


            const sql = `
                INSERT INTO accounts
                (
                    username,
                    password,
                    email,
                    role
                )
                VALUES (?, ?, ?, 'lecturer')
            `;


            db.query(
                sql,
                [
                    username,
                    hashedPassword,
                    email
                ],
                (err, result) => {

                    if (err) {

                        if (
                            err.code ===
                            'ER_DUP_ENTRY'
                        ) {

                            return res.status(409).json({
                                message:
                                    'Username or email already exists'
                            });
                        }

                        return res.status(500).json({
                            error:
                                err.message
                        });
                    }


                    res.status(201).json({

                        message:
                            'Lecturer account created successfully',

                        account_id:
                            result.insertId,

                        role:
                            'lecturer'
                    });
                }
            );

        } catch (error) {

            res.status(500).json({
                error:
                    error.message
            });
        }
    }
);


// =====================================================
// LOGIN
// =====================================================

app.post(
    '/api/login',
    (req, res) => {

        const {
            username,
            password
        } = req.body;


        if (
            !username ||
            !password
        ) {

            return res.status(400).json({
                message:
                    'Username and password are required'
            });
        }


        const sql = `
            SELECT *
            FROM accounts
            WHERE username = ?
        `;


        db.query(
            sql,
            [username],
            async (err, results) => {

                if (err) {
                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length === 0) {

                    return res.status(401).json({
                        message:
                            'Invalid username or password'
                    });
                }


                const account =
                    results[0];


                const passwordMatch =
                    await bcrypt.compare(
                        password,
                        account.password
                    );


                if (!passwordMatch) {

                    return res.status(401).json({
                        message:
                            'Invalid username or password'
                    });
                }


                const token =
                    jwt.sign(
                        {
                            account_id:
                                account.account_id,

                            username:
                                account.username,

                            role:
                                account.role
                        },

                        SECRET,

                        {
                            expiresIn:
                                '2h'
                        }
                    );


                res.json({

                    message:
                        'Login successful',

                    account_id:
                        account.account_id,

                    username:
                        account.username,

                    role:
                        account.role,

                    token:
                        token
                });
            }
        );
    }
);


// =====================================================
// FORGOT PASSWORD
// SEND 6-DIGIT RESET CODE BY EMAIL
// =====================================================

app.post(
    '/api/forgot-password',
    (req, res) => {

        const {
            username
        } = req.body;


        if (!username) {

            return res.status(400).json({
                message:
                    'Username is required'
            });
        }


        const findAccountSql = `
            SELECT
                account_id,
                username,
                email,
                role
            FROM accounts
            WHERE username = ?
        `;


        db.query(
            findAccountSql,
            [username],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length === 0) {

                    return res.status(404).json({
                        message:
                            'Account not found'
                    });
                }


                const account =
                    results[0];


                if (!account.email) {

                    return res.status(400).json({
                        message:
                            'No email address is registered for this account'
                    });
                }


                // Generate 6-digit code
                const resetCode =
                    Math.floor(
                        100000 +
                        Math.random() * 900000
                    ).toString();


                // Code expires in 15 minutes
                const expiresAt =
                    new Date(
                        Date.now() +
                        15 * 60 * 1000
                    );


                // Delete previous codes
                const deleteOldCodesSql = `
                    DELETE FROM password_reset_codes
                    WHERE account_id = ?
                `;


                db.query(
                    deleteOldCodesSql,
                    [account.account_id],
                    (err) => {

                        if (err) {

                            return res.status(500).json({
                                error:
                                    err.message
                            });
                        }


                        // Store new reset code
                        const insertCodeSql = `
                            INSERT INTO password_reset_codes
                            (
                                account_id,
                                reset_code,
                                expires_at
                            )
                            VALUES (?, ?, ?)
                        `;


                        db.query(
                            insertCodeSql,
                            [
                                account.account_id,
                                resetCode,
                                expiresAt
                            ],
                            async (err) => {

                                if (err) {

                                    return res.status(500).json({
                                        error:
                                            err.message
                                    });
                                }


                                try {

                                    await transporter.sendMail({

                                        from:
                                            `"Student Management System" <${process.env.EMAIL_USER}>`,

                                        to:
                                            account.email,

                                        subject:
                                            'Password Reset Code',

                                        text:
                                            `Hello ${account.username},\n\n` +
                                            `Your password reset code is: ${resetCode}\n\n` +
                                            `This code will expire in 15 minutes.\n\n` +
                                            `If you did not request a password reset, please ignore this email.\n\n` +
                                            `Student Management System`
                                    });


                                    const maskedEmail =
                                        account.email.replace(
                                            /^(.{2}).*(@.*)$/,
                                            '$1****$2'
                                        );


                                    res.json({

                                        message:
                                            'Password reset code has been sent to your registered email address.',

                                        email:
                                            maskedEmail,

                                        expires_in:
                                            '15 minutes'
                                    });


                                } catch (emailError) {

                                    // Delete code if email failed
                                    db.query(
                                        `
                                        DELETE FROM password_reset_codes
                                        WHERE account_id = ?
                                        `,
                                        [
                                            account.account_id
                                        ]
                                    );


                                    return res.status(500).json({

                                        message:
                                            'Unable to send password reset email',

                                        error:
                                            emailError.message
                                    });
                                }
                            }
                        );
                    }
                );
            }
        );
    }
);


// =====================================================
// RESET PASSWORD USING 6-DIGIT CODE
// =====================================================

app.post(
    '/api/reset-password',
    async (req, res) => {

        const {
            reset_code,
            new_password
        } = req.body;


        if (
            !reset_code ||
            !new_password
        ) {

            return res.status(400).json({
                message:
                    'Reset code and new password are required'
            });
        }


        if (
            !/^\d{6}$/.test(reset_code)
        ) {

            return res.status(400).json({
                message:
                    'Reset code must contain exactly 6 digits'
            });
        }


        try {

            const findCodeSql = `
                SELECT
                    reset_id,
                    account_id,
                    reset_code,
                    expires_at
                FROM password_reset_codes
                WHERE reset_code = ?
                ORDER BY created_at DESC
                LIMIT 1
            `;


            db.query(
                findCodeSql,
                [reset_code],
                async (err, results) => {

                    if (err) {

                        return res.status(500).json({
                            error:
                                err.message
                        });
                    }


                    if (results.length === 0) {

                        return res.status(403).json({
                            message:
                                'Invalid reset code'
                        });
                    }


                    const resetRecord =
                        results[0];


                    // Check expiration
                    if (
                        new Date(
                            resetRecord.expires_at
                        ) < new Date()
                    ) {

                        db.query(
                            `
                            DELETE FROM password_reset_codes
                            WHERE reset_id = ?
                            `,
                            [
                                resetRecord.reset_id
                            ]
                        );


                        return res.status(403).json({
                            message:
                                'Password reset code has expired'
                        });
                    }


                    // Hash new password
                    const hashedPassword =
                        await bcrypt.hash(
                            new_password,
                            10
                        );


                    const updatePasswordSql = `
                        UPDATE accounts
                        SET password = ?
                        WHERE account_id = ?
                    `;


                    db.query(
                        updatePasswordSql,
                        [
                            hashedPassword,
                            resetRecord.account_id
                        ],
                        (err, result) => {

                            if (err) {

                                return res.status(500).json({
                                    error:
                                        err.message
                                });
                            }


                            if (
                                result.affectedRows === 0
                            ) {

                                return res.status(404).json({
                                    message:
                                        'Account not found'
                                });
                            }


                            // Delete reset code
                            const deleteCodeSql = `
                                DELETE FROM password_reset_codes
                                WHERE reset_id = ?
                            `;


                            db.query(
                                deleteCodeSql,
                                [
                                    resetRecord.reset_id
                                ],
                                (err) => {

                                    if (err) {

                                        return res.status(500).json({
                                            error:
                                                err.message
                                        });
                                    }


                                    res.json({

                                        message:
                                            'Password reset successfully'
                                    });
                                }
                            );
                        }
                    );
                }
            );

        } catch (error) {

            res.status(500).json({
                error:
                    error.message
            });
        }
    }
);


// =====================================================
// STUDENT ACCESS
// =====================================================

app.get(
    '/api/student/me',
    authenticateToken,
    requireRole('student'),
    (req, res) => {

        const sql = `
            SELECT
                students.student_id,
                students.student_name,
                students.student_number,

                accounts.account_id,
                accounts.username,
                accounts.email,
                accounts.role,

                program.program_id,
                program.program_name,

                labgroup.lab_group_id,
                labgroup.group_name

            FROM students

            INNER JOIN accounts
                ON students.account_id =
                   accounts.account_id

            INNER JOIN program
                ON students.program_id =
                   program.program_id

            INNER JOIN labgroup
                ON students.lab_group_id =
                   labgroup.lab_group_id

            WHERE students.account_id = ?
        `;


        db.query(
            sql,
            [req.user.account_id],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length === 0) {

                    return res.status(404).json({
                        message:
                            'Student information not found'
                    });
                }


                res.json(results[0]);
            }
        );
    }
);


// =====================================================
// GET ALL PROGRAMS
// =====================================================

app.get(
    '/api/programs',
    authenticateToken,
    (req, res) => {

        const sql = `
            SELECT *
            FROM program
            ORDER BY program_name
        `;


        db.query(
            sql,
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                res.json(results);
            }
        );
    }
);


// =====================================================
// CREATE PROGRAM
// =====================================================

app.post(
    '/api/programs',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const {
            program_name
        } = req.body;


        if (!program_name) {

            return res.status(400).json({
                message:
                    'Program name is required'
            });
        }


        const sql = `
            INSERT INTO program
            (program_name)
            VALUES (?)
        `;


        db.query(
            sql,
            [program_name],
            (err, result) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                res.status(201).json({

                    message:
                        'Program created successfully',

                    program_id:
                        result.insertId
                });
            }
        );
    }
);


// =====================================================
// GET ALL LAB GROUPS
// =====================================================

app.get(
    '/api/labgroups',
    authenticateToken,
    (req, res) => {

        const sql = `
            SELECT
                labgroup.lab_group_id,
                labgroup.group_name,
                COUNT(students.student_id)
                    AS member_count

            FROM labgroup

            LEFT JOIN students
                ON labgroup.lab_group_id =
                   students.lab_group_id

            GROUP BY
                labgroup.lab_group_id,
                labgroup.group_name

            ORDER BY
                labgroup.group_name
        `;


        db.query(
            sql,
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                res.json(results);
            }
        );
    }
);


// =====================================================
// CREATE LAB GROUP
// =====================================================

app.post(
    '/api/labgroups',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const {
            group_name
        } = req.body;


        if (!group_name) {

            return res.status(400).json({
                message:
                    'Lab group name is required'
            });
        }


        const sql = `
            INSERT INTO labgroup
            (group_name)
            VALUES (?)
        `;


        db.query(
            sql,
            [group_name],
            (err, result) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                res.status(201).json({

                    message:
                        'Lab group created successfully',

                    lab_group_id:
                        result.insertId,

                    maximum_members:
                        MAX_LAB_GROUP_MEMBERS
                });
            }
        );
    }
);


// =====================================================
// CREATE STUDENT
// =====================================================

app.post(
    '/api/students',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const {
            account_id,
            student_name,
            student_number,
            program_id,
            lab_group_id
        } = req.body;


        if (
            !account_id ||
            !student_name ||
            !student_number ||
            !program_id ||
            !lab_group_id
        ) {

            return res.status(400).json({
                message:
                    'Please provide all required fields'
            });
        }


        if (!nameRegex.test(student_name)) {

            return res.status(400).json({
                message:
                    'Student name must contain letters and spaces only'
            });
        }


        if (!studentNumberRegex.test(student_number)) {

            return res.status(400).json({
                message:
                    'Student number must contain exactly 9 digits'
            });
        }


        const checkStudentSql = `
            SELECT student_id
            FROM students
            WHERE student_number = ?
        `;


        db.query(
            checkStudentSql,
            [student_number],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length > 0) {

                    return res.status(409).json({
                        message:
                            'Student number already exists'
                    });
                }


                checkLabGroupCapacity(
                    lab_group_id,
                    null,
                    (capacityErr, available) => {

                        if (capacityErr) {

                            return res.status(500).json({
                                error:
                                    capacityErr.message
                            });
                        }


                        if (!available) {

                            return res.status(409).json({
                                message:
                                    'This lab group is full. A lab group can have a maximum of 15 students.'
                            });
                        }


                        const sql = `
                            INSERT INTO students
                            (
                                account_id,
                                student_name,
                                student_number,
                                program_id,
                                lab_group_id
                            )
                            VALUES (?, ?, ?, ?, ?)
                        `;


                        db.query(
                            sql,
                            [
                                account_id,
                                student_name,
                                student_number,
                                program_id,
                                lab_group_id
                            ],
                            (err, result) => {

                                if (err) {

                                    if (
                                        err.code ===
                                        'ER_DUP_ENTRY'
                                    ) {

                                        return res.status(409).json({
                                            message:
                                                'Student number already exists'
                                        });
                                    }


                                    return res.status(500).json({
                                        error:
                                            err.message
                                    });
                                }


                                res.status(201).json({

                                    message:
                                        'Student created successfully',

                                    student_id:
                                        result.insertId
                                });
                            }
                        );
                    }
                );
            }
        );
    }
);


// =====================================================
// READ / SEARCH ALL STUDENTS
// =====================================================

app.get(
    '/api/students',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const {
            labgroup,
            program
        } = req.query;


        let sql = `
            SELECT
                students.student_id,
                students.student_name,
                students.student_number,

                accounts.account_id,
                accounts.username,
                accounts.email,
                accounts.role,

                program.program_name,
                labgroup.group_name

            FROM students

            INNER JOIN accounts
                ON students.account_id =
                   accounts.account_id

            INNER JOIN program
                ON students.program_id =
                   program.program_id

            INNER JOIN labgroup
                ON students.lab_group_id =
                   labgroup.lab_group_id
        `;


        const conditions = [];
        const values = [];


        if (labgroup) {

            conditions.push(
                'labgroup.group_name LIKE ?'
            );

            values.push(
                `%${labgroup}%`
            );
        }


        if (program) {

            conditions.push(
                'program.program_name LIKE ?'
            );

            values.push(
                `%${program}%`
            );
        }


        if (conditions.length > 0) {

            sql +=
                ' WHERE ' +
                conditions.join(' AND ');
        }


        sql += `
            ORDER BY students.student_id
        `;


        db.query(
            sql,
            values,
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                res.json({
                    count:
                        results.length,

                    students:
                        results
                });
            }
        );
    }
);


// =====================================================
// READ ONE STUDENT
// =====================================================

app.get(
    '/api/students/:id',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const sql = `
            SELECT
                students.student_id,
                students.student_name,
                students.student_number,

                accounts.account_id,
                accounts.username,
                accounts.email,
                accounts.role,

                program.program_id,
                program.program_name,

                labgroup.lab_group_id,
                labgroup.group_name

            FROM students

            INNER JOIN accounts
                ON students.account_id =
                   accounts.account_id

            INNER JOIN program
                ON students.program_id =
                   program.program_id

            INNER JOIN labgroup
                ON students.lab_group_id =
                   labgroup.lab_group_id

            WHERE students.student_id = ?
        `;


        db.query(
            sql,
            [req.params.id],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length === 0) {

                    return res.status(404).json({
                        message:
                            'Student not found'
                    });
                }


                res.json(results[0]);
            }
        );
    }
);


// =====================================================
// UPDATE STUDENT
// =====================================================

app.put(
    '/api/students/:id',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const {
            student_name,
            student_number,
            program_id,
            lab_group_id
        } = req.body;


        if (
            !student_name ||
            !student_number ||
            !program_id ||
            !lab_group_id
        ) {

            return res.status(400).json({
                message:
                    'Please provide all required fields'
            });
        }


        if (!nameRegex.test(student_name)) {

            return res.status(400).json({
                message:
                    'Student name must contain letters and spaces only'
            });
        }


        if (!studentNumberRegex.test(student_number)) {

            return res.status(400).json({
                message:
                    'Student number must contain exactly 9 digits'
            });
        }


        const checkStudentSql = `
            SELECT student_id
            FROM students
            WHERE student_number = ?
            AND student_id != ?
        `;


        db.query(
            checkStudentSql,
            [
                student_number,
                req.params.id
            ],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length > 0) {

                    return res.status(409).json({
                        message:
                            'Student number already exists'
                    });
                }


                const currentStudentSql = `
                    SELECT lab_group_id
                    FROM students
                    WHERE student_id = ?
                `;


                db.query(
                    currentStudentSql,
                    [req.params.id],
                    (err, currentStudent) => {

                        if (err) {

                            return res.status(500).json({
                                error:
                                    err.message
                            });
                        }


                        if (
                            currentStudent.length === 0
                        ) {

                            return res.status(404).json({
                                message:
                                    'Student not found'
                            });
                        }


                        const currentLabGroupId =
                            currentStudent[0]
                                .lab_group_id;


                        if (
                            String(
                                currentLabGroupId
                            ) !==
                            String(
                                lab_group_id
                            )
                        ) {

                            checkLabGroupCapacity(
                                lab_group_id,
                                req.params.id,
                                (capacityErr, available) => {

                                    if (capacityErr) {

                                        return res.status(500).json({
                                            error:
                                                capacityErr.message
                                        });
                                    }


                                    if (!available) {

                                        return res.status(409).json({
                                            message:
                                                'The new lab group is full. A lab group can have a maximum of 15 students.'
                                        });
                                    }


                                    updateStudent();
                                }
                            );

                        } else {

                            updateStudent();
                        }


                        function updateStudent() {

                            const sql = `
                                UPDATE students
                                SET
                                    student_name = ?,
                                    student_number = ?,
                                    program_id = ?,
                                    lab_group_id = ?
                                WHERE student_id = ?
                            `;


                            db.query(
                                sql,
                                [
                                    student_name,
                                    student_number,
                                    program_id,
                                    lab_group_id,
                                    req.params.id
                                ],
                                (err, result) => {

                                    if (err) {

                                        if (
                                            err.code ===
                                            'ER_DUP_ENTRY'
                                        ) {

                                            return res.status(409).json({
                                                message:
                                                    'Student number already exists'
                                            });
                                        }


                                        return res.status(500).json({
                                            error:
                                                err.message
                                        });
                                    }


                                    if (
                                        result.affectedRows === 0
                                    ) {

                                        return res.status(404).json({
                                            message:
                                                'Student not found'
                                        });
                                    }


                                    res.json({

                                        message:
                                            'Student updated successfully'
                                    });
                                }
                            );
                        }
                    }
                );
            }
        );
    }
);


// =====================================================
// DELETE STUDENT
// =====================================================

app.delete(
    '/api/students/:id',
    authenticateToken,
    requireRole('lecturer'),
    (req, res) => {

        const findSql = `
            SELECT account_id
            FROM students
            WHERE student_id = ?
        `;


        db.query(
            findSql,
            [req.params.id],
            (err, results) => {

                if (err) {

                    return res.status(500).json({
                        error:
                            err.message
                    });
                }


                if (results.length === 0) {

                    return res.status(404).json({
                        message:
                            'Student not found'
                    });
                }


                const accountId =
                    results[0].account_id;


                const deleteStudentSql = `
                    DELETE FROM students
                    WHERE student_id = ?
                `;


                db.query(
                    deleteStudentSql,
                    [req.params.id],
                    (err) => {

                        if (err) {

                            return res.status(500).json({
                                error:
                                    err.message
                            });
                        }


                        const deleteAccountSql = `
                            DELETE FROM accounts
                            WHERE account_id = ?
                        `;


                        db.query(
                            deleteAccountSql,
                            [accountId],
                            (err) => {

                                if (err) {

                                    return res.status(500).json({
                                        error:
                                            err.message
                                    });
                                }


                                res.json({

                                    message:
                                        'Student and account deleted successfully'
                                });
                            }
                        );
                    }
                );
            }
        );
    }
);


// =====================================================
// SERVER
// =====================================================

app.listen(
    3000,
    () => {

        console.log(
            'Server running on port 3000'
        );
    }
);