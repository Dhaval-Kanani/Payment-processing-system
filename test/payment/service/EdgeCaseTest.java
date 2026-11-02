package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;
import static payment.support.Events.event;
import static payment.support.Events.failed;
import static payment.support.Events.refund;

import java.util.List;
import payment.model.EventType;
import payment.model.PaymentEvent;
import payment.model.PaymentStatus;
import payment.model.ProcessingOutcome;
import payment.support.Fixture;

/** Remaining edge cases from NOTES.md §5. */
public class EdgeCaseTest {
    private final Fixture f = new Fixture();

    public void testFailedFromNewIsTerminal() {
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(failed("pay_1", 1)));
        assertEquals(PaymentStatus.FAILED, f.state("pay_1").status());
        PaymentEvent late = authorized("pay_1", 2);
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(late));
        assertEquals(PaymentStatus.FAILED, f.state("pay_1").status());
    }

    public void testFailedAfterAuthorized() {
        f.processor.process(authorized("pay_1", 1));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(failed("pay_1", 2)));
        assertEquals(PaymentStatus.FAILED, f.state("pay_1").status());
    }

    public void testEventOnRefundedWithValidSequenceRetriesThenDlqs() {
        f.processor.process(authorized("pay_1", 1));
        f.processor.process(captured("pay_1", 2));
        f.processor.process(refund("pay_1", 3));
        PaymentEvent extra = captured("pay_1", 4);
        for (int i = 0; i < Fixture.MAX_RETRIES; i++) {
            assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(extra));
        }
        assertEquals(ProcessingOutcome.DEAD_LETTERED, f.processor.process(extra));
        assertEquals(PaymentStatus.REFUNDED, f.state("pay_1").status());
        assertEquals(3L, f.state("pay_1").lastProcessedSequence());
    }

    public void testSameSequenceDifferentEventIdIsIgnoredAsDuplicate() {
        f.processor.process(authorized("pay_1", 1));
        PaymentEvent sameSeqOtherId = event("evt_dup_other_id", "pay_1", 1, EventType.FAILED);
        assertEquals(ProcessingOutcome.IGNORED_DUPLICATE, f.processor.process(sameSeqOtherId));
        assertEquals(PaymentStatus.AUTHORIZED, f.state("pay_1").status());
    }

    public void testDuplicateDeliveryClearsStaleAttempts() {
        PaymentEvent e2 = captured("pay_1", 2);
        f.processor.process(e2); // future → attempts=1
        assertEquals(1, f.attempts.get(e2.eventId()));
        f.processor.process(authorized("pay_1", 1));
        f.processor.process(e2); // applied → cleared
        assertEquals(0, f.attempts.get(e2.eventId()));
        f.processor.process(e2); // duplicate → stays cleared
        assertEquals(0, f.attempts.get(e2.eventId()));
    }

    public void testLargeGapGoesToRetryNotApplied() {
        PaymentEvent far = captured("pay_1", Long.MAX_VALUE);
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(far));
        assertEquals(List.of(far), f.retry.drain());
    }

    public void testRetryAfterGapFilledDoesNotDoubleApply() {
        PaymentEvent e2 = captured("pay_1", 2);
        f.processor.process(e2);                       // retry
        f.processor.process(authorized("pay_1", 1));   // fills gap
        f.processor.process(e2);                       // direct redelivery applies
        assertEquals(ProcessingOutcome.IGNORED_DUPLICATE, f.processor.process(f.retry.drain().get(0)));
        assertEquals(2L, f.state("pay_1").lastProcessedSequence());
    }
}
