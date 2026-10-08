const express = require('express');
const router = express.Router();
const pool = require('../db');

router.post('/groups/:id/join', async (req,res)=>{
  const conn = await pool.getConnection();
  try{
    await conn.beginTransaction();
    const groupId = req.params.id;
    await conn.query('SELECT id FROM groups_table WHERE id=? FOR UPDATE',[groupId]);
    const [r] = await conn.query('UPDATE groups_table SET member_count = member_count + 1 WHERE id=? AND member_count < 100',[groupId]);
    if(r.affectedRows===0){
      await conn.rollback();
      return res.status(400).json({error:'full'});
    }
    await conn.commit();
    res.json({ok:true});
  }catch(e){
    await conn.rollback();
    console.log('ERR',e.message);
    res.status(500).json({error:e.message});
  }finally{
    conn.release();
  }
});

module.exports = router;