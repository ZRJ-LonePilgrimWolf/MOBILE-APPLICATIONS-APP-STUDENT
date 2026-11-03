package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Every screen extends this, so every screen gets:
 *  - the saved mode and colour,
 *  - the floating chat button (screens that ask for it),
 *  - one shared logout(), and
 *  - a session guard: a protected screen opened without a login (after logout, or after Android
 *    killed the app and the in-memory token was lost) sends the user back to the login screen.
 */
public class BaseActivity extends AppCompatActivity {

    // Screens anyone may open without logging in
    private static final Set<Class<?>> PUBLIC_SCREENS = new HashSet<>(Arrays.asList(
            MainActivity.class, lecturer_login.class, Registration.class, lecture_registration.class,
            resetpassword.class, ThemeActivity.class, SettingsActivity.class));

    private String appliedAccent;
    private ChatBubble chatBubble;   // only on screens that call enableChatBubble()

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedMode(this);
        appliedAccent = ThemeHelper.getAccent(this);
        setTheme(ThemeHelper.styleFor(appliedAccent));   // MUST be before super.onCreate
        super.onCreate(savedInstanceState);
    }

    /**
     * Newer Android draws the app under the phone's Back/Home/Recents bar (edge-to-edge).
     * This pushes every screen's content up by the height of that bar (and above the keyboard
     * when it is open), so bottom buttons are never hidden. Runs for every screen.
     */
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        final int baseLeft = content.getPaddingLeft();
        final int baseRight = content.getPaddingRight();
        final int baseBottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(nav.bottom, ime.bottom);
            v.setPadding(baseLeft + nav.left, v.getPaddingTop(), baseRight + nav.right, baseBottom + bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsLogin() && Session.bearer() == null) {   // session guard
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            goToLogin(false);
            return;
        }
        recreateIfAccentChanged();   // e.g. coming back from the Theme screen
        if (chatBubble != null) chatBubble.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatBubble != null) chatBubble.stop();
    }

    /** Call once in onCreate, AFTER setContentView, to show the floating chat button with unread count. */
    protected void enableChatBubble() {
        if (chatBubble == null) chatBubble = ChatBubble.attach(this);
    }

    /** Rebuilds this screen if the colour was changed since it was built. */
    protected void recreateIfAccentChanged() {
        if (!isFinishing() && !isDestroyed()
                && !ThemeHelper.getAccent(this).equals(appliedAccent)) {
            recreate();
        }
    }

    /** True for every screen except the login/registration/theme/settings screens. */
    protected boolean needsLogin() {
        return !PUBLIC_SCREENS.contains(getClass());
    }

    /** Delete my own account (students and lecturers), confirmed with the password. */
    protected void confirmDeleteAccount() {
        if (Session.bearer() == null) { logout(); return; }
        final android.widget.EditText pw = new android.widget.EditText(this);
        pw.setHint("Your password");
        pw.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        pw.setPadding(pad, pad / 2, pad, pad / 2);

        final androidx.appcompat.app.AlertDialog d = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete my account")
                .setMessage("This permanently deletes your account. You will be logged out and "
                        + "cannot log in with it again. Enter your password to confirm.")
                .setView(pw)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", null)
                .create();

        d.setOnShowListener(x -> d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String password = pw.getText().toString();
                    if (password.isEmpty()) { pw.setError("Enter your password"); return; }
                    v.setEnabled(false);
                    java.util.Map<String, String> body = new java.util.HashMap<>();
                    body.put("password", password);
                    ApiClient.getClient().create(ApiService.class)
                            .deleteMyAccount(Session.bearer(), body)
                            .enqueue(new retrofit2.Callback<okhttp3.ResponseBody>() {
                                @Override
                                public void onResponse(retrofit2.Call<okhttp3.ResponseBody> c,
                                                       retrofit2.Response<okhttp3.ResponseBody> r) {
                                    if (isFinishing() || isDestroyed()) return;
                                    v.setEnabled(true);
                                    if (r.isSuccessful() || r.code() == 404) {
                                        d.dismiss();
                                        android.widget.Toast.makeText(BaseActivity.this,
                                                "Your account has been deleted", android.widget.Toast.LENGTH_LONG).show();
                                        logout();
                                    } else if (r.code() == 401) {
                                        pw.setError("Password is incorrect");
                                    } else {
                                        android.widget.Toast.makeText(BaseActivity.this,
                                                "Could not delete the account (" + r.code() + ")",
                                                android.widget.Toast.LENGTH_LONG).show();
                                    }
                                }
                                @Override
                                public void onFailure(retrofit2.Call<okhttp3.ResponseBody> c, Throwable t) {
                                    if (isFinishing() || isDestroyed()) return;
                                    v.setEnabled(true);
                                    android.widget.Toast.makeText(BaseActivity.this,
                                            "No connection. Try again.", android.widget.Toast.LENGTH_LONG).show();
                                }
                            });
                }));
        d.show();
    }

    /**
     * Log out: forget the token (it only lives in memory), clear this account's finished local copies,
     * and open the right login screen with the back stack wiped, so Back cannot return to account data.
     */
    protected void logout() {
        boolean lecturer = "lecturer".equals(Session.role());
        Session.clear();
        final android.content.Context app = getApplicationContext();
        new Thread(() -> AppDb.get(app).dao().deleteFinished()).start();   // off the main thread
        goToLogin(lecturer);
    }

    private void goToLogin(boolean lecturer) {
        Intent i = new Intent(this, lecturer ? lecturer_login.class : MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
