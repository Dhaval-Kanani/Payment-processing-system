package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Assert.assertNull;
import static payment.support.Assert.assertThrows;
import static payment.support.Assert.assertTrue;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;

import payment.exception.PublishException;
import payment.exception.ValidationException;
import payment.model.AckDecision;
import payment.model.ConsumeResult;
import payment.model.PaymentEvent;
import payment.model.ProcessingOutcome;
import payment.publisher.RetryPublisher;
import payment.support.Fixture;

public class PaymentEventConsumerTest {
    private final Fixture f = new Fixture();
    private final PaymentEventConsumer consumer = new PaymentEventConsumer(new EventValidator(), f.processor);

    public void testAppliedIsAcked() {
        ConsumeResult r = consumer.onMessage(authorized("pay_1", 1));
        assertEquals(AckDecision.ACK, r.ack());
        assertEquals(ProcessingOutcome.APPLIED, r.outcome());
        assertNull(r.error(), "error");
    }

    public void testDuplicateIsAcked() {
        PaymentEvent e = authorized("pay_1", 1);
        consumer.onMessage(e);
        assertEquals(ConsumeResult.ack(ProcessingOutcome.IGNORED_DUPLICATE), consumer.onMessage(e));
    }

    public void testFutureEventIsAckedAfterRetryPublish() {
        ConsumeResult r = consumer.onMessage(captured("pay_1", 3));
        assertEquals(ConsumeResult.ack(ProcessingOutcome.RETRY_SCHEDULED), r);
        assertEquals(1, f.retry.size(), "published before ack");
    }

    public void testExhaustedEventIsAckedAfterDlqPublish() {
        PaymentEvent e = captured("pay_1", 3);
        for (int i = 0; i < Fixture.MAX_RETRIES; i++) {
            consumer.onMessage(e);
        }
        assertEquals(ConsumeResult.ack(ProcessingOutcome.DEAD_LETTERED), consumer.onMessage(e));
        assertEquals(1, f.dlq.deadLetters().size());
    }

    public void testDownstreamFailureIsNackedAndStateUntouched() {
        Fixture failing = new Fixture(Fixture.failingDownstream("downstream down"));
        PaymentEventConsumer c = new PaymentEventConsumer(new EventValidator(), failing.processor);
        ConsumeResult r = c.onMessage(authorized("pay_1", 1));
        assertEquals(AckDecision.NACK, r.ack());
        assertNull(r.outcome(), "outcome");
        assertTrue(r.error().contains("downstream down"), r.error());
        assertEquals(0L, failing.state("pay_1").lastProcessedSequence());
        // redelivery after downstream recovers: same event now applies
        PaymentEventConsumer healthy = new PaymentEventConsumer(new EventValidator(),
                new EventProcessor(failing.repo, new PaymentStateService(failing.repo, (s, e) -> {}),
                        failing.attempts, failing.retry, failing.dlq, Fixture.MAX_RETRIES));
        assertEquals(ConsumeResult.ack(ProcessingOutcome.APPLIED), healthy.onMessage(authorized("pay_1", 1)));
    }

    public void testRetryPublishFailureIsNacked() {
        RetryPublisher broken = (event, attempt) -> { throw new PublishException("retry topic down"); };
        EventProcessor p = new EventProcessor(f.repo, f.service, f.attempts, broken, f.dlq, Fixture.MAX_RETRIES);
        ConsumeResult r = new PaymentEventConsumer(new EventValidator(), p).onMessage(captured("pay_1", 3));
        assertEquals(AckDecision.NACK, r.ack());
        assertTrue(r.error().contains("retry topic down"), r.error());
    }

    public void testMalformedEventThrowsValidationNotNack() {
        assertThrows(ValidationException.class, () -> consumer.onMessage(null));
        assertThrows(ValidationException.class,
                () -> consumer.onMessage(payment.support.Events.event("evt_x", "pay_1", 0, null)));
        assertEquals(java.util.Optional.empty(), f.repo.findByPaymentId("pay_1"));
    }
}
