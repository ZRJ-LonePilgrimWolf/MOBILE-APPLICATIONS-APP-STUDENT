package com.example.myapplication;

import androidx.room.Dao;
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

    @Query("SELECT * FROM local_students")
    List<LocalStudent> all();

    @Query("SELECT * FROM local_students WHERE status = 'PENDING'")
    List<LocalStudent> pending();

    // Logout: remove copies that are finished (synced or rejected). Unsent PENDING work is kept.
    @Query("DELETE FROM local_students WHERE status <> 'PENDING'")
    void deleteFinished();
}
