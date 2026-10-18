package com.example.myapplication;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;

/**
 * Lecturer adds a student. Screen -> ViewModel -> Repository -> Room + WorkManager -> API.
 * Saved on the phone FIRST, then synced. Each row shows: Saved locally / Pending / Syncing / Synced / Action required.
 */
public class AddStudentActivity extends BaseActivity {
    // Fixed IDs so Android restores typed text after rotation / process death
    private static final int ID_NAME = 1001, ID_NUMBER = 1002, ID_PROGRAM = 1003, ID_GROUP = 1004;

    private AddStudentViewModel vm;
    private EditText name, number, program, group;
    private LinearLayout queueBox;

    private EditText field(String hint, int id) {
        EditText e = new EditText(this);
        e.setId(id);
        e.setHint(hint);
        e.setContentDescription(hint);
        e.setMinHeight(dp(48));
        return e;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("Add student (works offline)");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        name = field("Student name (letters only)", ID_NAME);
        number = field("Student number (9 digits)", ID_NUMBER);
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        program = field("Programme: 1=CS 2=IT 3=DS", ID_PROGRAM);
        program.setInputType(InputType.TYPE_CLASS_NUMBER);
        group = field("Group: 1=G01 2=G02 3=G03 4=G04", ID_GROUP);
        group.setInputType(InputType.TYPE_CLASS_NUMBER);

        Button save = new Button(this);
        save.setText("Save (works offline)");
        save.setMinHeight(dp(48));
        Button sync = new Button(this);
        sync.setText("Sync now");
        sync.setMinHeight(dp(48));
        sync.setContentDescription("Send queued students to the server now");

        TextView queueTitle = new TextView(this);
        queueTitle.setText("\nYour queue (this account only)");
        queueTitle.setTypeface(Typeface.DEFAULT_BOLD);
        queueBox = new LinearLayout(this);
        queueBox.setOrientation(LinearLayout.VERTICAL);

        root.addView(title);
        root.addView(name);
        root.addView(number);
        root.addView(program);
        root.addView(group);
        root.addView(save);
        root.addView(sync);
        root.addView(queueTitle);
        root.addView(queueBox);

        // ViewModel built with the REAL repository and the logged-in account
        final int account = Session.accountId();
        final StudentQueueRepository repo = new RoomStudentQueueRepository(this);
        vm = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            @SuppressWarnings("unchecked")
            public <T extends ViewModel> T create(@NonNull Class<T> c) {
                return (T) new AddStudentViewModel(repo, account);
            }
        }).get(AddStudentViewModel.class);

        vm.queue.observe(this, this::render);   // redraws by itself when Room or the SyncWorker changes a row

        save.setOnClickListener(v -> {
            String err = vm.save(text(name), text(number), text(program), text(group));
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();   // form input is kept
                return;
            }
            name.setText("");
            number.setText("");
            Toast.makeText(this, "Saved on this phone. It will sync when online.", Toast.LENGTH_SHORT).show();
        });
        sync.setOnClickListener(v -> {
            vm.syncNow();
            Toast.makeText(this, "Sync started (waits for internet if offline)", Toast.LENGTH_SHORT).show();
        });

        vm.syncNow();   // try to send anything left from before
    }

    private static String text(EditText e) { return e.getText() == null ? "" : e.getText().toString(); }

    static String label(String status) {
        if (status == null) return "Saved locally";
        switch (status) {
            case LocalStudent.SAVED:    return "Saved locally";
            case LocalStudent.PENDING:  return "Pending";
            case LocalStudent.SYNCING:  return "Syncing…";
            case LocalStudent.SYNCED:   return "Synced ✓";
            case LocalStudent.REJECTED: return "Action required";
            default:                    return status;
        }
    }

    private void render(List<LocalStudent> items) {
        queueBox.removeAllViews();
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nothing queued.");
            queueBox.addView(empty);
            return;
        }
        for (LocalStudent s : items) {
            TextView row = new TextView(this);
            row.setText("[" + label(s.status) + "]  " + s.name + "  " + s.number
                    + "\n   " + (s.message != null ? s.message : ""));
            row.setPadding(0, dp(8), 0, dp(4));
            row.setContentDescription(label(s.status) + ": " + s.name + ", " + s.message);
            queueBox.addView(row);
            if (LocalStudent.REJECTED.equals(s.status)) {
                Button review = new Button(this);
                review.setText("Review");
                review.setMinHeight(dp(48));
                review.setContentDescription("Review refused student " + s.name);
                review.setOnClickListener(v -> review(s));
                queueBox.addView(review);
            }
        }
    }

    /** "Action required" screen: show what the server said and let the lecturer decide. Nothing is lost silently. */
    private void review(LocalStudent s) {
        new AlertDialog.Builder(this)
                .setTitle("Action required")
                .setMessage("The server refused this student:\n\n" + s.name + "  " + s.number
                        + "\nProgramme " + s.programId + ", group " + s.groupId
                        + "\n\nServer said: " + s.message
                        + "\n\nYour entry is still kept on this phone. Edit it and save again (it gets a NEW operation ID), or discard it.")
                .setPositiveButton("Edit & save again", (d, w) -> {
                    name.setText(s.name);
                    number.setText(s.number);
                    program.setText(String.valueOf(s.programId));
                    group.setText(String.valueOf(s.groupId));
                    vm.discard(s);
                    Toast.makeText(this, "Fix the details above, then tap Save.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Discard", (d, w) -> new AlertDialog.Builder(this)
                        .setMessage("Discard " + s.name + "? This cannot be undone.")
                        .setNegativeButton("Keep", null)
                        .setPositiveButton("Discard", (d2, w2) -> vm.discard(s))
                        .show())
                .setNeutralButton("Keep for now", null)
                .show();
    }
}
