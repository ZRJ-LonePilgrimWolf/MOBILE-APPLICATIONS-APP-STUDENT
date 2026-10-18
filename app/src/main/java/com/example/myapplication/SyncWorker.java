package com.example.myapplication;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Sends the logged-in lecturer's queued students to the server, oldest first.
 * - Runs only when there is a network connection (WorkManager constraint).
 * - Offline / server error: stops (to keep the order) and retries later with growing waits (exponential backoff).
 * - Every retry reuses the row's operationId, so the server's receipt makes it happen only once.
 * - No token (logged out / app restarted) or a different account logged in: pauses. Nothing is sent under another user.
 */
public class SyncWorker extends Worker {
    private static final String UNIQUE_NAME = "nexa-student-sync";

    public SyncWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    /** Schedule a sync now (or as soon as there is internet). Also used by the "Sync now" button. */
    public static void enqueue(Context c) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        String bearer = Session.bearer();
        int account = Session.accountId();
        if (bearer == null || account <= 0) return Result.retry();          // session paused: wait for a login

        LocalStudentDao dao = AppDb.get(getApplicationContext()).dao();
        ApiService api = ApiClient.getClient().create(ApiService.class);
        dao.resetStuckSyncing(account);

        for (LocalStudent s : dao.pending(account)) {
            if (isStopped()) return Result.retry();
            if (Session.accountId() != account || Session.bearer() == null) return Result.retry();  // user changed

            s.status = LocalStudent.SYNCING;
            s.message = "Sending…";
            dao.update(s);
            try {
                NewStudent body = new NewStudent(s.operationId, s.name, s.number, s.programId, s.groupId);
                Response<ResponseBody> r = api.createStudent(bearer, body).execute();
                if (r.isSuccessful()) {
                    s.status = LocalStudent.SYNCED;
                    s.message = "Saved on the server";
                    dao.update(s);
                } else if (r.code() == 401 || r.code() == 403) {
                    s.status = LocalStudent.PENDING;
                    s.message = "Paused: session expired. Log in again to continue.";
                    dao.update(s);
                    return Result.retry();
                } else if (r.code() >= 500) {
                    s.status = LocalStudent.PENDING;
                    s.message = "Server problem - will retry automatically";
                    dao.update(s);
                    return Result.retry();
                } else {
                    StudentDtos.ServerError e = edit2.readError(r.errorBody());
                    s.status = LocalStudent.REJECTED;
                    s.message = (e.code != null ? e.code + ": " : "") + (e.message != null ? e.message : "Refused (" + r.code() + ")");
                    dao.update(s);
                }
            } catch (IOException ex) {
                s.status = LocalStudent.PENDING;
                s.message = "Offline - will retry automatically";
                dao.update(s);
                return Result.retry();                // stop here so later operations keep their order
            }
        }
        return Result.success();
    }
}
