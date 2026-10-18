package com.example.myapplication;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Real repository: Room for durable storage, WorkManager for sending. */
public class RoomStudentQueueRepository implements StudentQueueRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();   // one thread = writes stay in order
    private final Context app;
    private final LocalStudentDao dao;

    public RoomStudentQueueRepository(Context context) {
        app = context.getApplicationContext();
        dao = AppDb.get(app).dao();
    }

    @Override public LiveData<List<LocalStudent>> queue(int accountId) { return dao.watch(accountId); }

    @Override public void save(LocalStudent s) {
        IO.execute(() -> {
            dao.insert(s);               // saved on the phone before any network call
            SyncWorker.enqueue(app);     // then WorkManager sends it when there is internet
        });
    }

    @Override public void syncNow() { SyncWorker.enqueue(app); }

    @Override public void discard(LocalStudent s) { IO.execute(() -> dao.delete(s)); }
}
