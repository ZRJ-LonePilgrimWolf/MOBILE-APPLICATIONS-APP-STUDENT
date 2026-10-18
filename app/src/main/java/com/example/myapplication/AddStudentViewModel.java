package com.example.myapplication;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import java.util.List;
import java.util.UUID;

/**
 * Holds the Add-student screen's state. Survives rotation (the Activity is rebuilt, this object is not).
 * It does NOT survive process death - that is why every save goes to Room straight away.
 */
public class AddStudentViewModel extends ViewModel {
    // Same rules as the server (middleware.js nameRegex / studentNumberRegex)
    static final String NAME_RULE = "\\p{L}[\\p{L}\\p{M} .'’-]{1,99}";
    static final String NUMBER_RULE = "\\d{9}";

    private final StudentQueueRepository repo;
    private final int accountId;
    public final LiveData<List<LocalStudent>> queue;

    public AddStudentViewModel(StudentQueueRepository repo, int accountId) {
        this.repo = repo;
        this.accountId = accountId;
        this.queue = repo.queue(accountId);    // only THIS account's queue
    }

    /** Returns an error message for the user, or null if the student was saved. */
    public String save(String name, String number, String programText, String groupText) {
        if (accountId <= 0) return "Please log in again before adding students.";
        name = name == null ? "" : name.trim();
        number = number == null ? "" : number.trim();
        if (!name.matches(NAME_RULE)) return "Name must be 2-100 letters (spaces, hyphens and apostrophes allowed).";
        if (!number.matches(NUMBER_RULE)) return "Student number must be exactly 9 digits.";
        int p, g;
        try {
            p = Integer.parseInt(programText == null ? "" : programText.trim());
            g = Integer.parseInt(groupText == null ? "" : groupText.trim());
        } catch (NumberFormatException e) {
            return "Enter the programme and group numbers.";
        }
        if (p <= 0 || g <= 0) return "Enter the programme and group numbers.";

        LocalStudent s = new LocalStudent();
        s.operationId = UUID.randomUUID().toString();   // new receipt number for a NEW operation
        s.name = name;
        s.number = number;
        s.programId = p;
        s.groupId = g;
        s.ownerAccountId = accountId;
        s.createdAt = System.currentTimeMillis();
        s.status = LocalStudent.SAVED;
        s.message = "Saved on this phone";
        repo.save(s);
        return null;
    }

    public void syncNow() { repo.syncNow(); }

    public void discard(LocalStudent s) { repo.discard(s); }
}
