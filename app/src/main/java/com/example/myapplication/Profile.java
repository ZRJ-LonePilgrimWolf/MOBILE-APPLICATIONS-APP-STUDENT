package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Shows the profile of the student who is logged in RIGHT NOW.
 * Data comes from GET /api/student/me, which finds the student by the login token.
 */
public class Profile extends BaseActivity {

    private TextView tvAvatar, tvHeaderName, tvHeaderNumber, tvHeaderBadge;
    private TextView tvStudentNumber, tvProgramme, tvLabGroup, tvStatus;
    private TextView tvFullName, tvUsername, tvEmail;
    private TextView tvProfileMessage;
    private View profileContent;

    private Call<StudentDtos.Me> pending;   // the request in flight, so we can cancel it
    private boolean loadedOnce = false;     // true after the first successful load

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile);

        tvAvatar = findViewById(R.id.tvAvatar);
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvHeaderNumber = findViewById(R.id.tvHeaderNumber);
        tvHeaderBadge = findViewById(R.id.tvHeaderBadge);
        tvStudentNumber = findViewById(R.id.tvStudentNumber);
        tvProgramme = findViewById(R.id.tvProgramme);
        tvLabGroup = findViewById(R.id.tvLabGroup);
        tvStatus = findViewById(R.id.tvStatus);
        tvFullName = findViewById(R.id.tvFullName);
        tvUsername = findViewById(R.id.tvUsername);
        tvEmail = findViewById(R.id.tvEmail);
        tvProfileMessage = findViewById(R.id.tvProfileMessage);
        profileContent = findViewById(R.id.profileContent);

        // Buttons — same destinations as before
        findViewById(R.id.back).setOnClickListener(v -> finish());

        ImageView change = findViewById(R.id.change);
        change.setOnClickListener(v -> startActivity(new Intent(this, changegroup.class)));

        findViewById(R.id.information).setOnClickListener(v ->
                startActivity(new Intent(this, edit1.class)));

        findViewById(R.id.edit2).setOnClickListener(v ->
                startActivity(new Intent(this, edit2.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();   // reload every time, so changes show when you come back
    }

    @Override
    protected void onDestroy() {
        if (pending != null) pending.cancel();
        super.onDestroy();
    }

    private void loadProfile() {
        String bearer = Session.bearer();
        if (bearer == null) {
            showMessage("You are not logged in. Please log in again.", false);
            return;
        }

        // First load: show "Loading…". Reloads keep the old data on screen.
        if (!loadedOnce) {
            showMessage("Loading your profile…", false);
        }

        if (pending != null) pending.cancel();
        pending = ApiClient.getClient().create(ApiService.class).studentMe(bearer);
        pending.enqueue(new Callback<StudentDtos.Me>() {
            @Override
            public void onResponse(Call<StudentDtos.Me> call, Response<StudentDtos.Me> res) {
                if (isFinishing() || isDestroyed()) return;
                if (res.isSuccessful() && res.body() != null) {
                    fill(res.body());
                } else if (res.code() == 401 || res.code() == 403) {
                    showMessage("Your session has expired. Please log in again.", false);
                } else if (res.code() == 404) {
                    showMessage("No student record was found for this account.", false);
                } else {
                    showMessage("Could not load your profile (error " + res.code() + ").", true);
                }
            }

            @Override
            public void onFailure(Call<StudentDtos.Me> call, Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                showMessage("Cannot reach the server. Check that it is running, then reopen this page.", true);
            }
        });
    }

    /** Put the logged-in student's details on the screen. */
    private void fill(StudentDtos.Me me) {
        String name = orDash(me.student_name);
        String number = orDash(me.student_number);

        tvAvatar.setText(name.equals("—") ? "?" : name.substring(0, 1).toUpperCase());
        tvHeaderName.setText(name);
        tvHeaderNumber.setText(number);

        String status;
        if ("PENDING".equals(me.approval_status)) {
            status = "Awaiting approval";
        } else if ("REJECTED".equals(me.approval_status) || me.is_deleted == 1) {
            status = "Not active";
        } else {
            status = "Active";
        }
        tvHeaderBadge.setText(status.toUpperCase());
        tvHeaderBadge.setVisibility(View.VISIBLE);

        tvStudentNumber.setText(number);
        tvProgramme.setText(orDash(me.program_name));

        String group = orDash(me.group_name);
        if (me.max_members > 0 && me.group_name != null) {
            group += "  (" + me.group_members + "/" + me.max_members + " members)";
        }
        tvLabGroup.setText(group);
        tvStatus.setText(status);

        tvFullName.setText(name);
        tvUsername.setText(orDash(me.username));
        tvEmail.setText(orDash(me.email));

        loadedOnce = true;
        tvProfileMessage.setVisibility(View.GONE);
        profileContent.setVisibility(View.VISIBLE);
    }

    /** Show a message. Before the first load it replaces the cards; after that, old data stays visible. */
    private void showMessage(String text, boolean canRetry) {
        if (!loadedOnce) tvHeaderName.setText(text.startsWith("Loading") ? "Loading…" : "Profile unavailable");
        tvProfileMessage.setText(canRetry ? text + "\n\nTap here to try again." : text);
        tvProfileMessage.setVisibility(View.VISIBLE);
        tvProfileMessage.setOnClickListener(canRetry ? v -> loadProfile() : null);
        tvProfileMessage.setClickable(canRetry);
        if (!loadedOnce) profileContent.setVisibility(View.GONE);
    }

    private static String orDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s.trim();
    }
}