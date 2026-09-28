package com.example.myapplication.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "students")
public class StudentEntity {

    @PrimaryKey
    @NonNull
    private String studentId;

    private String studentNumber;
    private String studentName;
    private String programOfStudy;
    private String labGroup;

    // Used for conflict detection and synchronization
    private int version;

    // Used for soft deletion and synchronization
    private boolean deleted;

    // Identifies the account that owns this local record
    private String accountId;

    public StudentEntity(
            @NonNull String studentId,
            String studentNumber,
            String studentName,
            String programOfStudy,
            String labGroup,
            int version,
            boolean deleted,
            String accountId) {

        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.studentName = studentName;
        this.programOfStudy = programOfStudy;
        this.labGroup = labGroup;
        this.version = version;
        this.deleted = deleted;
        this.accountId = accountId;
    }

    @NonNull
    public String getStudentId() {
        return studentId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getProgramOfStudy() {
        return programOfStudy;
    }

    public String getLabGroup() {
        return labGroup;
    }

    public int getVersion() {
        return version;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public void setProgramOfStudy(String programOfStudy) {
        this.programOfStudy = programOfStudy;
    }

    public void setLabGroup(String labGroup) {
        this.labGroup = labGroup;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
}
