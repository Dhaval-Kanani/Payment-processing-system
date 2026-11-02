package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Assert.assertTrue;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;
import static payment.support.Events.refund;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import payment.model.PaymentEvent;
import payment.model.PaymentStatus;
import payment.model.ProcessingOutcome;
import payment.support.Fixture;

/** Shared state: payment state map, attempt store, per-payment lock. */
public class ConcurrencyTest {
    private static final int THREADS = 16;

    /** All tasks start on a latch so they really contend. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> fut : futures) {
            results.add(fut.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "pool terminated");
        return results;
    }

    public void testConcurrentDuplicatesApplyExactlyOnce() throws Exception {
        AtomicInteger downstreamCalls = new AtomicInteger();
        Fixture f = new Fixture((s, e) -> downstreamCalls.incrementAndGet());
        PaymentEvent e = authorized("pay_1", 1);
        List<Callable<ProcessingOutcome>> tasks = Collections.nCopies(THREADS, () -> f.processor.process(e));

        List<ProcessingOutcome> outcomes = runConcurrently(tasks);

        assertEquals(1L, outcomes.stream().filter(o -> o == ProcessingOutcome.APPLIED).count(), "applied once");
        assertEquals((long) THREADS - 1, outcomes.stream().filter(o -> o == ProcessingOutcome.IGNORED_DUPLICATE).count());
        assertEquals(1, downstreamCalls.get(), "downstream notified once");
        assertEquals(1L, f.state("pay_1").lastProcessedSequence());
    }

    public void testConcurrentSeq1AndSeq2EndConsistent() throws Exception {
        Fixture f = new Fixture();
        PaymentEvent e1 = authorized("pay_1", 1);
        PaymentEvent e2 = captured("pay_1", 2);
        List<Callable<ProcessingOutcome>> tasks = List.of(() -> f.processor.process(e1), () -> f.processor.process(e2));

        List<ProcessingOutcome> outcomes = runConcurrently(tasks);

        assertEquals(ProcessingOutcome.APPLIED, outcomes.get(0), "seq 1 always applies");
        if (outcomes.get(1) == ProcessingOutcome.RETRY_SCHEDULED) {
            // seq 2 won the lock first: it must be on the retry topic and apply on re-feed
            assertEquals(List.of(e2), f.retry.drain());
            assertEquals(ProcessingOutcome.APPLIED, f.processor.process(e2));
        } else {
            assertEquals(ProcessingOutcome.APPLIED, outcomes.get(1));
        }
        assertEquals(PaymentStatus.CAPTURED, f.state("pay_1").status());
        assertEquals(2L, f.state("pay_1").lastProcessedSequence());
    }

    public void testManyPaymentsInParallelEachEndRefunded() throws Exception {
        Fixture f = new Fixture();
        int payments = 50;
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < payments; i++) {
            String id = "pay_" + i;
            tasks.add(() -> {
                f.processor.process(authorized(id, 1));
                f.processor.process(captured(id, 2));
                f.processor.process(refund(id, 3));
                return null;
            });
        }
        runConcurrently(tasks);
        for (int i = 0; i < payments; i++) {
            assertEquals(PaymentStatus.REFUNDED, f.state("pay_" + i).status(), "pay_" + i);
            assertEquals(3L, f.state("pay_" + i).lastProcessedSequence(), "pay_" + i);
        }
        assertEquals(0, f.retry.size());
        assertEquals(0, f.dlq.deadLetters().size());
    }

    public void testShuffledDeliveriesWithRefeedConverge() throws Exception {
        Fixture f = new Fixture();
        List<PaymentEvent> all = new ArrayList<>(List.of(
                authorized("pay_1", 1), captured("pay_1", 2), refund("pay_1", 3)));
        Collections.reverse(all); // worst case: 3, 2, 1
        List<Callable<ProcessingOutcome>> tasks = new ArrayList<>();
        for (PaymentEvent e : all) {
            tasks.add(() -> f.processor.process(e));
        }
        runConcurrently(tasks);
        // drain + re-feed until the retry topic is empty (bounded by MAX_RETRIES per event)
        List<PaymentEvent> pending = f.retry.drain();
        int rounds = 0;
        while (!pending.isEmpty() && rounds++ < 10) {
            for (PaymentEvent e : pending) {
                f.processor.process(e);
            }
            pending = f.retry.drain();
        }
        assertEquals(PaymentStatus.REFUNDED, f.state("pay_1").status());
        assertEquals(3L, f.state("pay_1").lastProcessedSequence());
        assertEquals(0, f.dlq.deadLetters().size(), "no DLQ: every event lands within 3 retries");
    }

    public void testConcurrentFutureEventsCountAttemptsExactly() throws Exception {
        Fixture f = new Fixture();
        PaymentEvent future = captured("pay_1", 9);
        List<Callable<ProcessingOutcome>> tasks = Collections.nCopies(THREADS, () -> f.processor.process(future));

        List<ProcessingOutcome> outcomes = runConcurrently(tasks);

        long retried = outcomes.stream().filter(o -> o == ProcessingOutcome.RETRY_SCHEDULED).count();
        long dead = outcomes.stream().filter(o -> o == ProcessingOutcome.DEAD_LETTERED).count();
        assertEquals((long) THREADS, retried + dead, "every delivery decided");
        // Deliveries are serialized by the per-payment lock: pattern is 3 retries, 1 DLQ, repeat.
        assertEquals((long) THREADS / (Fixture.MAX_RETRIES + 1), dead, "DLQ every 4th delivery");
        assertEquals(dead, (long) f.dlq.deadLetters().size());
        assertEquals(retried, (long) f.retry.size());
    }
}
