package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer screen: students' group-change and number-correction requests.
 * Approve/Reject are decided on the server (selfService.js) inside locked transactions;
 * a group change is refused with GROUP_FULL if the group already has 15 students.
 */
public class StudentRequestsActivity extends BaseActivity {

    private ListView lvRequests;
    private TextView tvRequestsMessage, tvRequestCount;
    private final List<StudentDtos.LecturerRequest> requests = new ArrayList<>();
    private final Set<Integer> busy = new HashSet<>();
    private int maxMembers = 15;
    private RequestAdapter adapter;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_requests);

        api = ApiClient.getClient().create(ApiService.class);
        lvRequests = findViewById(R.id.lvRequests);
        tvRequestsMessage = findViewById(R.id.tvRequestsMessage);
        tvRequestCount = findViewById(R.id.tvRequestCount);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        adapter = new RequestAdapter();
        lvRequests.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private boolean gone() { return isFinishing() || isDestroyed(); }

    // ------------------------------------------------------------------ load

    private void load() {
        if (Session.bearer() == null) { showMessage("You are not logged in. Please log in again.", false); return; }
        if (requests.isEmpty()) showMessage("Loading…", false);

        api.lecturerRequests(Session.bearer(), "PENDING").enqueue(new Callback<StudentDtos.LecturerRequestsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.LecturerRequestsResponse> c, Response<StudentDtos.LecturerRequestsResponse> r) {
                if (gone()) return;
                if (r.isSuccessful() && r.body() != null) {
                    requests.clear();
                    if (r.body().requests != null) requests.addAll(r.body().requests);
                    if (r.body().max_members > 0) maxMembers = r.body().max_members;
                    busy.clear();
                    refresh();
                } else if (r.code() == 401 || r.code() == 403) {
                    showMessage("Your session has expired or this account is not a lecturer. Please log in again.", false);
                } else {
                    showMessage("Could not load requests (error " + r.code() + ").", true);
                }
            }

            @Override
            public void onFailure(Call<StudentDtos.LecturerRequestsResponse> c, Throwable t) {
                if (gone()) return;
                showMessage("Cannot reach the server. Check your connection.", true);
            }
        });
    }

    private void refresh() {
        int n = requests.size();
        tvRequestCount.setText(n == 1 ? "1 item" : n + " items");
        if (n == 0) {
            showMessage("All done. No student requests are waiting.", false);
        } else {
            tvRequestsMessage.setVisibility(View.GONE);
            lvRequests.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void showMessage(String text, boolean canRetry) {
        tvRequestsMessage.setText(canRetry ? text + "\n\nTap here to try again." : text);
        tvRequestsMessage.setOnClickListener(canRetry ? v -> load() : null);
        tvRequestsMessage.setClickable(canRetry);
        tvRequestsMessage.setVisibility(View.VISIBLE);
        if (requests.isEmpty()) lvRequests.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ approve / reject

    private void confirmApprove(StudentDtos.LecturerRequest q) {
        String what = "GROUP_CHANGE".equals(q.type)
                ? "Move " + q.student_name + " from " + nz(q.current_group_name) + " to " + nz(q.requested_group_name) + "?"
                : "Change " + q.student_name + "'s student number from " + q.student_number + " to " + q.requested_number + "?";
        new AlertDialog.Builder(this)
                .setTitle("Approve request?")
                .setMessage(what)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Approve", (d, w) -> approve(q))
                .show();
    }

    private void approve(StudentDtos.LecturerRequest q) {
        busy.add(q.request_id);
        adapter.notifyDataSetChanged();
        api.approveRequest(Session.bearer(), q.request_id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (gone()) return;
                busy.remove(q.request_id);
                if (r.isSuccessful()) {
                    remove(q);
                    toast("Approved: " + q.student_name);
                } else {
                    handleError(q, r);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (gone()) return;
                busy.remove(q.request_id);
                adapter.notifyDataSetChanged();
                toast("Cannot reach the server. Nothing was changed.");
            }
        });
    }

    private void askRejectNote(StudentDtos.LecturerRequest q) {
        EditText note = new EditText(this);
        note.setHint("Reason for the student (optional)");
        note.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        note.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
                .setTitle("Reject request?")
                .setMessage("Reject " + q.student_name + "'s request?")
                .setView(note)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reject", (d, w) -> reject(q, note.getText().toString().trim()))
                .show();
    }

    private void reject(StudentDtos.LecturerRequest q, String note) {
        busy.add(q.request_id);
        adapter.notifyDataSetChanged();
        api.rejectRequest(Session.bearer(), q.request_id, new StudentDtos.RejectBody(note)).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (gone()) return;
                busy.remove(q.request_id);
                if (r.isSuccessful()) {
                    remove(q);
                    toast("Rejected: " + q.student_name);
                } else {
                    handleError(q, r);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (gone()) return;
                busy.remove(q.request_id);
                adapter.notifyDataSetChanged();
                toast("Cannot reach the server. Nothing was changed.");
            }
        });
    }

    private void handleError(StudentDtos.LecturerRequest q, Response<ResponseBody> r) {
        StudentDtos.ServerError e = edit2.readError(r.errorBody());
        if ("ALREADY_HANDLED".equals(e.code) || "NOT_FOUND".equals(e.code)) {
            remove(q);                       // someone else (or the student) already dealt with it
        } else if ("GROUP_FULL".equals(e.code)) {
            load();                          // refresh the counts; the request stays pending
        } else {
            adapter.notifyDataSetChanged();
        }
        toast(e.message != null ? e.message : "Request failed (error " + r.code() + ").");
    }

    private void remove(StudentDtos.LecturerRequest q) {
        requests.remove(q);
        refresh();
    }

    private void toast(String t) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); }

    private static String nz(String s) { return s != null ? s : "Unassigned"; }

    // ------------------------------------------------------------------ list

    private class RequestAdapter extends BaseAdapter {
        @Override public int getCount() { return requests.size(); }
        @Override public Object getItem(int i) { return requests.get(i); }
        @Override public long getItemId(int i) { return requests.get(i).request_id; }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            if (row == null) {
                row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_student_request, parent, false);
            }
            StudentDtos.LecturerRequest q = requests.get(position);
            boolean group = "GROUP_CHANGE".equals(q.type);

            ((TextView) row.findViewById(R.id.tvReqName)).setText(q.student_name);
            ((TextView) row.findViewById(R.id.tvReqType)).setText(group ? "GROUP CHANGE" : "NUMBER FIX");

            StringBuilder d = new StringBuilder();
            d.append("Student number: ").append(q.student_number);
            if (group) {
                boolean full = q.requested_group_members >= maxMembers;
                d.append("\nFrom: ").append(nz(q.current_group_name))
                        .append("  →  To: ").append(nz(q.requested_group_name))
                        .append(" (").append(q.requested_group_members).append("/").append(maxMembers)
                        .append(full ? " FULL" : "").append(")");
            } else {
                d.append("\nNew number: ").append(q.requested_number);
            }
            if (q.reason != null && !q.reason.isEmpty()) d.append("\nReason: ").append(q.reason);
            if (q.created_at != null && q.created_at.length() >= 10) d.append("\nRequested: ").append(q.created_at.substring(0, 10));
            ((TextView) row.findViewById(R.id.tvReqDetails)).setText(d.toString());

            boolean isBusy = busy.contains(q.request_id);
            Button approve = row.findViewById(R.id.btnReqApprove);
            Button reject = row.findViewById(R.id.btnReqReject);
            approve.setEnabled(!isBusy);
            reject.setEnabled(!isBusy);
            approve.setText(isBusy ? "Saving…" : "Approve");
            approve.setContentDescription("Approve request from " + q.student_name);
            reject.setContentDescription("Reject request from " + q.student_name);
            approve.setOnClickListener(v -> confirmApprove(q));
            reject.setOnClickListener(v -> askRejectNote(q));
            return row;
        }
    }
}
