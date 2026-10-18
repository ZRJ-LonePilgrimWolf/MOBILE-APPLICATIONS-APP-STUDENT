const express = require('express');
require('dotenv').config();
const registerSafe = require('./registerSafe');
   const reference = require('./reference');
      const chat = require('./chat');
         const approvals = require('./approvals');
         const selfService = require('./selfService');
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
         app.use('/api', selfService);
app.use('/api', authRoutes);
app.use('/api', passwordResetRoutes);
app.use('/api', studentsSafe);
app.use('/api', studentsUpdateSafe);
app.use('/api', studentRoutes);

// =====================================================
// SERVER
// =====================================================
// Simple page to check the server is alive (open it in a browser)
app.get('/', (req, res) => res.send('Nexa API is running'));

// Render gives its own port in process.env.PORT; the laptop uses 3000
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
    console.log('Server running on port ' + PORT);
});