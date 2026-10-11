const express = require('express');

const db = require('./database');

const {
    nameRegex,
    studentNumberRegex,
    MAX_LAB_GROUP_MEMBERS,
    checkLabGroupCapacity,
    authenticateToken,
    requireRole
} = require('./middleware');

const router = express.Router();


// =====================================================
// STUDENT ACCESS
// =====================================================

router.get(
    '/student/me',
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

router.get(
    '/programs',
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

router.post(
    '/programs',
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

router.get(
    '/labgroups',
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

router.post(
    '/labgroups',
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

router.post(
    '/students',
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

router.get(
    '/students',
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

router.get(
    '/students/:id',
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

router.put(
    '/students/:id',
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

router.delete(
    '/students/:id',
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


module.exports = router;