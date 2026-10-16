package com.example.myapplication;

import java.util.List;

// JSON shapes for the student profile and the lecturer approvals screen (approvals.js on the server).
// Field names match the server exactly.
public class StudentDtos {

    // GET /api/student/me
    public static class Me {
        public int student_id;
        public String student_name;
        public String student_number;
        public String approval_status;   // "PENDING", "APPROVED" or "REJECTED"
        public int is_deleted;
        public int lab_group_id;
        public String username;
        public String email;
        public String program_name;
        public String group_name;
        public int group_members;
        public int max_members;
    }

    // One student waiting for approval
    public static class Pending {
        public int student_id;
        public String student_name;
        public String student_number;
        public int lab_group_id;
        public String created_at;
        public String email;
        public String program_name;
        public String group_name;
    }

    // A lab group with how full it is (for the "move to group" choice)
    public static class Group {
        public int lab_group_id;
        public String group_name;
        public int member_count;
        public int max_members;
    }

    // GET /api/approvals
    public static class ApprovalsResponse {
        public int count;
        public List<Pending> pending;
        public List<Group> groups;
    }

    // POST /api/approvals/:id/approve  body: { lab_group_id } (optional move)
    public static class ApproveBody {
        public Integer lab_group_id;   // null = keep their current group
        public ApproveBody(Integer labGroupId) { this.lab_group_id = labGroupId; }
    }
}