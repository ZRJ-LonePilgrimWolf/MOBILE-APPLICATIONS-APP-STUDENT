package com.example.myapplication;

// Bodies for POST /api/forgot-password and /api/reset-password
public class PasswordResetDtos {
    public static class ForgotBody {
        public String username;
        public ForgotBody(String username) { this.username = username; }
    }

    public static class ResetBody {
        public String username, reset_code, new_password;
        public ResetBody(String username, String code, String newPassword) {
            this.username = username; this.reset_code = code; this.new_password = newPassword;
        }
    }

    public static class Message {
        public String message;
    }
}