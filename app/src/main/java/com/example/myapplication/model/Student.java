package com.example.myapplication.model;

public class Student {
    private String studentId;
    private String studentNumber;
    private String studentName;
    private String programOfStudy;
    private String labGroup;

    public Student(String studentId, String studentNumber, String studentName, String programOfStudy, String labGroup) {
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.studentName = studentName;
        this.programOfStudy = programOfStudy;
        this.labGroup = labGroup;
    }
    public String getStudentId() {
        return studentId;
    }
    public String getStudentNumber(){
        return studentNumber;
    }
    public String getStudentName(){
        return studentName;
    }
    public String getProgramOfStudy(){
        return programOfStudy;
    }
    public String getLabGroup(){
        return labGroup;
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
}
