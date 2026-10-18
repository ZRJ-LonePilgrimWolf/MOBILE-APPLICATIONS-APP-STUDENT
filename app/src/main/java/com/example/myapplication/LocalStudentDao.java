package com.example.myapplication;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface LocalStudentDao {
    @Insert
    void insert(LocalStudent s);

    @Update
    void update(LocalStudent s);

    @Delete
    void delete(LocalStudent s);

    // Everything (used by the migration test)
    @Query("SELECT * FROM local_students ORDER BY createdAt, operationId")
    List<LocalStudent> all();

    // Live list for ONE account - the screen redraws by itself whenever a row changes
    @Query("SELECT * FROM local_students WHERE ownerAccountId = :accountId ORDER BY createdAt, operationId")
    LiveData<List<LocalStudent>> watch(int accountId);

    // What the SyncWorker must send for this account, oldest first (order is preserved)
    @Query("SELECT * FROM local_students WHERE ownerAccountId = :accountId AND status IN ('SAVED','PENDING') ORDER BY createdAt, operationId")
    List<LocalStudent> pending(int accountId);

    // If the app died while sending, put those rows back in the queue (resending is safe: same operation ID)
    @Query("UPDATE local_students SET status = 'PENDING' WHERE ownerAccountId = :accountId AND status = 'SYNCING'")
    void resetStuckSyncing(int accountId);

    // Logout: remove only rows the server has confirmed. Unsent work and "Action required" rows are kept.
    @Query("DELETE FROM local_students WHERE status = 'SYNCED'")
    void deleteFinished();
}
