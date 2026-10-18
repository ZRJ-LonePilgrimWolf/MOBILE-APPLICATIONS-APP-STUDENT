package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** ViewModel tested with a FAKE repository: fast, no phone, no server. */
public class AddStudentViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();   // LiveData runs instantly

    private FakeStudentQueueRepository repo;
    private AddStudentViewModel lecturerA;

    @Before
    public void setUp() {
        repo = new FakeStudentQueueRepository();
        lecturerA = new AddStudentViewModel(repo, 1);
    }

    // Rule: invalid input is refused on the phone and NOTHING is queued
    @Test
    public void invalidNumber_isRejected_andNothingQueued() {
        String err = lecturerA.save("Mary Banda", "12345", "1", "1");
        assertNotNull(err);
        assertTrue(repo.saved.isEmpty());
    }

    @Test
    public void invalidName_isRejected() {
        assertNotNull(lecturerA.save("M4ry", "202100001", "1", "1"));
        assertTrue(repo.saved.isEmpty());
    }

    // Rule: a valid student is saved locally first, owned by the account, with a status and a receipt ID
    @Test
    public void validStudent_isSavedLocally_withOwnerStatusAndOperationId() {
        assertNull(lecturerA.save("Mary Banda", "202100001", "1", "2"));
        assertEquals(1, repo.saved.size());
        LocalStudent s = repo.saved.get(0);
        assertEquals(1, s.ownerAccountId);
        assertEquals(LocalStudent.SAVED, s.status);
        assertEquals(2, s.groupId);
        assertNotNull(s.operationId);
        assertEquals(36, s.operationId.length());   // a UUID
    }

    // Rule: every NEW operation gets its OWN operation ID
    @Test
    public void twoSaves_getDifferentOperationIds() {
        lecturerA.save("Mary Banda", "202100001", "1", "1");
        lecturerA.save("Mary Banda", "202100001", "1", "2");
        assertNotEquals(repo.saved.get(0).operationId, repo.saved.get(1).operationId);
    }

    // Rule: queues are scoped by account - lecturer B never sees lecturer A's queued work
    @Test
    public void queue_isScopedToAccount() {
        lecturerA.save("Mary Banda", "202100001", "1", "1");
        AddStudentViewModel lecturerB = new AddStudentViewModel(repo, 2);
        assertEquals(1, lecturerA.queue.getValue().size());
        assertEquals(0, lecturerB.queue.getValue().size());
    }

    // Rule: with no logged-in account nothing can be queued
    @Test
    public void noAccount_cannotSave() {
        AddStudentViewModel nobody = new AddStudentViewModel(repo, 0);
        assertNotNull(nobody.save("Mary Banda", "202100001", "1", "1"));
        assertTrue(repo.saved.isEmpty());
    }

    @Test
    public void syncNow_asksRepositoryToSync() {
        lecturerA.syncNow();
        assertEquals(1, repo.syncCalls);
    }
}