package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// A student saved ON THE PHONE (Room). operationId is the receipt number the server uses to ignore repeats.
@Entity(tableName = "local_students")
public class LocalStudent {
    @PrimaryKey
    @NonNull
    public String operationId = "";
    public String name;
    public String number;
    public int programId;
    public int groupId;
    public String status;   // PENDING, SYNCED or REJECTED
    public String message;
}
