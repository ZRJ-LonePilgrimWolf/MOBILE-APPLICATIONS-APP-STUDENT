package com.example.myapplication;

// Body sent to POST /api/students. Field names match the server exactly.
public class NewStudent {
    public String operation_id;
    public String student_name;
    public String student_number;
    public int program_id;
    public int lab_group_id;

    public NewStudent(String operationId, String name, String number, int programId, int groupId) {
        this.operation_id = operationId;
        this.student_name = name;
        this.student_number = number;
        this.program_id = programId;
        this.lab_group_id = groupId;
    }
}
