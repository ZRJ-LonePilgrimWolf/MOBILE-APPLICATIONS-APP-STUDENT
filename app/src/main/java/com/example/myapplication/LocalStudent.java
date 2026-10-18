package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One queued "create student" operation, saved ON THE PHONE (Room) before it is sent.
 * operationId = the receipt number: the SAME id is reused on every retry, so the server applies it once.
 * ownerAccountId = the lecturer who queued it. Each account only sees and syncs its own queue.
 */
@Entity(tableName = "local_students")
public class LocalStudent {
    // Status values shown to the user (see AddStudentActivity.label)
    public static final String SAVED = "SAVED";       // Saved locally, not tried yet
    public static final String PENDING = "PENDING";   // Waiting to sync (offline / will retry)
    public static final String SYNCING = "SYNCING";   // Being sent right now
    public static final String SYNCED = "SYNCED";     // Confirmed by the server
    public static final String REJECTED = "REJECTED"; // Action required (e.g. GROUP_FULL, DUPLICATE)

    @PrimaryKey
    @NonNull
    public String operationId = "";
    public String name;
    public String number;
    public int programId;
    public int groupId;
    public String status;
    public String message;

    @ColumnInfo(defaultValue = "0")
    public int ownerAccountId;      // added in database version 2

    @ColumnInfo(defaultValue = "0")
    public long createdAt;          // added in version 2: keeps operations in the order they were made
}
