package com.example.myapplication;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {LocalStudent.class}, version = 1, exportSchema = false)
public abstract class AppDb extends RoomDatabase {
    public abstract LocalStudentDao dao();

    private static AppDb instance;

    // allowMainThreadQueries keeps the lab simple. Say so in your README (a real app would use a background thread).
    public static synchronized AppDb get(Context c) {
        if (instance == null) {
            instance = Room.databaseBuilder(c.getApplicationContext(), AppDb.class, "lab.db")
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}
