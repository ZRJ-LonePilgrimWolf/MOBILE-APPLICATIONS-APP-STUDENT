package com.example.myapplication;

import java.util.List;

// Answer of GET /api/reference  ->  { programs: [...], groups: [...] }
// Field names match the JSON keys exactly, so Gson fills them automatically.
public class ReferenceResponse {
    public List<Program> programs;
    public List<Group> groups;

    public static class Program {
        public int program_id;
        public String program_name;
    }

    public static class Group {
        public int lab_group_id;
        public String group_name;
        public int member_count;
        public int max_members;
    }
}