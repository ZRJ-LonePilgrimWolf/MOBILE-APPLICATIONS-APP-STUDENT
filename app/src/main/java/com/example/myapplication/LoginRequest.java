package com.example.myapplication;

// The server (POST /api/login) expects exactly:  { "username": "...", "password": "..." }
public class LoginRequest {
    private String username;
    private String password;

    // Same 3-argument constructor as before so existing screens still compile.
    // The "role" is decided by the server from the account, so we do not send it.
    public LoginRequest(String number, String password, String role) {
        this.username = number;
        this.password = password;
    }

    public String getNumber() { return username; }
    public String getPassword() { return password; }
}
