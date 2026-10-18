package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Forgot password in two steps:
 *  1) username -> the server emails a 6-digit code to the account's email
 *  2) code + new password -> password changed
 * The server never says whether an account exists, allows 5 wrong tries and expires codes after 15 minutes.
 */
public class resetpassword extends BaseActivity {
    private EditText etUser, etCode, etNew, etConfirm;
    private Button btnSend, btnReset;
    private TextView tvMsg;
    private View step2;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resetpassword);

        api = ApiClient.getClient().create(ApiService.class);
        etUser = findViewById(R.id.number);
        etCode = findViewById(R.id.etCode);
        etNew = findViewById(R.id.etNewPassword);
        etConfirm = findViewById(R.id.etConfirmPassword);
        btnSend = findViewById(R.id.btnSendCode);
        btnReset = findViewById(R.id.change);
        tvMsg = findViewById(R.id.tvResetMessage);
        step2 = findViewById(R.id.step2);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendCode());
        btnReset.setOnClickListener(v -> reset());

        if (savedInstanceState != null && savedInstanceState.getBoolean("step2")) {
            step2.setVisibility(View.VISIBLE);
            btnSend.setText("Send code again");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("step2", step2.getVisibility() == View.VISIBLE);
    }

    private boolean gone() { return isFinishing() || isDestroyed(); }
    private static String text(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    private void show(String m) {
        tvMsg.setText(m);
        tvMsg.setVisibility(View.VISIBLE);
    }

    private void busy(Button b, boolean on, String label) {
        b.setEnabled(!on);
        b.setText(label);
    }

    // ---------- step 1 ----------
    private void sendCode() {
        String u = text(etUser);
        if (u.isEmpty()) { etUser.setError("Enter your username"); return; }
        busy(btnSend, true, "Sending…");
        show("Sending… (the first request can take up to a minute while the server wakes up)");

        api.forgotPassword(new PasswordResetDtos.ForgotBody(u)).enqueue(new Callback<PasswordResetDtos.Message>() {
            @Override
            public void onResponse(Call<PasswordResetDtos.Message> c, Response<PasswordResetDtos.Message> r) {
                if (gone()) return;
                busy(btnSend, false, "Send code again");
                if (r.isSuccessful() && r.body() != null) {
                    show(r.body().message);
                    step2.setVisibility(View.VISIBLE);
                    etCode.requestFocus();
                } else {
                    StudentDtos.ServerError e = edit2.readError(r.errorBody());
                    if ("username".equals(e.field)) etUser.setError(e.message);
                    show(e.message != null ? e.message : "Could not send the code (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<PasswordResetDtos.Message> c, Throwable t) {
                if (gone()) return;
                busy(btnSend, false, "Send code");
                show("Cannot reach the server. Check your internet and try again.");
            }
        });
    }

    // ---------- step 2 ----------
    private void reset() {
        String u = text(etUser), code = text(etCode), pw = text(etNew), pw2 = text(etConfirm);
        if (u.isEmpty()) { etUser.setError("Enter your username"); return; }
        if (!code.matches("\\d{6}")) { etCode.setError("The code has exactly 6 digits"); return; }
        if (pw.length() < 6) { etNew.setError("At least 6 characters"); return; }
        if (!pw.equals(pw2)) { etConfirm.setError("The two passwords are different"); return; }

        busy(btnReset, true, "Saving…");
        api.resetPassword(new PasswordResetDtos.ResetBody(u, code, pw)).enqueue(new Callback<PasswordResetDtos.Message>() {
            @Override
            public void onResponse(Call<PasswordResetDtos.Message> c, Response<PasswordResetDtos.Message> r) {
                if (gone()) return;
                busy(btnReset, false, "Reset password");
                if (r.isSuccessful()) {
                    Toast.makeText(resetpassword.this, "Password changed. Log in with your new password.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                StudentDtos.ServerError e = edit2.readError(r.errorBody());
                if ("reset_code".equals(e.field)) etCode.setError(e.message);
                else if ("new_password".equals(e.field)) etNew.setError(e.message);
                show(e.message != null ? e.message : "Could not reset (error " + r.code() + ").");
            }

            @Override
            public void onFailure(Call<PasswordResetDtos.Message> c, Throwable t) {
                if (gone()) return;
                busy(btnReset, false, "Reset password");
                show("Cannot reach the server. Nothing was changed.");
            }
        });
    }
}