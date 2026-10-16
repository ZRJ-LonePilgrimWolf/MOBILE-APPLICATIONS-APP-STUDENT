package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Chat home: the list of group chats this person may use, and everyone they can message privately.
 * (The class keeps its old name "chatbot" so the manifest entry and any old links still work.)
 */
public class chatbot extends BaseActivity {

    private ApiService api;
    private TextView tvStatus;
    private EditText etSearch;
    private RowAdapter adapter;

    private final List<ChatDtos.Room> rooms = new ArrayList<>();
    private final List<ChatDtos.Contact> contacts = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    // Unread counts from the server, refreshed every few seconds while this screen is open.
    private ChatDtos.UnreadResponse unread;
    private static final long UNREAD_REFRESH_MS = 5000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable unreadRefresh = new Runnable() {
        @Override
        public void run() {
            loadUnread();
            handler.postDelayed(this, UNREAD_REFRESH_MS);
        }
    };

    /** One line in the list: a section header, a room, or a person. */
    private static class Row {
        String header;
        ChatDtos.Room room;
        ChatDtos.Contact contact;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        ImageView back = findViewById(R.id.back);
        back.setOnClickListener(v -> finish());

        if (Session.bearer() == null) {
            toast("Please log in first.");
            finish();
            return;
        }

        api = ApiClient.getClient().create(ApiService.class);
        tvStatus = findViewById(R.id.tvChatStatus);
        etSearch = findViewById(R.id.etChatSearch);

        ListView list = findViewById(R.id.chatList);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(shown.get(position)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { rebuild(); }
        });

        publishMyKey();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (api != null) {
            load();
            handler.post(unreadRefresh);   // also loads the counts straight away
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(unreadRefresh);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(unreadRefresh);
        background.shutdown();
    }

    /** Asks the server how many unread messages there are per room and per person. */
    private void loadUnread() {
        api.chatUnread(Session.bearer()).enqueue(new Callback<ChatDtos.UnreadResponse>() {
            @Override
            public void onResponse(Call<ChatDtos.UnreadResponse> call, Response<ChatDtos.UnreadResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                unread = response.body();
                rebuild();   // people with new messages move to the top, badges update
            }

            @Override
            public void onFailure(Call<ChatDtos.UnreadResponse> call, Throwable t) { }
        });
    }

    /** Unread count for one line of the list (0 if none or not loaded yet). */
    private int unreadFor(Row row) {
        if (unread == null || row.header != null) return 0;
        Integer n = null;
        if (row.room != null && unread.rooms != null) n = unread.rooms.get(row.room.room);
        if (row.contact != null && unread.people != null) n = unread.people.get(String.valueOf(row.contact.account_id));
        return n == null ? 0 : n;
    }

    /** Makes sure this phone has a key pair for private messages and that the server has the PUBLIC half. */
    private void publishMyKey() {
        background.execute(() -> {
            try {
                String publicKey = ChatCrypto.ensureKeyPair(Session.accountId());
                api.publishKey(Session.bearer(), new ChatDtos.PublicKeyBody(publicKey)).execute();
            } catch (Exception e) {
                runOnUiThread(() -> toast("Private messages are not available on this phone: " + e.getMessage()));
            }
        });
    }

    private void load() {
        tvStatus.setText("Loading chats…");
        tvStatus.setVisibility(View.VISIBLE);

        api.chatRooms(Session.bearer()).enqueue(new Callback<ChatDtos.RoomsResponse>() {
            @Override
            public void onResponse(Call<ChatDtos.RoomsResponse> call, Response<ChatDtos.RoomsResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showProblem(serverMessage(response));
                    return;
                }
                rooms.clear();
                if (response.body().rooms != null) rooms.addAll(response.body().rooms);
                rebuild();
            }

            @Override
            public void onFailure(Call<ChatDtos.RoomsResponse> call, Throwable t) {
                showProblem("Cannot reach the server. Is the backend running?");
            }
        });

        api.chatContacts(Session.bearer()).enqueue(new Callback<ChatDtos.ContactsResponse>() {
            @Override
            public void onResponse(Call<ChatDtos.ContactsResponse> call, Response<ChatDtos.ContactsResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                contacts.clear();
                if (response.body().contacts != null) contacts.addAll(response.body().contacts);
                rebuild();
            }

            @Override
            public void onFailure(Call<ChatDtos.ContactsResponse> call, Throwable t) { }
        });
    }

    /** Builds the visible list from rooms + contacts, applying the search box. */
    private void rebuild() {
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<Row> groupRows = new ArrayList<>();
        List<Row> staffRows = new ArrayList<>();
        List<Row> peopleRows = new ArrayList<>();

        for (ChatDtos.Room r : rooms) {
            if (!q.isEmpty() && !r.title.toLowerCase(Locale.ROOT).contains(q)) continue;
            Row row = new Row();
            row.room = r;
            if (r.room.startsWith("staff:")) staffRows.add(row); else groupRows.add(row);
        }
        for (ChatDtos.Contact c : contacts) {
            if (!q.isEmpty() && !c.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            Row row = new Row();
            row.contact = c;
            peopleRows.add(row);
        }

        // People who sent you something new go to the top of the private list (stable: keeps the A-Z order otherwise).
        java.util.Collections.sort(peopleRows, (a, b) -> Integer.compare(unreadFor(b), unreadFor(a)));

        shown.clear();
        addSection("GROUP CHATS", groupRows);
        addSection("STAFF ROOMS · lecturers only", staffRows);
        addSection("PRIVATE MESSAGES · end-to-end encrypted", peopleRows);
        adapter.notifyDataSetChanged();

        if (shown.isEmpty()) {
            tvStatus.setText(q.isEmpty() ? "No chats yet." : "Nothing matches \"" + q + "\".");
            tvStatus.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setVisibility(View.GONE);
        }
    }

    private void addSection(String title, List<Row> rows) {
        if (rows.isEmpty()) return;
        Row header = new Row();
        header.header = title;
        shown.add(header);
        shown.addAll(rows);
    }

    private void open(Row row) {
        if (row.header != null) return;
        Intent i = new Intent(this, ChatRoomActivity.class);
        if (row.room != null) {
            i.putExtra(ChatRoomActivity.EXTRA_MODE, ChatRoomActivity.MODE_ROOM);
            i.putExtra(ChatRoomActivity.EXTRA_ROOM, row.room.room);
            i.putExtra(ChatRoomActivity.EXTRA_TITLE, row.room.title);
            i.putExtra(ChatRoomActivity.EXTRA_SUBTITLE, row.room.audience);
        } else {
            i.putExtra(ChatRoomActivity.EXTRA_MODE, ChatRoomActivity.MODE_PRIVATE);
            i.putExtra(ChatRoomActivity.EXTRA_OTHER_ID, row.contact.account_id);
            i.putExtra(ChatRoomActivity.EXTRA_TITLE, row.contact.name);
            i.putExtra(ChatRoomActivity.EXTRA_SUBTITLE, describe(row.contact));
        }
        startActivity(i);
    }

    private static String describe(ChatDtos.Contact c) {
        if ("lecturer".equals(c.role)) return "Lecturer";
        StringBuilder sb = new StringBuilder("Student");
        if (c.program_name != null) sb.append(" · ").append(c.program_name);
        if (c.group_name != null) sb.append(" · ").append(c.group_name);
        return sb.toString();
    }

    private static String iconFor(Row row) {
        if (row.room != null) {
            String key = row.room.room;
            if (key.endsWith("uni")) return "U";
            if (key.contains("prog:")) return "P";
            return "G";
        }
        String n = row.contact.name == null ? "?" : row.contact.name.trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void showProblem(String message) {
        tvStatus.setText(message);
        tvStatus.setVisibility(View.VISIBLE);
    }

    static String serverMessage(Response<?> response) {
        try (ResponseBody err = response.errorBody()) {
            if (err != null) {
                String msg = new JSONObject(err.string()).optString("message", "");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) { }
        if (response.code() == 401 || response.code() == 403) return "Your session has ended. Please log in again.";
        return "Chat is not available right now (" + response.code() + ")";
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- list adapter

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return shown.get(position).header == null; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_row, parent, false);
            Row row = shown.get(position);

            TextView tvHeader = v.findViewById(R.id.tvRowHeader);
            View body = v.findViewById(R.id.rowBody);

            if (row.header != null) {
                tvHeader.setText(row.header);
                tvHeader.setVisibility(View.VISIBLE);
                body.setVisibility(View.GONE);
                return v;
            }
            tvHeader.setVisibility(View.GONE);
            body.setVisibility(View.VISIBLE);

            TextView tvBadge = v.findViewById(R.id.tvRowBadge);
            int count = unreadFor(row);
            if (count > 0) {
                tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                tvBadge.setContentDescription(count + " unread");
                tvBadge.setVisibility(View.VISIBLE);
            } else {
                tvBadge.setVisibility(View.GONE);
            }

            TextView tvIcon = v.findViewById(R.id.tvRowIcon);
            TextView tvTitle = v.findViewById(R.id.tvRowTitle);
            TextView tvSubtitle = v.findViewById(R.id.tvRowSubtitle);

            tvIcon.setText(iconFor(row));
            if (row.room != null) {
                tvTitle.setText(row.room.title);
                tvSubtitle.setText(row.room.audience);
            } else {
                tvTitle.setText(row.contact.name);
                String sub = describe(row.contact);
                if (row.contact.has_key == 0) sub += " · has not opened Chat yet";
                tvSubtitle.setText(sub);
            }
            return v;
        }
    }
}