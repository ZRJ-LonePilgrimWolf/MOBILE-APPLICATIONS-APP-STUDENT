package com.example.myapplication;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {LocalStudent.class}, version = 2, exportSchema = false)
public abstract class AppDb extends RoomDatabase {
    public abstract LocalStudentDao dao();

    /** Version 1 -> 2: adds the owner account and the creation time. Existing rows (and pending work) are KEPT. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE local_students ADD COLUMN ownerAccountId INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE local_students ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static AppDb instance;

    // allowMainThreadQueries keeps the lab simple; screens use the repository's background thread for writes.
    public static synchronized AppDb get(Context c) {
        if (instance == null) {
            instance = Room.databaseBuilder(c.getApplicationContext(), AppDb.class, "lab.db")
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}
