package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer screen: students who registered themselves and are waiting for approval.
 * Approve / Reject are saved on the server (approvals.js); the card then disappears.
 */
public class ActionNeededActivity extends BaseActivity {

    private ListView lvPending;
    private TextView tvActionMessage, tvPendingCount;

    private final List<StudentDtos.Pending> pending = new ArrayList<>();
    private final List<StudentDtos.Group> groups = new ArrayList<>();
    private final Map<Integer, Integer> chosenGroup = new HashMap<>(); // studentId -> lab_group_id picked in the dropdown
    private final Set<Integer> busy = new HashSet<>();                 // studentIds with a request in flight

    private PendingAdapter adapter;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.action_needed);

        api = ApiClient.getClient().create(ApiService.class);
        lvPending = findViewById(R.id.lvPending);
        tvActionMessage = findViewById(R.id.tvActionMessage);
        tvPendingCount = findViewById(R.id.tvPendingCount);

        findViewById(R.id.back).setOnClickListener(v -> finish());

        adapter = new PendingAdapter();
        lvPending.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();   // always fresh: another lecturer may have handled some students
    }

    // ------------------------------------------------------------------ load the list

    private void load() {
        String bearer = Session.bearer();
        if (bearer == null) {
            showMessage("You are not logged in. Please log in again.", false);
            return;
        }
        if (pending.isEmpty()) showMessage("Loading…", false);

        api.approvals(bearer).enqueue(new Callback<StudentDtos.ApprovalsResponse>() {
            @Override
            public void onResponse(Call<StudentDtos.ApprovalsResponse> call,
                                   Response<StudentDtos.ApprovalsResponse> res) {
                if (isFinishing() || isDestroyed()) return;
                if (res.isSuccessful() && res.body() != null) {
                    pending.clear();
                    if (res.body().pending != null) pending.addAll(res.body().pending);
                    groups.clear();
                    if (res.body().groups != null) groups.addAll(res.body().groups);
                    busy.clear();
                    // Keep dropdown choices only for students still on the list
                    Set<Integer> ids = new HashSet<>();
                    for (StudentDtos.Pending p : pending) ids.add(p.student_id);
                    chosenGroup.keySet().retainAll(ids);
                    refresh();
                } else if (res.code() == 401 || res.code() == 403) {
                    showMessage("Your session has expired or this account is not a lecturer. Please log in again.", false);
                } else {
                    showMessage("Could not load pending students (error " + res.code() + ").", true);
                }
            }

            @Override
            public void onFailure(Call<StudentDtos.ApprovalsResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showMessage("Cannot reach the server. Check that it is running.", true);
            }
        });
    }

    /** Redraw the list, the count badge and the empty message. */
    private void refresh() {
        int n = pending.size();
        tvPendingCount.setText(n == 1 ? "1 item" : n + " items");
        if (n == 0) {
            showMessage("All done. No students are waiting for approval.", false);
        } else {
            tvActionMessage.setVisibility(View.GONE);
            lvPending.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private void showMessage(String text, boolean canRetry) {
        tvActionMessage.setText(canRetry ? text + "\n\nTap here to try again." : text);
        tvActionMessage.setOnClickListener(canRetry ? v -> load() : null);
        tvActionMessage.setClickable(canRetry);
        tvActionMessage.setVisibility(View.VISIBLE);
        if (pending.isEmpty()) lvPending.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ approve / reject

    private void approve(StudentDtos.Pending p) {
        Integer target = chosenGroup.get(p.student_id);
        // Only send a group if the lecturer changed it
        Integer move = (target != null && target != p.lab_group_id) ? target : null;

        busy.add(p.student_id);
        adapter.notifyDataSetChanged();

        api.approveStudent(Session.bearer(), p.student_id, new StudentDtos.ApproveBody(move))
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                        if (isFinishing() || isDestroyed()) return;
                        busy.remove(p.student_id);
                        if (res.isSuccessful()) {
                            if (move != null) {               // keep group counts right without reloading
                                changeCount(p.lab_group_id, -1);
                                changeCount(move, +1);
                            }
                            removeStudent(p);
                            toast(p.student_name + " approved");
                        } else {
                            handleError(p, res);
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        busy.remove(p.student_id);
                        adapter.notifyDataSetChanged();
                        toast("Cannot reach the server. Nothing was changed.");
                    }
                });
    }

    private void confirmReject(StudentDtos.Pending p) {
        new AlertDialog.Builder(this)
                .setTitle("Reject registration?")
                .setMessage("Reject " + p.student_name + " (" + p.student_number + ")?\n\n"
                        + "They will not be able to log in, and their lab group place is freed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reject", (d, w) -> reject(p))
                .show();
    }

    private void reject(StudentDtos.Pending p) {
        busy.add(p.student_id);
        adapter.notifyDataSetChanged();

        api.rejectStudent(Session.bearer(), p.student_id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (isFinishing() || isDestroyed()) return;
                busy.remove(p.student_id);
                if (res.isSuccessful()) {
                    changeCount(p.lab_group_id, -1);   // their place is free again
                    removeStudent(p);
                    toast(p.student_name + "'s registration rejected");
                } else {
                    handleError(p, res);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                busy.remove(p.student_id);
                adapter.notifyDataSetChanged();
                toast("Cannot reach the server. Nothing was changed.");
            }
        });
    }

    /** Server said no. Show its message; if someone else already handled the student, drop the card. */
    private void handleError(StudentDtos.Pending p, Response<ResponseBody> res) {
        ServerError err = readError(res);
        if (res.code() == 409 && "ALREADY_HANDLED".equals(err.code)) {
            removeStudent(p);
        } else if (res.code() == 409 && "GROUP_FULL".equals(err.code)) {
            load();   // refresh group sizes so the dropdown shows which groups are full
        } else {
            adapter.notifyDataSetChanged();
        }
        toast(err.message != null ? err.message : "Request failed (error " + res.code() + ").");
    }

    private void removeStudent(StudentDtos.Pending p) {
        pending.remove(p);
        chosenGroup.remove(p.student_id);
        refresh();
    }

    private void changeCount(int groupId, int delta) {
        for (StudentDtos.Group g : groups) {
            if (g.lab_group_id == groupId) { g.member_count += delta; return; }
        }
    }

    private static class ServerError { String code; String message; }

    private ServerError readError(Response<?> res) {
        try (ResponseBody body = res.errorBody()) {
            if (body != null) {
                ServerError e = new Gson().fromJson(body.string(), ServerError.class);
                if (e != null) return e;
            }
        } catch (Exception ignored) { }
        return new ServerError();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ list adapter

    private class PendingAdapter extends BaseAdapter {
        @Override public int getCount() { return pending.size(); }
        @Override public Object getItem(int i) { return pending.get(i); }
        @Override public long getItemId(int i) { return pending.get(i).student_id; }

        @Override
        public View getView(int position, View row, ViewGroup parent) {
            if (row == null) {
                row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_pending_student, parent, false);
            }
            StudentDtos.Pending p = pending.get(position);

            ((TextView) row.findViewById(R.id.tvPendingName)).setText(p.student_name);
            ((TextView) row.findViewById(R.id.tvPendingNumber)).setText(p.student_number);

            StringBuilder d = new StringBuilder();
            d.append("Programme: ").append(p.program_name != null ? p.program_name : "—");
            d.append("\nEmail: ").append(p.email != null ? p.email : "—");
            d.append("\nChose: ").append(p.group_name != null ? p.group_name : "—");
            if (p.created_at != null && p.created_at.length() >= 10) {
                d.append("\nRegistered: ").append(p.created_at.substring(0, 10));
            }
            ((TextView) row.findViewById(R.id.tvPendingDetails)).setText(d.toString());

            // Group dropdown: labels show how full each group is
            Spinner sp = row.findViewById(R.id.spPendingGroup);
            List<String> labels = new ArrayList<>();
            int selected = 0;
            for (int i = 0; i < groups.size(); i++) {
                StudentDtos.Group g = groups.get(i);
                boolean full = g.member_count >= g.max_members && g.lab_group_id != p.lab_group_id;
                labels.add(g.group_name + "  (" + g.member_count + "/" + g.max_members + ")" + (full ? " FULL" : ""));
                Integer want = chosenGroup.get(p.student_id);
                if (g.lab_group_id == (want != null ? want : p.lab_group_id)) selected = i;
            }
            ArrayAdapter<String> ga = new ArrayAdapter<>(ActionNeededActivity.this,
                    android.R.layout.simple_spinner_item, labels);
            ga.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sp.setOnItemSelectedListener(null);          // recycled row: detach the old student's listener first
            sp.setAdapter(ga);
            sp.setSelection(selected, false);
            sp.setContentDescription("Lab group for " + p.student_name);
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                    if (pos >= 0 && pos < groups.size()) chosenGroup.put(p.student_id, groups.get(pos).lab_group_id);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });

            boolean isBusy = busy.contains(p.student_id);
            Button approve = row.findViewById(R.id.btnApprove);
            Button reject = row.findViewById(R.id.btnReject);
            approve.setEnabled(!isBusy);
            reject.setEnabled(!isBusy);
            sp.setEnabled(!isBusy);
            approve.setText(isBusy ? "Saving…" : "Approve");
            approve.setContentDescription("Approve " + p.student_name);
            reject.setContentDescription("Reject " + p.student_name);
            approve.setOnClickListener(v -> approve(p));
            reject.setOnClickListener(v -> confirmReject(p));

            return row;
        }
    }
}