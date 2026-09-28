package com.example.myapplication.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                StudentEntity.class,
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract StudentDao studentDao();


    private static volatile AppDatabase INSTANCE;

    // Migration from database version 1 to version 2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {

            database.execSQL(
                    "ALTER TABLE students ADD COLUMN version INTEGER NOT NULL DEFAULT 0"
            );

            database.execSQL(
                    "ALTER TABLE students ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0"
            );

            database.execSQL(
                    "ALTER TABLE students ADD COLUMN accountId TEXT"
            );
        }
    };

    public static AppDatabase getInstance(Context context) {

        if (INSTANCE == null) {

            synchronized (AppDatabase.class) {

                if (INSTANCE == null) {

                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "student_lab_database"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}