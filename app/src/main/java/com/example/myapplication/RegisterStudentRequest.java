package com.example.myapplication;

// Body sent to POST /api/register/student. Field names match the server exactly.
public class RegisterStudentRequest {
    public String username;
    public String password;
    public String email;
    public String student_name;
    public String student_number;
    public int program_id;
    public int lab_group_id;

    public RegisterStudentRequest(String username, String password, String email,
                                  String studentName, String studentNumber,
                                  int programId, int labGroupId) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.student_name = studentName;
        this.student_number = studentNumber;
        this.program_id = programId;
        this.lab_group_id = labGroupId;
    }
}