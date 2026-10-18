package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
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
