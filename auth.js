const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');

const db = require('./database');

const {
    nameRegex,
    studentNumberRegex,
    checkLabGroupCapacity
} = require('./middleware');

require('dotenv').config();

const router = express.Router();

const SECRET = process.env.JWT_SECRET;


// =====================================================
// REGISTER STUDENT
// =====================================================

router.post(
    '/register/student',
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

router.post(
    '/register/lecturer',
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

router.post(
    '/login',
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


module.exports = router;