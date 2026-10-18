package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer: edit one student - name, number, programme - and assign, transfer or unassign their group.
 * Delete asks for confirmation (with Cancel). Server rules: 15-student limit, version check (no silent
 * overwrite), unique numbers, and an operation_id so a retried request is applied only once.
 */
public class StudentEditorActivity extends BaseActivity {

    public static final String EXTRA_STUDENT_ID = "student_id";
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'’-]{1,99}$");

    private int studentId;
    private TextView tvMessage, tvGroupError;
    private TextInputLayout tilName, tilNumber;
    private TextInputEditText etName, etNumber;
    private Spinner spProgramme, spGroup;
    private Button btnSave, btnDelete;

    private RosterDtos.Student student;                             // as loaded from the server
    private final List<ReferenceResponse.Program> programs = new ArrayList<>();
    private final List<StudentDtos.Group> groups = new ArrayList<>(); // plus "Unassigned" at the end
    private int maxMembers = 15;

    // The same operation ID is reused if the SAME save/delete is retried after a network failure,
    // so the server applies it only once. A new attempt gets a new ID.
    private String saveOpId, deleteOpId;
    private int restoreProgramme = -1, restoreGroup = -1;
    private boolean filled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_editor);

        studentId = getIntent().getIntExtra(EXTRA_STUDENT_ID, 0);
        tvMessage = findViewById(R.id.tvEditMessage);
        tvGroupError = findViewById(R.id.tvEdGroupError);
        tilName = findViewById(R.id.tilEdName);
        tilNumber = findViewById(R.id.tilEdNumber);
        etName = findViewById(R.id.etEdName);
        etNumber = findViewById(R.id.etEdNumber);
        spProgramme = findViewById(R.id.spEdProgramme);
        spGroup = findViewById(R.id.spEdGroup);
        btnSave = findViewById(R.id.btnEdSave);
        btnDelete = findViewById(R.id.btnEdDelete);
        spProgramme.setContentDescription("Programme");
        spGroup.setContentDescription("Lab group");

        findViewById(R.id.back).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
        btnDelete.setOnClickListener(v -> confirmDelete());

        if (savedInstanceState != null) {
            restoreProgramme = savedInstanceState.getInt("programme", -1);
            restoreGroup = savedInstanceState.getInt("group", -1);
            saveOpId = savedInstanceState.getString("saveOpId");
            deleteOpId = savedInstanceState.getString("deleteOpId");
            filled = savedInstanceState.getBoolean("filled", false);   // typed text is restored by Android
        }
        if (studentId == 0) { showMessage("No student selected."); return; }
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("programme", spProgramme.getSelectedItemPosition());
        out.putInt("group", spGroup.getSelectedItemPosition());
        out.putString("saveOpId", saveOpId);
        out.putString("deleteOpId", deleteOpId);
        out.putBoolean("filled", filled);
    }

    private ApiService api() { return ApiClient.getClient().create(ApiService.class); }
    private boolean gone() { return isFinishing() || isDestroyed(); }

    // ================================================================== load

    private void load() {
        showMessage("Loading…");
        api().getReference().enqueue(new Callback<ReferenceResponse>() {
            @Override
            public void onResponse(Call<ReferenceResponse> c, Response<ReferenceResponse> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null && r.body().programs != null) {
                    programs.clear();
                    programs.addAll(r.body().programs);
                    fillForm();
                } else showMessage("Could not load programmes (error " + r.code() + ").");
            }
            @Override public void onFailure(Call<ReferenceResponse> c, Throwable t) { if (!gone()) showMessage("Cannot reach the server."); }
        });
        api().groupTotals(Session.bearer()).enqueue(new Callback<RosterDtos.GroupTotals>() {
            @Override
            public void onResponse(Call<RosterDtos.GroupTotals> c, Response<RosterDtos.GroupTotals> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null && r.body().groups != null) {
                    groups.clear();
                    groups.addAll(r.body().groups);
                    if (r.body().max_members > 0) maxMembers = r.body().max_members;
                    fillForm();
                } else showMessage("Could not load lab groups (error " + r.code() + ").");
            }
            @Override public void onFailure(Call<RosterDtos.GroupTotals> c, Throwable t) { if (!gone()) showMessage("Cannot reach the server."); }
        });
        api().rosterStudent(Session.bearer(), studentId).enqueue(new Callback<RosterDtos.Student>() {
            @Override
            public void onResponse(Call<RosterDtos.Student> c, Response<RosterDtos.Student> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null) {
                    student = r.body();
                    fillForm();
                } else if (r.code() == 404) {
                    showMessage("This student no longer exists (it may have been deleted).");
                } else if (r.code() == 401 || r.code() == 403) {
                    showMessage("Your session has expired. Please log in again.");
                } else showMessage("Could not load the student (error " + r.code() + ").");
            }
            @Override public void onFailure(Call<RosterDtos.Student> c, Throwable t) { if (!gone()) showMessage("Cannot reach the server."); }
        });
    }

    /** Runs when each of the 3 answers arrives; fills the form once all are here. */
    private void fillForm() {
        if (student == null || programs.isEmpty() || groups.isEmpty()) return;

        if (!filled) {   // first time only: after rotation Android has kept what the lecturer typed
            etName.setText(student.student_name);
            etNumber.setText(student.student_number);
        }
        List<String> pLabels = new ArrayList<>();
        int pSel = 0;
        for (int i = 0; i < programs.size(); i++) {
            pLabels.add(programs.get(i).program_name);
            if (programs.get(i).program_id == student.program_id) pSel = i;
        }
        setUp(spProgramme, pLabels, restoreProgramme >= 0 ? restoreProgramme : pSel);

        List<String> gLabels = new ArrayList<>();
        int gSel = groups.size();                       // default: "Unassigned"
        for (int i = 0; i < groups.size(); i++) {
            StudentDtos.Group g = groups.get(i);
            boolean mine = student.lab_group_id != null && g.lab_group_id == student.lab_group_id;
            boolean full = !mine && g.member_count >= maxMembers;
            gLabels.add(g.group_name + "  (" + g.member_count + "/" + maxMembers + ")" + (full ? "  FULL" : "") + (mine ? "  - current" : ""));
            if (mine) gSel = i;
        }
        gLabels.add("Unassigned (no group)");
        setUp(spGroup, gLabels, restoreGroup >= 0 ? restoreGroup : gSel);

        restoreProgramme = restoreGroup = -1;
        filled = true;
        btnSave.setEnabled(true);
        btnDelete.setEnabled(true);
        showMessage("PENDING".equals(student.approval_status) ? "Note: this student is still awaiting approval." : "");
    }

    private void setUp(Spinner sp, List<String> labels, int select) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(Math.min(Math.max(select, 0), labels.size() - 1), false);
    }

    private Integer selectedGroupId() {
        int pos = spGroup.getSelectedItemPosition();
        return (pos >= 0 && pos < groups.size()) ? groups.get(pos).lab_group_id : null;   // last item = Unassigned
    }

    // ================================================================== save

    private void save() {
        if (student == null) return;
        tilName.setError(null);
        tilNumber.setError(null);
        tvGroupError.setVisibility(View.GONE);

        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        String number = etNumber.getText() == null ? "" : etNumber.getText().toString().trim();
        boolean ok = true;
        if (!NAME.matcher(name).matches()) { tilName.setError("Enter 2–100 characters: letters, spaces, hyphens or apostrophes"); ok = false; }
        if (!number.matches("\\d{9}")) { tilNumber.setError("Enter exactly 9 digits"); ok = false; }
        int pPos = spProgramme.getSelectedItemPosition();
        if (pPos < 0 || pPos >= programs.size()) { showMessage("Choose a programme."); ok = false; }
        if (!ok) return;

        if (saveOpId == null) saveOpId = UUID.randomUUID().toString();
        RosterDtos.UpdateStudent body = new RosterDtos.UpdateStudent(saveOpId, name, number,
                programs.get(pPos).program_id, selectedGroupId(), student.version);

        setBusy(true, "Saving…");
        api().updateStudent(Session.bearer(), studentId, body).enqueue(new Callback<RosterDtos.UpdateResponse>() {
            @Override
            public void onResponse(Call<RosterDtos.UpdateResponse> c, Response<RosterDtos.UpdateResponse> r) {
                if (gone()) return;
                setBusy(false, null);
                saveOpId = null;   // the server answered: the next save is a new operation
                if (r.isSuccessful()) {
                    Toast.makeText(StudentEditorActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    finish();      // the roster refreshes itself
                    return;
                }
                RosterDtos.Error e = readError(r.errorBody());
                if (r.code() == 409 && "CONFLICT".equals(e.code) && e.current != null) {
                    // Keep the lecturer's typed changes; adopt the new version so a second Save is deliberate.
                    student.version = e.current.version;
                    showMessage("Someone else changed this student. On record now: " + e.current.student_name
                            + ", " + e.current.student_number + ", " + (e.current.group_name != null ? e.current.group_name : "Unassigned")
                            + ". Your changes are kept - tap Save again to replace them, or Back to cancel.");
                } else if ("GROUP_FULL".equals(e.code) || "lab_group_id".equals(e.field)) {
                    tvGroupError.setText(e.message);
                    tvGroupError.setVisibility(View.VISIBLE);
                } else if ("student_number".equals(e.field) || "DUPLICATE".equals(e.code)) {
                    tilNumber.setError(e.message);
                } else if ("student_name".equals(e.field)) {
                    tilName.setError(e.message);
                } else if (r.code() == 410) {
                    showMessage("This student was deleted by someone else. Nothing was saved.");
                    btnSave.setEnabled(false);
                    btnDelete.setEnabled(false);
                } else {
                    showMessage(e.message != null ? e.message : "Could not save (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<RosterDtos.UpdateResponse> c, Throwable t) {
                if (gone()) return;
                setBusy(false, null);
                // Keep saveOpId: tapping Save again re-sends the SAME operation, so it can't be applied twice.
                showMessage("Cannot reach the server. Your changes are kept - tap Save to retry.");
            }
        });
    }

    // ================================================================== delete

    private void confirmDelete() {
        if (student == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete student?")
                .setMessage("Delete " + student.student_name + " (" + student.student_number + ")?\n\n"
                        + "Their account is disabled, their lab-group place is released and their number stays reserved.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> delete())
                .show();
    }

    private void delete() {
        if (deleteOpId == null) deleteOpId = UUID.randomUUID().toString();
        setBusy(true, null);
        btnDelete.setText("Deleting…");
        api().deleteStudent(Session.bearer(), studentId, deleteOpId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (gone()) return;
                setBusy(false, null);
                btnDelete.setText("Delete student");
                deleteOpId = null;
                if (r.isSuccessful()) {
                    Toast.makeText(StudentEditorActivity.this, student.student_name + " deleted", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    RosterDtos.Error e = readError(r.errorBody());
                    showMessage(e.message != null ? e.message : "Could not delete (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (gone()) return;
                setBusy(false, null);
                btnDelete.setText("Delete student");
                showMessage("Cannot reach the server. Nothing was deleted - try again when online.");
            }
        });
    }

    // ================================================================== helpers

    private void setBusy(boolean busy, String saveLabel) {
        btnSave.setEnabled(!busy);
        btnDelete.setEnabled(!busy);
        btnSave.setText(busy && saveLabel != null ? saveLabel : "Save changes");
    }

    private void showMessage(String text) {
        tvMessage.setText(text);
        tvMessage.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static RosterDtos.Error readError(ResponseBody body) {
        try (ResponseBody b = body) {
            if (b != null) {
                RosterDtos.Error e = new Gson().fromJson(b.string(), RosterDtos.Error.class);
                if (e != null) return e;
            }
        } catch (Exception ignored) { }
        return new RosterDtos.Error();
    }
}
