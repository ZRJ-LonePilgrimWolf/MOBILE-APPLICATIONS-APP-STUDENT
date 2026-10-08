const jwt = require('jsonwebtoken');

require('dotenv').config();

const db = require('./database');

const SECRET = process.env.JWT_SECRET;


// =====================================================
// VALIDATION
// =====================================================

const nameRegex = /^[A-Za-z\s]+$/;
const studentNumberRegex = /^\d{9}$/;


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


module.exports = {
    nameRegex,
    studentNumberRegex,
    MAX_LAB_GROUP_MEMBERS,
    checkLabGroupCapacity,
    authenticateToken,
    requireRole
};