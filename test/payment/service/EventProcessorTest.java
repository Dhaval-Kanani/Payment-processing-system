package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;
import static payment.support.Events.refund;

import java.util.List;
import payment.model.PaymentEvent;
import payment.model.PaymentStatus;
import payment.model.ProcessingOutcome;
import payment.support.Fixture;

/** Ordering + idempotency: the three sequential cases. */
public class EventProcessorTest {
    private final Fixture f = new Fixture();

    public void testFirstEventSeq1AuthorizedIsApplied() {
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(authorized("pay_1", 1)));
        assertEquals(PaymentStatus.AUTHORIZED, f.state("pay_1").status());
        assertEquals(1L, f.state("pay_1").lastProcessedSequence());
    }

    public void testInOrderChainIsApplied() {
        f.processor.process(authorized("pay_1", 1));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(captured("pay_1", 2)));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(refund("pay_1", 3)));
        assertEquals(PaymentStatus.REFUNDED, f.state("pay_1").status());
        assertEquals(3L, f.state("pay_1").lastProcessedSequence());
    }

    public void testDuplicateIsIgnoredWithoutStateChange() {
        PaymentEvent e = authorized("pay_1", 1);
        f.processor.process(e);
        assertEquals(ProcessingOutcome.IGNORED_DUPLICATE, f.processor.process(e));
        assertEquals(1L, f.state("pay_1").lastProcessedSequence());
        assertEquals(0, f.retry.size());
    }

    public void testStaleLowerSequenceIsIgnored() {
        f.processor.process(authorized("pay_1", 1));
        f.processor.process(captured("pay_1", 2));
        assertEquals(ProcessingOutcome.IGNORED_DUPLICATE, f.processor.process(authorized("pay_1", 1)));
        assertEquals(PaymentStatus.CAPTURED, f.state("pay_1").status());
    }

    public void testSequenceZeroOnNewPaymentIsIgnored() {
        assertEquals(ProcessingOutcome.IGNORED_DUPLICATE, f.processor.process(authorized("pay_1", 0)));
        assertEquals(java.util.Optional.empty(), f.repo.findByPaymentId("pay_1"));
    }

    public void testFutureEventGoesToRetryTopic() {
        PaymentEvent e = captured("pay_1", 3);
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(e));
        assertEquals(List.of(e), f.retry.drain());
        assertEquals(0L, f.state("pay_1").lastProcessedSequence());
        assertEquals(1, f.attempts.get(e.eventId()));
    }

    public void testFirstEventNotAuthorizedGoesToRetryTopic() {
        PaymentEvent e = captured("pay_1", 1);
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(e));
        assertEquals(List.of(e), f.retry.drain());
        assertEquals(PaymentStatus.NEW, f.state("pay_1").status());
    }

    public void testInSequenceInvalidTransitionGoesToRetryTopic() {
        f.processor.process(authorized("pay_1", 1));
        PaymentEvent e = refund("pay_1", 2); // refund not allowed from AUTHORIZED
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(e));
        assertEquals(List.of(e), f.retry.drain());
        assertEquals(PaymentStatus.AUTHORIZED, f.state("pay_1").status());
        assertEquals(1L, f.state("pay_1").lastProcessedSequence());
    }

    public void testOutOfOrderThenRefeedApplies() {
        PaymentEvent e3 = refund("pay_1", 3);
        f.processor.process(authorized("pay_1", 1));
        assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(e3));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(captured("pay_1", 2)));
        List<PaymentEvent> refeed = f.retry.drain();
        assertEquals(List.of(e3), refeed);
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(refeed.get(0)));
        assertEquals(PaymentStatus.REFUNDED, f.state("pay_1").status());
        assertEquals(0, f.attempts.get(e3.eventId()), "attempts cleared after apply");
    }

    public void testIndependentPaymentsDoNotInterfere() {
        f.processor.process(authorized("pay_1", 1));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(authorized("pay_2", 1)));
        assertEquals(ProcessingOutcome.APPLIED, f.processor.process(captured("pay_1", 2)));
        assertEquals(PaymentStatus.AUTHORIZED, f.state("pay_2").status());
        assertEquals(PaymentStatus.CAPTURED, f.state("pay_1").status());
    }
}
