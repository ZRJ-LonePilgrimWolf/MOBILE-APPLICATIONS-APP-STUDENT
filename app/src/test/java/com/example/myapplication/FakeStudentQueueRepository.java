package com.example.myapplication;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.ArrayList;
import java.util.List;

/** Test double: keeps the queue in a plain list. No Room, no network, no WorkManager. */
public class FakeStudentQueueRepository implements StudentQueueRepository {
    public final List<LocalStudent> saved = new ArrayList<>();
    public int syncCalls = 0;
    private final List<MutableLiveData<List<LocalStudent>>> watchers = new ArrayList<>();
    private final List<Integer> watcherAccounts = new ArrayList<>();

    @Override public LiveData<List<LocalStudent>> queue(int accountId) {
        MutableLiveData<List<LocalStudent>> d = new MutableLiveData<>();
        watchers.add(d);
        watcherAccounts.add(accountId);
        d.setValue(filter(accountId));
        return d;
    }

    @Override public void save(LocalStudent s) { saved.add(s); publish(); }
    @Override public void syncNow() { syncCalls++; }
    @Override public void discard(LocalStudent s) { saved.remove(s); publish(); }

    private List<LocalStudent> filter(int accountId) {
        List<LocalStudent> out = new ArrayList<>();
        for (LocalStudent s : saved) if (s.ownerAccountId == accountId) out.add(s);
        return out;
    }

    private void publish() {
        for (int i = 0; i < watchers.size(); i++) watchers.get(i).setValue(filter(watcherAccounts.get(i)));
    }
}