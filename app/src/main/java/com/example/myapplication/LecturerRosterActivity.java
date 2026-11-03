package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LecturerRosterActivity extends BaseActivity {
    private EditText etStudentSearch;
    private TextView tvStudentName, tvStudentNumber, tvStudentStatus, tvStudentProgram, tvStudentYear, tvStudentCourses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lecturer_roster);
        enableChatBubble();

        ImageView backButton = findViewById(R.id.back);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        AutoCompleteTextView roleAutoComplete = findViewById(R.id.rol);
        if (roleAutoComplete != null) {
            String[] roles = new String[]{"CHAT", "THEME", "SETTINGS", "MANAGE GROUPS", "LOGOUT", "DELETE ACCOUNT"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, roles);
            roleAutoComplete.setAdapter(adapter);

            roleAutoComplete.setDropDownBackgroundDrawable(new ColorDrawable(androidx.core.content.ContextCompat.getColor(this, R.color.card_bg_color)));
            roleAutoComplete.setDropDownWidth((int) (200 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownVerticalOffset((int) (20 * getResources().getDisplayMetrics().density));
            roleAutoComplete.setDropDownHeight((int) (300 * getResources().getDisplayMetrics().density));

            roleAutoComplete.setOnClickListener(v -> roleAutoComplete.showDropDown());
            roleAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    roleAutoComplete.post(() -> {
                        if (roleAutoComplete.getWindowToken() != null)
                            roleAutoComplete.showDropDown();
                    });
                }
            });

            roleAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
                String selectedItem = (String) parent.getItemAtPosition(position);

                // Prevent displaying the selected text inside the AutoCompleteTextView icon
                roleAutoComplete.post(() -> roleAutoComplete.setText("", false));

                if ("CHAT".equalsIgnoreCase(selectedItem)) {
                    startActivity(new Intent(LecturerRosterActivity.this, chatbot.class));
                } else if ("THEME".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(LecturerRosterActivity.this, ThemeActivity.class);
                    startActivity(intent);
                } else if ("SETTINGS".equalsIgnoreCase(selectedItem)) {
                    Intent intent = new Intent(LecturerRosterActivity.this, SettingsActivity.class);
                    startActivity(intent);
                } else if ("MANAGE GROUPS".equalsIgnoreCase(selectedItem)) {
                    startActivity(new Intent(LecturerRosterActivity.this, ManageLabActivity.class));
                } else if ("LOGOUT".equalsIgnoreCase(selectedItem)) {
                    logout();
                } else if ("DELETE ACCOUNT".equalsIgnoreCase(selectedItem)) {
                    confirmDeleteAccount();
                }
            });
        }

        // Day Schedule Buttons
        Button btnMon = findViewById(R.id.btnMon);
        Button btnTue = findViewById(R.id.btnTue);
        Button btnWed = findViewById(R.id.btnWed);
        Button btnThu = findViewById(R.id.btnThu);
        Button btnFri = findViewById(R.id.btnFri);

        if (btnMon != null) {
            btnMon.setOnClickListener(v -> Toast.makeText(this, "Monday Schedule Active", Toast.LENGTH_SHORT).show());
        }
        if (btnTue != null) {
            btnTue.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, teusday.class);
                startActivity(intent);
            });
        }
        if (btnWed != null) {
            btnWed.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, wednesday.class);
                startActivity(intent);
            });
        }
        if (btnThu != null) {
            btnThu.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, thursday.class);
                startActivity(intent);
            });
        }
        if (btnFri != null) {
            btnFri.setOnClickListener(v -> {
                Intent intent = new Intent(LecturerRosterActivity.this, friday.class);
                startActivity(intent);
            });
        }

        TextView tvActionNeededBadge = findViewById(R.id.tvActionNeededBadge);
        if (tvActionNeededBadge != null) {
            tvActionNeededBadge.setOnClickListener(v -> startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
        }
        Button btnGoToActionNeeded = findViewById(R.id.btnGoToActionNeeded);
        if (btnGoToActionNeeded != null) {
            btnGoToActionNeeded.setOnClickListener(v -> startActivity(new Intent(LecturerRosterActivity.this, ActionNeededActivity.class)));
        }

        Button btnOpenRoster = findViewById(R.id.btnOpenRoster);
        if (btnOpenRoster != null) {
            btnOpenRoster.setOnClickListener(v -> startActivity(new Intent(LecturerRosterActivity.this, RosterListActivity.class)));
        }

        etStudentSearch = findViewById(R.id.etStudentSearch);
        Button btnSearchStudent = findViewById(R.id.btnSearchStudent);
        tvStudentName = findViewById(R.id.tvStudentName);
        tvStudentNumber = findViewById(R.id.tvStudentNumber);
        tvStudentStatus = findViewById(R.id.tvStudentStatus);
        tvStudentProgram = findViewById(R.id.tvStudentProgram);
        tvStudentYear = findViewById(R.id.tvStudentYear);
        tvStudentCourses = findViewById(R.id.tvStudentCourses);
        if (btnSearchStudent != null && etStudentSearch != null) {
            btnSearchStudent.setOnClickListener(v -> performStudentSearch());
        }

        // "+ Add student" button floating at the bottom right (added in code, no XML change needed)
        Button add = new Button(this);
        add.setText("Student roster");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, 32, 32);
        ((ViewGroup) findViewById(android.R.id.content)).addView(add, lp);
        add.setOnClickListener(v -> startActivity(new Intent(this, RosterListActivity.class)));
    }

    // Opens the full roster with the search already typed in, so EVERY match is listed
    // (server-side search with paging), not just the first one.

    @Override
    protected void onResume() {
        super.onResume();
        loadActionCounts();   // refresh every time the lecturer comes back to this screen
        askNameIfMissing();
    }

    private boolean nameAsked = false;

    /** Lecturers created before names existed are asked once for their full name. */
    private void askNameIfMissing() {
        if (nameAsked || Session.bearer() == null) return;
        nameAsked = true;
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.lecturerMe(Session.bearer()).enqueue(new Callback<LecturerDtos.Me>() {
            @Override
            public void onResponse(Call<LecturerDtos.Me> c, Response<LecturerDtos.Me> r) {
                if (isFinishing() || isDestroyed() || !r.isSuccessful() || r.body() == null) return;
                if (r.body().full_name != null && r.body().full_name.trim().split("\\s+").length >= 2) return;

                EditText input = new EditText(LecturerRosterActivity.this);
                input.setHint("e.g. Mary Banda");
                int pad = (int) (20 * getResources().getDisplayMetrics().density);
                input.setPadding(pad, pad / 2, pad, pad / 2);
                AlertDialog d = new AlertDialog.Builder(LecturerRosterActivity.this)
                        .setTitle("Your full name")
                        .setMessage("Students and other lecturers will see this name instead of your username.")
                        .setView(input)
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Save", null)
                        .create();
                d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = input.getText().toString().trim().replaceAll("\\s+", " ");
                    if (!name.matches("\\p{L}[\\p{L}\\p{M} .'\u2019-]{1,99}")) { input.setError("Letters only, at least 2"); return; }
                    if (name.split(" ").length < 2) { input.setError("Enter at least two names, e.g. Mary Banda"); return; }
                    api.updateLecturerMe(Session.bearer(), new LecturerDtos.UpdateName(name)).enqueue(new Callback<okhttp3.ResponseBody>() {
                        @Override public void onResponse(Call<okhttp3.ResponseBody> c2, Response<okhttp3.ResponseBody> r2) {
                            Toast.makeText(LecturerRosterActivity.this, r2.isSuccessful() ? "Name saved" : "Could not save the name", Toast.LENGTH_SHORT).show();
                            if (r2.isSuccessful()) d.dismiss();
                        }
                        @Override public void onFailure(Call<okhttp3.ResponseBody> c2, Throwable t) {
                            Toast.makeText(LecturerRosterActivity.this, "No connection. Try again later.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }));
                d.show();
            }
            @Override public void onFailure(Call<LecturerDtos.Me> c, Throwable t) { }
        });
    }

    /** Real numbers from the server - no sample names. */
    private void loadActionCounts() {
        TextView summary = findViewById(R.id.tvActionSummary);
        TextView badge = findViewById(R.id.tvActionNeededBadge);
        if (summary == null || Session.bearer() == null) return;
        summary.setText("Loading…");

        ApiService api = ApiClient.getClient().create(ApiService.class);
        final int[] n = {-1, -1, -1};          // registrations, requests, unassigned
        final boolean[] failed = {false};

        Runnable show = () -> {
            if (isFinishing() || isDestroyed()) return;
            if (failed[0]) {
                summary.setText("Could not load the numbers. Check your connection, then reopen this screen.");
                if (badge != null) badge.setText("…");
                return;
            }
            if (n[0] < 0 || n[1] < 0 || n[2] < 0) return;   // wait for all three answers
            summary.setText("• Registrations waiting for approval: " + n[0]
                    + "\n• Group / number change requests: " + n[1]
                    + "\n• Students not in any lab group: " + n[2]);
            int action = n[0] + n[1];
            if (badge != null) badge.setText(action == 0 ? "All clear" : action + " Action Needed");
        };

        api.approvals(Session.bearer()).enqueue(new Callback<StudentDtos.ApprovalsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.ApprovalsResponse> c, Response<StudentDtos.ApprovalsResponse> r) {
                if (r.isSuccessful() && r.body() != null)
                    n[0] = r.body().pending != null ? r.body().pending.size() : r.body().count;
                else failed[0] = true;
                show.run();
            }
            @Override
            public void onFailure(Call<StudentDtos.ApprovalsResponse> c, Throwable t) { failed[0] = true; show.run(); }
        });

        api.lecturerRequests(Session.bearer(), "PENDING").enqueue(new Callback<StudentDtos.LecturerRequestsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.LecturerRequestsResponse> c, Response<StudentDtos.LecturerRequestsResponse> r) {
                if (r.isSuccessful() && r.body() != null)
                    n[1] = r.body().requests != null ? r.body().requests.size() : 0;
                else failed[0] = true;
                show.run();
            }
            @Override
            public void onFailure(Call<StudentDtos.LecturerRequestsResponse> c, Throwable t) { failed[0] = true; show.run(); }
        });

        api.groupTotals(Session.bearer()).enqueue(new Callback<RosterDtos.GroupTotals>() {
            @Override
            public void onResponse(Call<RosterDtos.GroupTotals> c, Response<RosterDtos.GroupTotals> r) {
                if (r.isSuccessful() && r.body() != null) n[2] = r.body().unassigned;
                else failed[0] = true;
                show.run();
            }
            @Override
            public void onFailure(Call<RosterDtos.GroupTotals> c, Throwable t) { failed[0] = true; show.run(); }
        });
    }
    private void performStudentSearch() {
        String q = etStudentSearch.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Please enter a student name or number", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, RosterListActivity.class);
        i.putExtra("search", q);
        startActivity(i);
    }

    }

