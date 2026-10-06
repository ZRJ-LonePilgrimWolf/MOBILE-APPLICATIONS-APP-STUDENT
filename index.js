const express = require('express');

require('dotenv').config();

const authRoutes = require('./auth');
const passwordResetRoutes = require('./passwordReset');
const studentRoutes = require('./students');

const app = express();


// =====================================================
// MIDDLEWARE
// =====================================================

app.use(express.json());


// =====================================================
// ROUTES
// =====================================================

app.use('/api', authRoutes);

app.use('/api', passwordResetRoutes);

app.use('/api', studentRoutes);


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