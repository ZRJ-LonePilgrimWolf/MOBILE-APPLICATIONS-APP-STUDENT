package com.example.myapplication;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Rule: upgrading the phone database from version 1 to 2 must NOT delete existing or pending work. */
@RunWith(AndroidJUnit4.class)
public class RoomMigrationTest {
    private static final String NAME = "migration-test.db";

    @Test
    public void migrate1To2_keepsExistingAndPendingRows() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ctx.deleteDatabase(NAME);

        // 1. Build a VERSION 1 database exactly like the old app had, with one pending and one synced row
        SQLiteDatabase old = ctx.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        old.execSQL("CREATE TABLE IF NOT EXISTS `local_students` (`operationId` TEXT NOT NULL, `name` TEXT, "
                + "`number` TEXT, `programId` INTEGER NOT NULL, `groupId` INTEGER NOT NULL, `status` TEXT, "
                + "`message` TEXT, PRIMARY KEY(`operationId`))");
        old.execSQL("INSERT INTO local_students VALUES ('op-pending','Old Pending','202100001',1,1,'PENDING','Offline - will retry')");
        old.execSQL("INSERT INTO local_students VALUES ('op-synced','Old Synced','202100002',1,2,'SYNCED','Saved on server')");
        old.setVersion(1);
        old.close();

        // 2. Open it with the NEW Room database (version 2) and the migration
        AppDb db = Room.databaseBuilder(ctx, AppDb.class, NAME)
                .addMigrations(AppDb.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();
        List<LocalStudent> rows = db.dao().all();
        db.close();

        // 3. Both rows survived, the pending one is still PENDING, new columns got their defaults
        assertEquals(2, rows.size());
        LocalStudent pending = rows.get(0).operationId.equals("op-pending") ? rows.get(0) : rows.get(1);
        assertEquals("PENDING", pending.status);
        assertEquals("202100001", pending.number);
        assertEquals(0, pending.ownerAccountId);
        ctx.deleteDatabase(NAME);
    }
}