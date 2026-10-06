const express = require('express');
const bcrypt = require('bcrypt');
const nodemailer = require('nodemailer');

const db = require('./database');

require('dotenv').config();

const router = express.Router();


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
// FORGOT PASSWORD
// SEND 6-DIGIT RESET CODE BY EMAIL
// =====================================================

router.post(
    '/forgot-password',
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

router.post(
    '/reset-password',
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


module.exports = router;