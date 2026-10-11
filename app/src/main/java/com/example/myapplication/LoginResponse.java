package com.example.myapplication;

import com.google.gson.annotations.SerializedName;

// Server answer: { message, account_id, username, role, token }
public class LoginResponse {
    private String message;
    private String token;
    private String role;
    private String username;
    @SerializedName("account_id")
    private int accountId;

    // Login worked if the server gave us a token.
    public boolean isSuccess() { return token != null && !token.isEmpty(); }
    public String getMessage() { return message; }
    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getUsername() { return username; }
    public int getAccountId() { return accountId; }
}
