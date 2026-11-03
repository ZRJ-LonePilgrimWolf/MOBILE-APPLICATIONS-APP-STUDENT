package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Student search: one box that finds people (students and lecturers), lab groups,
 * programmes, courses, timetable days and app pages.
 * Shows only what Chat already shows (names, programmes, groups) - no student numbers or emails.
 */
public class SearchActivity extends BaseActivity {

    private static class Item {
        final int id; final String title, subtitle; final Class<?> target;
        Item(int id, String title, String subtitle) { this(id, title, subtitle, null); }
        Item(int id, String title, String subtitle, Class<?> target) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.target = target;
        }
    }

    private final List<Item> people = new ArrayList<>();
    private final List<Item> groups = new ArrayList<>();
    private final List<Item> programmes = new ArrayList<>();
    private final List<Item> courses = new ArrayList<>();
    private final List<Item> pages = new ArrayList<>();
    private EditText box;
    private LinearLayout results;
    private int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pad = (int) (16 * getResources().getDisplayMetrics().density);

        // ---- Screen layout (built in code, no XML) ----
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        TextView back = new TextView(this);
        back.setText("←  Search");
        back.setTextSize(20);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setPadding(0, 0, 0, pad / 2);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        box = new EditText(this);
        box.setHint("Search people, groups, programmes, courses, timetable");
        box.setSingleLine(true);
        root.addView(box);

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        // ---- Fixed items: courses, timetable days, app pages ----
        courses.add(new Item(361, "ICT 361", "Course · Mobile Application Development"));
        courses.add(new Item(351, "ICT 351", "Course · Software Engineering"));
        courses.add(new Item(341, "ICT 341", "Course · Database Systems · 02:00–04:00 PM, Main IT Lab"));

        pages.add(new Item(0, "Tuesday", "Timetable · classes · lectures", teusday.class));
        pages.add(new Item(0, "Wednesday", "Timetable · classes · lectures", wednesday.class));
        pages.add(new Item(0, "Thursday", "Timetable · classes · lectures", thursday.class));
        pages.add(new Item(0, "Friday", "Timetable · classes · lectures", friday.class));
        pages.add(new Item(0, "Chat", "Page · messages · group chats · private messages", chatbot.class));
        pages.add(new Item(0, "My Profile", "Page · account · student number · group · delete account", Profile.class));

        // Results update as the student types.
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { show(); }
        });

        message("Loading…");
        load();
    }

    /** Loads people (chat contacts) and the groups/programmes list from the server. */
    private void load() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        api.contactsRaw(Session.bearer()).enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed() || !r.isSuccessful() || r.body() == null) return;
                try {
                    JSONArray arr = new JSONObject(r.body().string()).optJSONArray("contacts");
                    people.clear();
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject p = arr.getJSONObject(i);
                        String sub;
                        if ("lecturer".equals(p.optString("role"))) {
                            sub = "Lecturer";
                        } else {
                            StringBuilder sb = new StringBuilder("Student");
                            if (!p.isNull("program_name")) sb.append(" · ").append(p.optString("program_name"));
                            if (!p.isNull("group_name")) sb.append(" · ").append(p.optString("group_name"));
                            sub = sb.toString();
                        }
                        people.add(new Item(p.optInt("account_id"), p.optString("name"), sub));
                    }
                    show();
                } catch (Exception ignored) { }
            }
            @Override public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isFinishing()) message("No connection. Go back and try again.");
            }
        });

        api.referenceRaw().enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (isFinishing() || isDestroyed() || !r.isSuccessful() || r.body() == null) return;
                try {
                    JSONObject json = new JSONObject(r.body().string());
                    JSONArray g = json.optJSONArray("groups");
                    groups.clear();
                    for (int i = 0; g != null && i < g.length(); i++) {
                        JSONObject o = g.getJSONObject(i);
                        groups.add(new Item(o.optInt("lab_group_id"), o.optString("group_name"),
                                "Lab group · " + o.optInt("member_count") + "/" + o.optInt("max_members", 16) + " members"));
                    }
                    JSONArray p = json.optJSONArray("programs");
                    programmes.clear();
                    for (int i = 0; p != null && i < p.length(); i++) {
                        JSONObject o = p.getJSONObject(i);
                        programmes.add(new Item(o.optInt("program_id"), o.optString("program_name"), "Programme"));
                    }
                    show();
                } catch (Exception ignored) { }
            }
            @Override public void onFailure(Call<ResponseBody> c, Throwable t) { }
        });
    }

    /** Redraws the results for whatever is in the box. */
    private void show() {
        String q = box.getText().toString().trim().toLowerCase(Locale.ROOT);
        results.removeAllViews();
        if (q.isEmpty()) {
            message("Type a name, \"lecturer\", \"student\", a group (e.g. G09), a programme, "
                    + "a course (e.g. ICT), a day or \"timetable\".");
            return;
        }
        int found = 0;
        found += section("People", people, q, 50, this::openChat);
        found += section("Lab groups", groups, q, 20, it -> box.setText(it.title));
        found += section("Programmes", programmes, q, 10, it -> box.setText(it.title));
        found += section("Timetable & pages", pages, q, 20,
                it -> startActivity(new Intent(this, it.target)));
        found += section("Courses", courses, q, 10,
                it -> Toast.makeText(this, it.title + ": open it from Home → View Course", Toast.LENGTH_SHORT).show());
        if (found == 0) message("No results for \"" + box.getText().toString().trim() + "\"");
    }

    private interface OnPick { void pick(Item it); }

    /** Adds one heading plus its matching rows. Returns how many matched. */
    private int section(String heading, List<Item> list, String q, int max, OnPick onPick) {
        List<Item> hits = new ArrayList<>();
        for (Item it : list) {
            if (it.title.toLowerCase(Locale.ROOT).contains(q)
                    || it.subtitle.toLowerCase(Locale.ROOT).contains(q)) hits.add(it);
        }
        if (hits.isEmpty()) return 0;

        TextView h = new TextView(this);
        h.setText(heading + " (" + hits.size() + ")");
        h.setTextSize(16);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        h.setPadding(0, pad, 0, pad / 4);
        results.addView(h);

        for (int i = 0; i < hits.size() && i < max; i++) {
            Item it = hits.get(i);
            TextView row = new TextView(this);
            row.setText(it.title + "\n" + it.subtitle);
            row.setTextSize(15);
            row.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
            row.setOnClickListener(v -> onPick.pick(it));
            results.addView(row);
        }
        return hits.size();
    }

    /** Opens a private chat, exactly like the Chat screen does. */
    private void openChat(Item it) {
        Intent i = new Intent(this, ChatRoomActivity.class);
        i.putExtra(ChatRoomActivity.EXTRA_MODE, ChatRoomActivity.MODE_PRIVATE);
        i.putExtra(ChatRoomActivity.EXTRA_OTHER_ID, it.id);
        i.putExtra(ChatRoomActivity.EXTRA_TITLE, it.title);
        i.putExtra(ChatRoomActivity.EXTRA_SUBTITLE, it.subtitle);
        startActivity(i);
    }

    private void message(String text) {
        results.removeAllViews();
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, pad, 0, 0);
        results.addView(t);
    }
}