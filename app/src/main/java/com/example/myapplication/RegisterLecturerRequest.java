package com.example.myapplication;

// Body sent to POST /api/register/lecturer. Field names match the server exactly.
public class RegisterLecturerRequest {
    public String username;
    public String password;
    public String email;
    public String lecturer_code;

    public RegisterLecturerRequest(String username, String password, String email, String lecturerCode) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.lecturer_code = lecturerCode;
    }
}