const express = require('express');

require('dotenv').config();

const authRoutes = require('./auth');
const passwordResetRoutes = require('./passwordReset');
const studentRoutes = require('./students');
const studentsSafe = require('./studentsSafe');

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

// Must come BEFORE studentRoutes so the safe POST /students wins
app.use('/api', studentsSafe);
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