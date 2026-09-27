const express = require('express');
const app = express();
app.use(express.json());
app.use('/api', require('./routes/students'));
app.use('/api', require('./routes/groups'));
app.listen(3000, () => console.log('Listening on 3000'));