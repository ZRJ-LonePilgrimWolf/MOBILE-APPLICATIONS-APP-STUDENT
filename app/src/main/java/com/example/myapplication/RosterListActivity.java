package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer roster: search by name or number, filter by programme and lab group (incl. Unassigned),
 * see group totals, and scroll a paged list (20 at a time). Tap a student to edit, transfer or delete.
 *
 * Old search answers are ignored: every new search gets a new "generation" number, and an answer
 * that belongs to an older generation is thrown away, so a slow old reply can never overwrite a newer one.
 */
public class RosterListActivity extends BaseActivity {

    private static final int PAGE_SIZE = 20;
    private static final String UNASSIGNED = "unassigned";

    private TextInputEditText etSearch;
    private Spinner spProgramme, spGroup;
    private TextView tvGroupTotals, tvStatus;
    private RosterDtos.GroupTotals lastTotals;   // kept for the Sharesheet summary
    private RosterAdapter adapter;

    // Filter choices (index 0 = "All")
    private final List<Integer> programmeIds = new ArrayList<>();   // null = all
    private final List<String> groupKeys = new ArrayList<>();       // null = all, "unassigned", or an ID

    // Paging state
    private final List<RosterDtos.Student> rows = new ArrayList<>();
    private int page = 0;
    private int total = 0;
    private boolean hasMore = false, loading = false;
    private int generation = 0;          // bumps on every new search
    private Call<RosterDtos.Page> pageCall;

    // Selections to restore after rotation
    private int restoreProgramme = 0, restoreGroup = 0;
    private boolean spinnersReady = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable searchLater = this::startNewSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roster_list);

        etSearch = findViewById(R.id.etSearch);
        spProgramme = findViewById(R.id.spFilterProgramme);
        spGroup = findViewById(R.id.spFilterGroup);
        tvGroupTotals = findViewById(R.id.tvGroupTotals);
        tvStatus = findViewById(R.id.tvRosterStatus);
        spProgramme.setContentDescription("Filter by programme");
        spGroup.setContentDescription("Filter by lab group");

        findViewById(R.id.back).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvRoster);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        adapter = new RosterAdapter();
        rv.setAdapter(adapter);
        // Load the next page when the user is 5 rows from the end
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (dy > 0 && hasMore && !loading && lm.findLastVisibleItemPosition() >= rows.size() - 5) {
                    loadPage(page + 1, generation);
                }
            }
        });

        // Search waits 350 ms after the last key press (no request per letter)
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchLater);
                handler.postDelayed(searchLater, 350);
            }
        });

        // "+ Add student" button at the bottom right
        MaterialButton add = new MaterialButton(this);
        add.setText("+ Add student");
        add.setMinHeight((int) (56 * getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        int m = (int) (16 * getResources().getDisplayMetrics().density);
        lp.setMargins(0, 0, m, m);
        ((ViewGroup) findViewById(android.R.id.content)).addView(add, lp);
        add.setOnClickListener(v -> startActivity(new Intent(this, AddStudentActivity.class)));

        // "Share summary" button at the bottom left: group labels and counts only - no names or numbers
        MaterialButton share = new MaterialButton(this);
        share.setText("Share summary");
        share.setContentDescription("Share lab group summary");
        share.setMinHeight((int) (56 * getResources().getDisplayMetrics().density));
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp2.setMargins(m, 0, 0, m);
        ((ViewGroup) findViewById(android.R.id.content)).addView(share, lp2);
        share.setOnClickListener(v -> shareSummary());

        // Opened from the lecturer home search: start with those words in the box
        String fromHome = getIntent().getStringExtra("search");
        if (savedInstanceState == null && fromHome != null) etSearch.setText(fromHome);

        if (savedInstanceState != null) {
            restoreProgramme = savedInstanceState.getInt("programme", 0);
            restoreGroup = savedInstanceState.getInt("group", 0);
        }
        loadFilters();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (spinnersReady) {   // coming back from the editor: refresh the list and the totals
            loadTotals();
            startNewSearch();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("programme", spProgramme.getSelectedItemPosition());
        out.putInt("group", spGroup.getSelectedItemPosition());
        // The search text is saved by Android automatically (the field has an ID).
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(searchLater);
        if (pageCall != null) pageCall.cancel();
        super.onDestroy();
    }

    private ApiService api() { return ApiClient.getClient().create(ApiService.class); }
    private boolean gone() { return isFinishing() || isDestroyed(); }

    // ================================================================== filters + totals

    private void loadFilters() {
        if (Session.bearer() == null) { tvStatus.setText("You are not logged in. Please log in again."); return; }
        tvStatus.setText("Loading…");
        api().getReference().enqueue(new Callback<ReferenceResponse>() {
            @Override
            public void onResponse(Call<ReferenceResponse> c, Response<ReferenceResponse> r) {
                if (gone()) return;
                if (!r.isSuccessful() || r.body() == null) {
                    tvStatus.setText("Could not load filters (error " + r.code() + ").");
                    return;
                }
                List<String> pLabels = new ArrayList<>();
                programmeIds.clear();
                pLabels.add("All programmes");
                programmeIds.add(null);
                if (r.body().programs != null) for (ReferenceResponse.Program p : r.body().programs) {
                    pLabels.add(p.program_name);
                    programmeIds.add(p.program_id);
                }
                List<String> gLabels = new ArrayList<>();
                groupKeys.clear();
                gLabels.add("All groups");
                groupKeys.add(null);
                if (r.body().groups != null) for (ReferenceResponse.Group g : r.body().groups) {
                    gLabels.add(g.group_name);
                    groupKeys.add(String.valueOf(g.lab_group_id));
                }
                gLabels.add("Unassigned");
                groupKeys.add(UNASSIGNED);

                setUp(spProgramme, pLabels, restoreProgramme);
                setUp(spGroup, gLabels, restoreGroup);
                spinnersReady = true;
                loadTotals();
                startNewSearch();
            }

            @Override
            public void onFailure(Call<ReferenceResponse> c, Throwable t) {
                if (gone()) return;
                tvStatus.setText("Cannot reach the server. Tap here to try again.");
                tvStatus.setOnClickListener(v -> { tvStatus.setOnClickListener(null); loadFilters(); });
            }
        });
    }

    private void setUp(Spinner sp, List<String> labels, int select) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        sp.setSelection(select < labels.size() ? select : 0, false);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (spinnersReady) startNewSearch(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
    }


    /** Android Sharesheet: shares ONLY group labels and counts (no personal data). Cancelling the sheet does nothing. */
    private void shareSummary() {
        if (lastTotals == null || lastTotals.groups == null) {
            Toast.makeText(this, "Group totals are still loading. Try again in a moment.", Toast.LENGTH_SHORT).show();
            loadTotals();
            return;
        }
        StringBuilder sb = new StringBuilder("Nexa - lab group summary\n\n");
        for (StudentDtos.Group g : lastTotals.groups) {
            sb.append(g.group_name).append(": ").append(g.member_count).append(" / ").append(g.max_members);
            if (g.member_count >= g.max_members) sb.append(" (full)");
            sb.append("\n");
        }
        sb.append("Unassigned: ").append(lastTotals.unassigned);

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TITLE, "Lab group summary");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "Share group summary"));
    }
    private void loadTotals() {
        api().groupTotals(Session.bearer()).enqueue(new Callback<RosterDtos.GroupTotals>() {
            @Override
            public void onResponse(Call<RosterDtos.GroupTotals> c, Response<RosterDtos.GroupTotals> r) {
                if (gone() || !r.isSuccessful() || r.body() == null || r.body().groups == null) return;
                lastTotals = r.body();
                StringBuilder sb = new StringBuilder("Group totals:  ");
                for (StudentDtos.Group g : r.body().groups) {
                    sb.append(g.group_name).append(" ").append(g.member_count).append("/").append(g.max_members);
                    if (g.member_count >= g.max_members) sb.append(" FULL");
                    sb.append("  ·  ");
                }
                sb.append("Unassigned ").append(r.body().unassigned);
                tvGroupTotals.setText(sb.toString());
            }
            @Override public void onFailure(Call<RosterDtos.GroupTotals> c, Throwable t) { }
        });
    }

    // ================================================================== search + paging

    private void startNewSearch() {
        if (!spinnersReady) return;
        handler.removeCallbacks(searchLater);
        generation++;                         // every older answer is now out of date
        if (pageCall != null) pageCall.cancel();
        rows.clear();
        adapter.notifyDataSetChanged();
        page = 0;
        total = 0;
        hasMore = false;
        loadPage(1, generation);
    }

    private void loadPage(int wanted, int gen) {
        String search = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        int pPos = spProgramme.getSelectedItemPosition();
        int gPos = spGroup.getSelectedItemPosition();
        Integer programId = (pPos >= 0 && pPos < programmeIds.size()) ? programmeIds.get(pPos) : null;
        String groupKey = (gPos >= 0 && gPos < groupKeys.size()) ? groupKeys.get(gPos) : null;

        loading = true;
        if (wanted == 1) tvStatus.setText("Searching…");
        pageCall = api().roster(Session.bearer(), search.isEmpty() ? null : search, programId, groupKey, wanted, PAGE_SIZE);
        pageCall.enqueue(new Callback<RosterDtos.Page>() {
            @Override
            public void onResponse(Call<RosterDtos.Page> c, Response<RosterDtos.Page> r) {
                if (gone() || gen != generation) return;   // an answer to an old search: ignore it
                loading = false;
                if (r.isSuccessful() && r.body() != null) {
                    RosterDtos.Page p = r.body();
                    int start = rows.size();
                    if (p.students != null) rows.addAll(p.students);
                    adapter.notifyItemRangeInserted(start, rows.size() - start);
                    page = p.page;
                    total = p.total;
                    hasMore = p.has_more;
                    showCount();
                } else if (r.code() == 401 || r.code() == 403) {
                    tvStatus.setText("Your session has expired. Please log in again.");
                } else {
                    tvStatus.setText("Could not load students (error " + r.code() + ").");
                }
            }

            @Override
            public void onFailure(Call<RosterDtos.Page> c, Throwable t) {
                if (c.isCanceled() || gone() || gen != generation) return;
                loading = false;
                tvStatus.setText(rows.isEmpty()
                        ? "Offline: cannot reach the server. Change the search or a filter to retry."
                        : "Offline: showing the " + rows.size() + " students already loaded (may be out of date).");
            }
        });
    }

    private void showCount() {
        if (total == 0) tvStatus.setText("No students match these filters.");
        else tvStatus.setText("Showing " + rows.size() + " of " + total + (total == 1 ? " student" : " students"));
    }

    // ================================================================== opening a student

    private void openEditor(RosterDtos.Student s) {
        // Step 2d replaces this line with the student editor.
        startActivity(new Intent(this, StudentEditorActivity.class)
                .putExtra(StudentEditorActivity.EXTRA_STUDENT_ID, s.student_id));
    }

    // ================================================================== list

    private class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.Row> {
        class Row extends RecyclerView.ViewHolder {
            final TextView name, group, details;
            Row(View v) {
                super(v);
                name = v.findViewById(R.id.tvRowName);
                group = v.findViewById(R.id.tvRowGroup);
                details = v.findViewById(R.id.tvRowDetails);
            }
        }

        @NonNull @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_roster_student, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Row h, int pos) {
            RosterDtos.Student s = rows.get(pos);
            boolean unassigned = s.lab_group_id == null;
            h.name.setText(s.student_name);
            h.group.setText(unassigned ? "Unassigned" : s.group_name);
            h.group.setTextColor(unassigned ? 0xFFD97706 : 0xFF2563EB);
            String status = "PENDING".equals(s.approval_status) ? "  ·  awaiting approval" : "";
            h.details.setText(s.student_number + "  ·  " + (s.program_name != null ? s.program_name : "—") + status);
            h.itemView.setContentDescription(s.student_name + ", number " + s.student_number + ", "
                    + (unassigned ? "unassigned" : "group " + s.group_name) + ". Double tap to edit.");
            h.itemView.setOnClickListener(v -> openEditor(s));
        }

        @Override public int getItemCount() { return rows.size(); }
    }
}
