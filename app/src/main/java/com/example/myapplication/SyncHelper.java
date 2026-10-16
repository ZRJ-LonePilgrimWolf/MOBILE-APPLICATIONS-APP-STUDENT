package com.example.myapplication;

import android.app.Activity;
import java.io.IOException;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class SyncHelper {
    // Sends every PENDING student to the server. Each one reuses its OWN operationId, so a retry can never create a duplicate.
    public static void sync(Activity act, Runnable onDone) {
        new Thread(() -> {
            AppDb db = AppDb.get(act);
            ApiService api = ApiClient.getClient().create(ApiService.class);
            for (LocalStudent s : db.dao().pending()) {
                try {
                    NewStudent body = new NewStudent(s.operationId, s.name, s.number, s.programId, s.groupId);
                    Response<ResponseBody> r = api.createStudent(Session.bearer(), body).execute();
                    if (r.isSuccessful()) {
                        s.status = "SYNCED";
                        s.message = "Saved on server";
                        db.dao().update(s);
                    } else if (r.code() == 401 || r.code() == 403) {
                        s.message = "Session expired - log in again, then Sync";   // stays PENDING
                        db.dao().update(s);
                    } else {
                        String err = r.errorBody() != null ? r.errorBody().string() : "";
                        s.status = "REJECTED";
                        s.message = "Server " + r.code() + ": " + err;
                        db.dao().update(s);
                    }
                } catch (IOException e) {
                    s.message = "Offline - will retry";   // stays PENDING
                    db.dao().update(s);
                }
            }
            act.runOnUiThread(onDone);
        }).start();
    }
}
