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
 * Group change: the logged-in student REQUESTS a move to another lab group.
 * Nothing changes until the lecturer approves; the server then re-checks the 15-student limit.
 */
public class changegroup extends BaseActivity {

    private TextView tvEditMessage, tvCurrentGroup, tvGroupRequestStatus;
    private Spinner spGroup;
    private Button btnSend, btnCancel;
    private TextInputLayout tilReason;
    private TextInputEditText etReason;

    private StudentDtos.Me me;
    private final List<ReferenceResponse.Group> choices = new ArrayList<>();   // every group except my own
    private final List<ReferenceResponse.Group> allGroups = new ArrayList<>();
    private StudentDtos.MyRequest pendingRequest;
    private int restoreGroupId = 0;

    private final List<Call<?>> calls = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_changegroup);

        tvEditMessage = findViewById(R.id.tvEditMessage);
        tvCurrentGroup = findViewById(R.id.tvCurrentGroup);
        tvGroupRequestStatus = findViewById(R.id.tvGroupRequestStatus);
        spGroup = findViewById(R.id.spGroup);
        btnSend = findViewById(R.id.change);
        btnCancel = findViewById(R.id.btnCancelGroupRequest);
        tilReason = findViewById(R.id.tilReason);
        etReason = findViewById(R.id.etReason);

        spGroup.setContentDescription("Lab group to move to");
        findViewById(R.id.back).setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> send());
        btnCancel.setOnClickListener(v -> confirmCancel());

        if (savedInstanceState != null) restoreGroupId = savedInstanceState.getInt("groupId", 0);
        load();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("groupId", selectedGroupId());
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

    private void load() {
        if (Session.bearer() == null) { showMessage("You are not logged in. Please log in again."); return; }
        showMessage("Loading…");

        track(api().getReference()).enqueue(new Callback<ReferenceResponse>() {
            @Override
            public void onResponse(Call<ReferenceResponse> c, Response<ReferenceResponse> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null && r.body().groups != null) {
                    allGroups.clear();
                    allGroups.addAll(r.body().groups);
                    fillGroups();
                } else {
                    showMessage("Could not load lab groups (error " + r.code() + ").");
                }
            }
            @Override
            public void onFailure(Call<ReferenceResponse> c, Throwable t) {
                if (!c.isCanceled() && !gone()) showMessage("Cannot reach the server. Reopen this page when online.");
            }
        });

        track(api().studentMe(Session.bearer())).enqueue(new Callback<StudentDtos.Me>() {
            @Override
            public void onResponse(Call<StudentDtos.Me> c, Response<StudentDtos.Me> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null) {
                    me = r.body();
                    tvCurrentGroup.setText("Current group: " + (me.group_name != null ? me.group_name : "Unassigned")
                            + (me.max_members > 0 ? "  (" + me.group_members + "/" + me.max_members + " students)" : ""));
                    fillGroups();
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

        loadRequests();
    }

    private void loadRequests() {
        track(api().myRequests(Session.bearer())).enqueue(new Callback<StudentDtos.MyRequestsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.MyRequestsResponse> c, Response<StudentDtos.MyRequestsResponse> r) {
                if (gone() || !r.isSuccessful() || r.body() == null || r.body().requests == null) return;
                StudentDtos.MyRequest latest = null;
                for (StudentDtos.MyRequest q : r.body().requests) {      // newest first
                    if ("GROUP_CHANGE".equals(q.type)) { latest = q; break; }
                }
                showRequest(latest);
            }
            @Override public void onFailure(Call<StudentDtos.MyRequestsResponse> c, Throwable t) { }
        });
    }

    /** Fill the dropdown once BOTH the group list and my profile are here. My own group is left out. */
    private void fillGroups() {
        if (allGroups.isEmpty() || me == null) return;
        choices.clear();
        List<String> labels = new ArrayList<>();
        int select = 0;
        for (ReferenceResponse.Group g : allGroups) {
            if (g.lab_group_id == me.lab_group_id) continue;
            boolean full = g.max_members > 0 && g.member_count >= g.max_members;
            if (g.lab_group_id == restoreGroupId) select = choices.size();
            choices.add(g);
            labels.add(g.group_name + "  (" + g.member_count + "/" + g.max_members + ")" + (full ? "  FULL" : ""));
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGroup.setAdapter(a);
        if (!choices.isEmpty()) spGroup.setSelection(select, false);
        restoreGroupId = 0;
        showMessage(choices.isEmpty() ? "There are no other lab groups to move to." : "");
        updateSendButton();
    }

    private int selectedGroupId() {
        int pos = spGroup.getSelectedItemPosition();
        return (pos >= 0 && pos < choices.size()) ? choices.get(pos).lab_group_id : 0;
    }

    private void updateSendButton() {
        btnSend.setEnabled(me != null && !choices.isEmpty() && pendingRequest == null);
    }

    // ================================================================== send / cancel

    private void send() {
        tilReason.setError(null);
        int groupId = selectedGroupId();
        String reason = etReason.getText() == null ? "" : etReason.getText().toString().trim();
        if (groupId == 0) { showMessage("Choose a lab group."); return; }
        if (reason.length() < 5) { tilReason.setError("Please give a short reason (at least 5 characters)"); return; }

        btnSend.setEnabled(false);
        btnSend.setText("Sending…");
        track(api().newRequest(Session.bearer(), StudentDtos.NewRequest.groupChange(groupId, reason)))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                        if (gone()) return;
                        btnSend.setText("Send request to lecturer");
                        if (r.isSuccessful()) {
                            etReason.setText("");
                            Toast.makeText(changegroup.this, "Request sent to your lecturer", Toast.LENGTH_SHORT).show();
                            loadRequests();
                            return;
                        }
                        updateSendButton();
                        StudentDtos.ServerError e = edit2.readError(r.errorBody());
                        if ("reason".equals(e.field)) tilReason.setError(e.message);
                        else showMessage(e.message != null ? e.message : "Could not send (error " + r.code() + ").");
                    }
                    @Override
                    public void onFailure(Call<ResponseBody> c, Throwable t) {
                        if (c.isCanceled() || gone()) return;
                        btnSend.setText("Send request to lecturer");
                        updateSendButton();
                        showMessage("Cannot reach the server. Your reason is kept - try again when online.");
                    }
                });
    }

    private void showRequest(StudentDtos.MyRequest q) {
        pendingRequest = (q != null && "PENDING".equals(q.status)) ? q : null;
        if (q == null) {
            tvGroupRequestStatus.setVisibility(View.GONE);
        } else {
            String g = q.requested_group_name != null ? q.requested_group_name : "?";
            String text;
            switch (q.status) {
                case "PENDING":  text = "⏳ Waiting for your lecturer: move to " + g; break;
                case "APPROVED": text = "✅ Approved: you were moved to " + g; break;
                case "REJECTED": text = "❌ Rejected: move to " + g
                        + (q.lecturer_note != null ? " (\"" + q.lecturer_note + "\")" : ""); break;
                default:         text = "Cancelled: move to " + g; break;
            }
            tvGroupRequestStatus.setText(text);
            tvGroupRequestStatus.setVisibility(View.VISIBLE);
        }
        btnCancel.setVisibility(pendingRequest != null ? View.VISIBLE : View.GONE);
        updateSendButton();
    }

    private void confirmCancel() {
        if (pendingRequest == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Cancel request?")
                .setMessage("Cancel your request to move to " + pendingRequest.requested_group_name + "?")
                .setNegativeButton("Keep it", null)
                .setPositiveButton("Cancel request", (d, w) -> cancelRequest())
                .show();
    }

    private void cancelRequest() {
        if (pendingRequest == null) return;
        btnCancel.setEnabled(false);
        track(api().cancelRequest(Session.bearer(), pendingRequest.request_id)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (gone()) return;
                btnCancel.setEnabled(true);
                if (!r.isSuccessful()) {
                    StudentDtos.ServerError e = edit2.readError(r.errorBody());
                    showMessage(e.message != null ? e.message : "Could not cancel (error " + r.code() + ").");
                }
                loadRequests();
            }
            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (c.isCanceled() || gone()) return;
                btnCancel.setEnabled(true);
                showMessage("Cannot reach the server. Nothing was changed.");
            }
        });
    }

    private void showMessage(String text) {
        tvEditMessage.setText(text);
        tvEditMessage.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
