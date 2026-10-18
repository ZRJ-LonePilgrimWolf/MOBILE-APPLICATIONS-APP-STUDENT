package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.util.regex.Pattern;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Personal details: the logged-in student edits their OWN full name.
 * Saved on the server with PUT /api/student/me (version-checked, so a lecturer's newer change is never overwritten).
 */
public class edit2 extends BaseActivity {

    // Same rule as the server (middleware.js nameRegex): 2-100 chars, letters incl. accents, space . ' ’ -
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'’-]{1,99}$");

    private TextInputLayout tilFullName;
    private TextInputEditText etFullName;
    private TextView tvEditMessage, tvReadOnly;
    private Button btnSave;

    // What the server sent; kept across rotation so typed text is never replaced
    private boolean loaded = false;
    private int version, programId;
    private String readOnlyText = "";

    private Call<?> inFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit2);

        tilFullName = findViewById(R.id.tilFullName);
        etFullName = findViewById(R.id.etFullName);
        tvEditMessage = findViewById(R.id.tvEditMessage);
        tvReadOnly = findViewById(R.id.tvReadOnly);
        btnSave = findViewById(R.id.editing);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());

        // Clear the error as soon as the user starts fixing it
        etFullName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { tilFullName.setError(null); }
            @Override public void afterTextChanged(Editable s) { }
        });

        if (savedInstanceState != null && savedInstanceState.getBoolean("loaded")) {
            // Rotation: Android already restored the typed name; just restore what the server told us.
            loaded = true;
            version = savedInstanceState.getInt("version");
            programId = savedInstanceState.getInt("programId");
            readOnlyText = savedInstanceState.getString("readOnly", "");
            tvReadOnly.setText(readOnlyText);
            showMessage(savedInstanceState.getString("message", ""));
            btnSave.setEnabled(true);
        } else {
            load();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("loaded", loaded);
        out.putInt("version", version);
        out.putInt("programId", programId);
        out.putString("readOnly", readOnlyText);
        out.putString("message", tvEditMessage.getText().toString());
    }

    @Override
    protected void onDestroy() {
        if (inFlight != null) inFlight.cancel();
        super.onDestroy();
    }

    private ApiService api() { return ApiClient.getClient().create(ApiService.class); }

    // ------------------------------------------------------------------ load current details

    private void load() {
        if (Session.bearer() == null) { showMessage("You are not logged in. Please log in again."); return; }
        showMessage("Loading your details…");
        Call<StudentDtos.Me> call = api().studentMe(Session.bearer());
        inFlight = call;
        call.enqueue(new Callback<StudentDtos.Me>() {
            @Override
            public void onResponse(Call<StudentDtos.Me> c, Response<StudentDtos.Me> r) {
                if (isFinishing() || isDestroyed()) return;
                if (r.isSuccessful() && r.body() != null) {
                    StudentDtos.Me me = r.body();
                    loaded = true;
                    version = me.version;
                    programId = me.program_id;
                    etFullName.setText(me.student_name);
                    readOnlyText = "Student number: " + me.student_number
                            + "\nUsername: " + me.username
                            + "\nEmail: " + me.email;
                    tvReadOnly.setText(readOnlyText);
                    showMessage("");
                    btnSave.setEnabled(true);
                } else if (r.code() == 401 || r.code() == 403) {
                    showMessage("Your session has expired. Please log in again.");
                } else {
                    showMessage("Could not load your details (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<StudentDtos.Me> c, Throwable t) {
                if (c.isCanceled() || isFinishing() || isDestroyed()) return;
                showMessage("Cannot reach the server. Check your connection and reopen this page.");
            }
        });
    }

    // ------------------------------------------------------------------ save

    private void save() {
        String name = etFullName.getText() == null ? "" : etFullName.getText().toString().trim();
        if (!NAME.matcher(name).matches()) {
            tilFullName.setError("Enter 2–100 characters: letters, spaces, hyphens or apostrophes");
            etFullName.requestFocus();
            return;
        }
        setBusy(true);
        Call<StudentDtos.UpdateMeResponse> call =
                api().updateMe(Session.bearer(), new StudentDtos.UpdateMe(name, programId, version));
        inFlight = call;
        call.enqueue(new Callback<StudentDtos.UpdateMeResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.UpdateMeResponse> c, Response<StudentDtos.UpdateMeResponse> r) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (r.isSuccessful() && r.body() != null && r.body().student != null) {
                    version = r.body().student.version;
                    Toast.makeText(edit2.this, "Your name was saved", Toast.LENGTH_SHORT).show();
                    finish();   // Profile reloads itself in onResume
                    return;
                }
                StudentDtos.ServerError e = readError(r.errorBody());
                if (r.code() == 409 && "CONFLICT".equals(e.code) && e.current != null) {
                    // Someone else changed the record. Keep what the user typed, show the current value.
                    version = e.current.version;
                    programId = e.current.program_id;
                    showMessage("Your details were changed by someone else. Current name on record: \""
                            + e.current.student_name + "\". Tap Save again to keep your version.");
                } else if (r.code() == 400 && "student_name".equals(e.field)) {
                    tilFullName.setError(e.message);
                } else if (r.code() == 401 || r.code() == 403) {
                    showMessage(e.message != null ? e.message : "Your session has expired. Please log in again.");
                } else {
                    showMessage(e.message != null ? e.message : "Could not save (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<StudentDtos.UpdateMeResponse> c, Throwable t) {
                if (c.isCanceled() || isFinishing() || isDestroyed()) return;
                setBusy(false);
                showMessage("Cannot reach the server. Your typed name is kept - try Save again when online.");
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void setBusy(boolean busy) {
        btnSave.setEnabled(!busy && loaded);
        btnSave.setText(busy ? "Saving…" : "Save changes");
    }

    private void showMessage(String text) {
        tvEditMessage.setText(text);
        tvEditMessage.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    static StudentDtos.ServerError readError(ResponseBody body) {
        try (ResponseBody b = body) {
            if (b != null) {
                StudentDtos.ServerError e = new Gson().fromJson(b.string(), StudentDtos.ServerError.class);
                if (e != null) return e;
            }
        } catch (Exception ignored) { }
        return new StudentDtos.ServerError();
    }
}
