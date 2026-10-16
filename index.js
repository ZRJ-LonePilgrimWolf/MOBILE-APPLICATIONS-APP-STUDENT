const express = require('express');
require('dotenv').config();
const registerSafe = require('./registerSafe');
   const reference = require('./reference');
      const chat = require('./chat');
         const approvals = require('./approvals');
const authRoutes = require('./auth');
const passwordResetRoutes = require('./passwordReset');
const studentsSafe = require('./studentsSafe');
const studentsUpdateSafe = require('./studentsUpdateSafe');
const studentRoutes = require('./students');
const app = express();

// =====================================================
// MIDDLEWARE
// =====================================================
app.use(express.json());

// =====================================================
// ROUTES  (the "Safe" files must come BEFORE the old ones so they win)
// =====================================================
app.use('/api', registerSafe);
   app.use('/api', reference);
      app.use('/api', chat);
         app.use('/api', approvals);
app.use('/api', authRoutes);
app.use('/api', passwordResetRoutes);
app.use('/api', studentsSafe);
app.use('/api', studentsUpdateSafe);
app.use('/api', studentRoutes);

// =====================================================
// SERVER
// =====================================================
app.listen(3000, () => {
    console.log('Server running on port 3000');
});
