package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Academic details for the logged-in student:
 *  - change own programme (saved directly, version-checked: PUT /api/student/me)
 *  - request a student-number correction (the lecturer decides: POST /api/student/me/requests)
 */
public class edit1 extends BaseActivity {

    private TextView tvEditMessage, tvCurrentInfo, tvNumberRequestStatus;
    private Spinner spProgramme;
    private Button btnSaveProgramme, btnRequestNumber, btnCancelNumberRequest;
    private TextInputLayout tilNewNumber, tilReason;
    private TextInputEditText etNewNumber, etReason;

    // Loaded from the server
    private StudentDtos.Me me;
    private final List<ReferenceResponse.Program> programs = new ArrayList<>();
    private StudentDtos.MyRequest pendingNumberRequest;   // the open request, if any
    private int restoreProgramId = 0;                     // programme picked before rotation

    private final List<Call<?>> calls = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit);

        tvEditMessage = findViewById(R.id.tvEditMessage);
        tvCurrentInfo = findViewById(R.id.tvCurrentInfo);
        tvNumberRequestStatus = findViewById(R.id.tvNumberRequestStatus);
        spProgramme = findViewById(R.id.spProgramme);
        btnSaveProgramme = findViewById(R.id.editing);
        btnRequestNumber = findViewById(R.id.btnRequestNumber);
        btnCancelNumberRequest = findViewById(R.id.btnCancelNumberRequest);
        tilNewNumber = findViewById(R.id.tilNewNumber);
        tilReason = findViewById(R.id.tilReason);
        etNewNumber = findViewById(R.id.etNewNumber);
        etReason = findViewById(R.id.etReason);

        spProgramme.setContentDescription("Programme of study");
        findViewById(R.id.back).setOnClickListener(v -> finish());
        btnSaveProgramme.setOnClickListener(v -> saveProgramme());
        btnRequestNumber.setOnClickListener(v -> sendNumberRequest());
        btnCancelNumberRequest.setOnClickListener(v -> confirmCancel());

        if (savedInstanceState != null) restoreProgramId = savedInstanceState.getInt("programId", 0);
        // Typed number and reason are restored automatically by Android (the fields have IDs).
        loadAll();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("programId", selectedProgramId());
    }

    @Override
    protected void onDestroy() {
        for (Call<?> c : calls) c.cancel();
        super.onDestroy();
    }

    private ApiService api() { return ApiClient.getClient().create(ApiService.class); }

    private <T> Call<T> track(Call<T> c) { calls.add(c); return c; }

    private boolean gone() { return isFinishing() || isDestroyed(); }

    // ================================================================== loading

    private void loadAll() {
        if (Session.bearer() == null) { showMessage("You are not logged in. Please log in again."); return; }
        showMessage("Loading your details…");

        track(api().getReference()).enqueue(new Callback<ReferenceResponse>() {
            @Override
            public void onResponse(Call<ReferenceResponse> c, Response<ReferenceResponse> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null && r.body().programs != null) {
                    programs.clear();
                    programs.addAll(r.body().programs);
                    fillProgrammes();
                } else {
                    showMessage("Could not load programmes (error " + r.code() + ").");
                }
            }
            @Override
            public void onFailure(Call<ReferenceResponse> c, Throwable t) {
                if (!c.isCanceled() && !gone()) showMessage("Cannot reach the server. Reopen this page when online.");
            }
        });

        loadMe();
        loadRequests();
    }

    private void loadMe() {
        track(api().studentMe(Session.bearer())).enqueue(new Callback<StudentDtos.Me>() {
            @Override
            public void onResponse(Call<StudentDtos.Me> c, Response<StudentDtos.Me> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null) {
                    me = r.body();
                    tvCurrentInfo.setText("Student number: " + me.student_number
                            + "\nCurrent programme: " + (me.program_name != null ? me.program_name : "—"));
                    fillProgrammes();
                    btnRequestNumber.setEnabled(pendingNumberRequest == null);
                } else if (r.code() == 401 || r.code() == 403) {
                    showMessage("Your session has expired. Please log in again.");
                } else {
                    showMessage("Could not load your details (error " + r.code() + ").");
                }
            }
            @Override
            public void onFailure(Call<StudentDtos.Me> c, Throwable t) {
                if (!c.isCanceled() && !gone()) showMessage("Cannot reach the server. Reopen this page when online.");
            }
        });
    }

    private void loadRequests() {
        track(api().myRequests(Session.bearer())).enqueue(new Callback<StudentDtos.MyRequestsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.MyRequestsResponse> c, Response<StudentDtos.MyRequestsResponse> r) {
                if (gone() || !r.isSuccessful() || r.body() == null || r.body().requests == null) return;
                StudentDtos.MyRequest latest = null;
                for (StudentDtos.MyRequest q : r.body().requests) {     // newest first
                    if ("NUMBER_CORRECTION".equals(q.type)) { latest = q; break; }
                }
                showNumberRequest(latest);
            }
            @Override public void onFailure(Call<StudentDtos.MyRequestsResponse> c, Throwable t) { }
        });
    }

    /** Fill the spinner once BOTH the programme list and my profile have arrived. */
    private void fillProgrammes() {
        if (programs.isEmpty() || me == null) return;
        List<String> names = new ArrayList<>();
        int select = 0;
        int want = restoreProgramId != 0 ? restoreProgramId : me.program_id;
        for (int i = 0; i < programs.size(); i++) {
            names.add(programs.get(i).program_name);
            if (programs.get(i).program_id == want) select = i;
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProgramme.setAdapter(a);
        spProgramme.setSelection(select, false);
        restoreProgramId = 0;
        btnSaveProgramme.setEnabled(true);
        showMessage("");
    }

    private int selectedProgramId() {
        int pos = spProgramme.getSelectedItemPosition();
        return (pos >= 0 && pos < programs.size()) ? programs.get(pos).program_id : 0;
    }

    // ================================================================== save programme

    private void saveProgramme() {
        if (me == null) return;
        int programId = selectedProgramId();
        if (programId == 0) { showMessage("Choose a programme."); return; }
        if (programId == me.program_id) { showMessage("That is already your programme."); return; }

        btnSaveProgramme.setEnabled(false);
        btnSaveProgramme.setText("Saving…");
        track(api().updateMe(Session.bearer(), new StudentDtos.UpdateMe(me.student_name, programId, me.version)))
                .enqueue(new Callback<StudentDtos.UpdateMeResponse>() {
                    @Override
                    public void onResponse(Call<StudentDtos.UpdateMeResponse> c, Response<StudentDtos.UpdateMeResponse> r) {
                        if (gone()) return;
                        btnSaveProgramme.setEnabled(true);
                        btnSaveProgramme.setText("Save programme");
                        if (r.isSuccessful() && r.body() != null && r.body().student != null) {
                            Toast.makeText(edit1.this, "Programme saved", Toast.LENGTH_SHORT).show();
                            finish();   // Profile reloads itself
                            return;
                        }
                        StudentDtos.ServerError e = edit2.readError(r.errorBody());
                        if (r.code() == 409 && "CONFLICT".equals(e.code) && e.current != null) {
                            // Keep the user's choice; update to the latest record so a second tap can save it.
                            me.version = e.current.version;
                            me.student_name = e.current.student_name;
                            me.program_id = e.current.program_id;
                            showMessage("Your details were changed by someone else (programme on record: "
                                    + e.current.program_name + "). Tap Save again to keep your choice.");
                        } else {
                            showMessage(e.message != null ? e.message : "Could not save (error " + r.code() + ").");
                        }
                    }
                    @Override
                    public void onFailure(Call<StudentDtos.UpdateMeResponse> c, Throwable t) {
                        if (c.isCanceled() || gone()) return;
                        btnSaveProgramme.setEnabled(true);
                        btnSaveProgramme.setText("Save programme");
                        showMessage("Cannot reach the server. Your choice is kept - try again when online.");
                    }
                });
    }

    // ================================================================== number correction request

    private void sendNumberRequest() {
        tilNewNumber.setError(null);
        tilReason.setError(null);
        String number = etNewNumber.getText() == null ? "" : etNewNumber.getText().toString().trim();
        String reason = etReason.getText() == null ? "" : etReason.getText().toString().trim();

        boolean ok = true;
        if (!number.matches("\\d{9}")) { tilNewNumber.setError("Enter exactly 9 digits"); ok = false; }
        else if (me != null && number.equals(me.student_number)) { tilNewNumber.setError("That is already your number"); ok = false; }
        if (reason.length() < 5) { tilReason.setError("Please give a short reason (at least 5 characters)"); ok = false; }
        if (!ok) return;

        btnRequestNumber.setEnabled(false);
        btnRequestNumber.setText("Sending…");
        track(api().newRequest(Session.bearer(), StudentDtos.NewRequest.numberCorrection(number, reason)))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                        if (gone()) return;
                        btnRequestNumber.setText("Send request to lecturer");
                        if (r.isSuccessful()) {
                            etNewNumber.setText("");
                            etReason.setText("");
                            Toast.makeText(edit1.this, "Request sent to your lecturer", Toast.LENGTH_SHORT).show();
                            loadRequests();
                            return;
                        }
                        btnRequestNumber.setEnabled(true);
                        StudentDtos.ServerError e = edit2.readError(r.errorBody());
                        if ("new_number".equals(e.field) || "NUMBER_TAKEN".equals(e.code)) tilNewNumber.setError(e.message);
                        else if ("reason".equals(e.field)) tilReason.setError(e.message);
                        else showMessage(e.message != null ? e.message : "Could not send (error " + r.code() + ").");
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> c, Throwable t) {
                        if (c.isCanceled() || gone()) return;
                        btnRequestNumber.setEnabled(true);
                        btnRequestNumber.setText("Send request to lecturer");
                        showMessage("Cannot reach the server. Your typed number is kept - try again when online.");
                    }
                });
    }

    private void showNumberRequest(StudentDtos.MyRequest q) {
        pendingNumberRequest = (q != null && "PENDING".equals(q.status)) ? q : null;
        if (q == null) {
            tvNumberRequestStatus.setVisibility(View.GONE);
        } else {
            String text;
            switch (q.status) {
                case "PENDING":  text = "⏳ Waiting for your lecturer: change to " + q.requested_number; break;
                case "APPROVED": text = "✅ Approved: your number is now " + q.requested_number; break;
                case "REJECTED": text = "❌ Rejected: " + q.requested_number
                        + (q.lecturer_note != null ? " (\"" + q.lecturer_note + "\")" : ""); break;
                default:         text = "Cancelled: " + q.requested_number; break;
            }
            tvNumberRequestStatus.setText(text);
            tvNumberRequestStatus.setVisibility(View.VISIBLE);
        }
        btnCancelNumberRequest.setVisibility(pendingNumberRequest != null ? View.VISIBLE : View.GONE);
        // Only one open request at a time
        btnRequestNumber.setEnabled(me != null && pendingNumberRequest == null);
    }

    private void confirmCancel() {
        if (pendingNumberRequest == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Cancel request?")
                .setMessage("Cancel your request to change your number to " + pendingNumberRequest.requested_number + "?")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Cancel request", (d, w) -> cancelRequest())
                .show();
    }

    private void cancelRequest() {
        if (pendingNumberRequest == null) return;
        btnCancelNumberRequest.setEnabled(false);
        track(api().cancelRequest(Session.bearer(), pendingNumberRequest.request_id)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (gone()) return;
                btnCancelNumberRequest.setEnabled(true);
                if (!r.isSuccessful()) {
                    StudentDtos.ServerError e = edit2.readError(r.errorBody());
                    showMessage(e.message != null ? e.message : "Could not cancel (error " + r.code() + ").");
                }
                loadRequests();   // shows the real state either way
            }
            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (c.isCanceled() || gone()) return;
                btnCancelNumberRequest.setEnabled(true);
                showMessage("Cannot reach the server. Nothing was changed.");
            }
        });
    }

    private void showMessage(String text) {
        tvEditMessage.setText(text);
        tvEditMessage.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
