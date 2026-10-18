package com.example.myapplication;

import java.util.List;

// JSON shapes for the student profile, self-service requests and the lecturer screens.
// Field names match the server exactly (approvals.js and selfService.js), so Gson fills them automatically.
public class StudentDtos {

    // GET /api/student/me
    public static class Me {
        public int student_id;
        public String student_name;
        public String student_number;
        public String approval_status;   // "PENDING", "APPROVED" or "REJECTED"
        public int is_deleted;
        public int version;              // record version: sent back as base_version when editing
        public int program_id;
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

    // ===================== Student self-service (selfService.js) =====================

    // PUT /api/student/me  body: { student_name, program_id, base_version }
    public static class UpdateMe {
        public String student_name;
        public int program_id;
        public int base_version;   // the version the phone loaded; the server refuses if it changed since
        public UpdateMe(String name, int programId, int baseVersion) {
            this.student_name = name;
            this.program_id = programId;
            this.base_version = baseVersion;
        }
    }

    // Answer of PUT /api/student/me (and the "current" record sent back on CONFLICT)
    public static class Profile {
        public int student_id;
        public String student_name;
        public String student_number;
        public int program_id;
        public int lab_group_id;
        public int version;
        public String program_name;
        public String group_name;
    }

    public static class UpdateMeResponse {
        public String code;
        public String message;
        public Profile student;
    }

    // POST /api/student/me/requests
    //   group change:      { type:"GROUP_CHANGE", lab_group_id, reason }
    //   number correction: { type:"NUMBER_CORRECTION", new_number, reason }
    public static class NewRequest {
        public String type;
        public Integer lab_group_id;
        public String new_number;
        public String reason;

        public static NewRequest groupChange(int groupId, String reason) {
            NewRequest r = new NewRequest();
            r.type = "GROUP_CHANGE";
            r.lab_group_id = groupId;
            r.reason = reason;
            return r;
        }

        public static NewRequest numberCorrection(String newNumber, String reason) {
            NewRequest r = new NewRequest();
            r.type = "NUMBER_CORRECTION";
            r.new_number = newNumber;
            r.reason = reason;
            return r;
        }
    }

    // One of the logged-in student's own requests (GET /api/student/me/requests)
    public static class MyRequest {
        public int request_id;
        public String type;          // GROUP_CHANGE or NUMBER_CORRECTION
        public String status;        // PENDING, APPROVED, REJECTED or CANCELLED
        public String reason;
        public String lecturer_note;
        public String created_at;
        public String decided_at;
        public Integer requested_group_id;
        public String requested_group_name;
        public String requested_number;
    }

    public static class MyRequestsResponse {
        public List<MyRequest> requests;
    }

    // ===================== Lecturer: student requests =====================

    // One request as the lecturer sees it (GET /api/requests)
    public static class LecturerRequest {
        public int request_id;
        public String type;
        public String status;
        public String reason;
        public String created_at;
        public String requested_number;
        public Integer requested_group_id;
        public String requested_group_name;
        public int requested_group_members;
        public int student_id;
        public String student_name;
        public String student_number;
        public int lab_group_id;
        public String current_group_name;
    }

    public static class LecturerRequestsResponse {
        public int count;
        public int max_members;
        public List<LecturerRequest> requests;
    }

    // POST /api/requests/:id/reject  body: { note }
    public static class RejectBody {
        public String note;
        public RejectBody(String note) { this.note = note; }
    }

    // Any error answer from the server: { code, message, field? }
    public static class ServerError {
        public String code;
        public String message;
        public String field;
        public Profile current;   // only on CONFLICT
    }
}