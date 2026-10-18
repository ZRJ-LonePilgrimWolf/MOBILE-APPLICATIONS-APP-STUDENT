package com.example.myapplication;

import androidx.lifecycle.LiveData;
import java.util.List;

/** What the screen/ViewModel needs from storage + sync. The real one uses Room + WorkManager; tests use a fake. */
public interface StudentQueueRepository {
    LiveData<List<LocalStudent>> queue(int accountId);
    void save(LocalStudent s);       // durable local save FIRST, then schedule sync
    void syncNow();
    void discard(LocalStudent s);
}
