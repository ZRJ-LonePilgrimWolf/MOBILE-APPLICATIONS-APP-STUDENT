const express = require('express');
const cors = require('cors');
const app = express();

app.use(cors());
app.use(express.json());

// Enhanced logging for offline sync
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url} - OpID: ${req.headers['x-operation-id'] || req.body.operation_id || 'none'}`);
  next();
});

app.use('/api', require('./routes/students'));
app.use('/api', require('./routes/groups'));

// Health check for Android WorkManager
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', server_time: new Date(), offline_ready: true });
});

app.listen(3000, () => console.log('Enhanced Backend Listening on 3000 - Offline Ready'));