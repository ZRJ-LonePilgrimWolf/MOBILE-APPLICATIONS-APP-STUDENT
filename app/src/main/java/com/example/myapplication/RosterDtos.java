package com.example.myapplication;

import java.util.List;

// JSON shapes for the lecturer roster (studentsUpdateSafe.js on the server).
// Field names match the server exactly, so Gson fills them automatically.
public class RosterDtos {

    // One student row in the roster (GET /api/students, GET /api/students/:id)
    public static class Student {
        public int student_id;
        public int account_id;
        public String student_name;
        public String student_number;
        public int program_id;
        public Integer lab_group_id;       // null = Unassigned
        public int version;                // sent back as base_version when editing
        public int is_deleted;
        public String approval_status;
        public String email;
        public String program_name;
        public String group_name;          // null = Unassigned
    }

    // GET /api/students?search=&program_id=&lab_group_id=&page=&page_size=
    public static class Page {
        public int count;
        public int total;
        public int page;
        public int page_size;
        public boolean has_more;
        public List<Student> students;
    }

    // GET /api/labgroups  -> group totals for the lecturer
    public static class GroupTotals {
        public int max_members;
        public int unassigned;
        public List<StudentDtos.Group> groups;
    }

    // PUT /api/students/:id   (lab_group_id null = Unassigned; Gson leaves nulls out, the server reads that as Unassigned)
    public static class UpdateStudent {
        public String operation_id;        // same ID on a retry = the change happens only once
        public String student_name;
        public String student_number;
        public int program_id;
        public Integer lab_group_id;
        public int base_version;

        public UpdateStudent(String operationId, String name, String number, int programId, Integer groupId, int baseVersion) {
            this.operation_id = operationId;
            this.student_name = name;
            this.student_number = number;
            this.program_id = programId;
            this.lab_group_id = groupId;
            this.base_version = baseVersion;
        }
    }

    public static class UpdateResponse {
        public String message;
        public Student student;
    }

    // Error answers: { code, message, field?, current? }  (current = the server's record on CONFLICT)
    public static class Error {
        public String code;
        public String message;
        public String field;
        public Student current;
    }
}
