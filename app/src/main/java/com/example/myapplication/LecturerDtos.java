package com.example.myapplication;

// GET / PUT /api/lecturer/me
public class LecturerDtos {
    public static class Me {
        public int account_id;
        public String username;
        public String email;
        public String full_name;
    }

    public static class UpdateName {
        public String full_name;
        public UpdateName(String fullName) { this.full_name = fullName; }
    }
}