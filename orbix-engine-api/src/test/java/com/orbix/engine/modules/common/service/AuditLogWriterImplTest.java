package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.entity.AuditLog;
import com.orbix.engine.modules.common.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuditLogWriterImpl.
 *
 * ISSUE-AUDIT-01: verifies that concurrent writes serialize the head-read +
 * insert so prev_hash always references a unique predecessor (no chain forks).
 *
 * ISSUE-ADMIN-001: verifies that the beforeJson field from Record is written
 * to the AuditLog row.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogWriterImplTest {

    @Mock
    private AuditLogRepository repo;

    private AuditLogWriterImpl writer;

    @BeforeEach
    void setUp() {
        writer = new AuditLogWriterImpl(repo);
    }

    private static AuditLogWriter.Record record(String action) {
        return new AuditLogWriter.Record(
            1L, 1L, 1L, action, "TestEntity", "1",
            null, "after-" + action, null);
    }

    // -----------------------------------------------------------------------
    // ISSUE-ADMIN-001: beforeJson is written to the AuditLog row
    // -----------------------------------------------------------------------

    @Test
    void write_persistsBeforeJson() {
        AuditLog head = new AuditLog();
        head.setRowHash(AuditHash.GENESIS);
        when(repo.findTopByOrderByIdDesc()).thenReturn(Optional.of(head));
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLogWriter.Record rec = new AuditLogWriter.Record(
            1L, 1L, 1L, "UPDATE", "Branch", "uid-1",
            "before-state-json", "after-state-json", null);

        writer.write(rec);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getBeforeJson()).isEqualTo("before-state-json");
        assertThat(saved.getAfterJson()).isEqualTo("after-state-json");
    }

    @Test
    void write_nullBeforeJson_isStoredAsNull() {
        AuditLog head = new AuditLog();
        head.setRowHash(AuditHash.GENESIS);
        when(repo.findTopByOrderByIdDesc()).thenReturn(Optional.of(head));
        when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        writer.write(record("CREATE"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(repo).save(captor.capture());
        assertThat(captor.getValue().getBeforeJson()).isNull();
    }

    // -----------------------------------------------------------------------
    // ISSUE-AUDIT-01: concurrent writes must not fork the hash chain
    // -----------------------------------------------------------------------

    /**
     * Fires N concurrent write() calls against a mock repository that simulates
     * an in-memory chain (each saved row becomes the new head). Verifies that
     * every row's prev_hash equals the row_hash of its predecessor by id — i.e.
     * the chain is strictly linear (no forks).
     *
     * Without the ReentrantLock, concurrent threads race on findTopByOrderByIdDesc()
     * and produce forks (multiple rows sharing the same prev_hash). With the lock,
     * the chain must be linear.
     */
    @Test
    void concurrentWrites_produceLinearChain() throws InterruptedException {
        int threads = 20;
        List<AuditLog> saved = new ArrayList<>();
        AtomicInteger idSeq = new AtomicInteger(1);

        // The mock head always returns the last saved row (simulates DB ordering by id).
        lenient().when(repo.findTopByOrderByIdDesc()).thenAnswer(inv -> {
            synchronized (saved) {
                return saved.isEmpty() ? Optional.empty()
                    : Optional.of(saved.get(saved.size() - 1));
            }
        });
        lenient().when(repo.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog row = inv.getArgument(0);
            row.setId((long) idSeq.getAndIncrement());
            synchronized (saved) {
                saved.add(row);
            }
            return row;
        });

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                writer.write(record("ACTION_" + idx));
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Verify linear chain: every row's prev_hash == predecessor's row_hash
        assertThat(saved).hasSize(threads);
        // Sort by id to walk in insertion order
        saved.sort(java.util.Comparator.comparingLong(AuditLog::getId));

        for (int i = 1; i < saved.size(); i++) {
            AuditLog prev = saved.get(i - 1);
            AuditLog curr = saved.get(i);
            assertThat(curr.getPrevHash())
                .as("Row %d prev_hash must equal row %d row_hash (no chain fork)",
                    curr.getId(), prev.getId())
                .isEqualTo(prev.getRowHash());
        }

        // Also verify no two rows share the same prev_hash (no forks)
        long distinctPrevHashes = saved.stream()
            .map(AuditLog::getPrevHash)
            .distinct()
            .count();
        assertThat(distinctPrevHashes)
            .as("Each prev_hash must be unique — no two rows may share a predecessor (fork)")
            .isEqualTo(threads);
    }
}
