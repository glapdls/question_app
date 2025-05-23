// routes/users.js
const express = require('express');
const router = express.Router();

router.get('/', (req, res) => {
  res.send('사용자 라우터 응답!');
});

module.exports = router;
