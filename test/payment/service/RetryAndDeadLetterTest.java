package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Assert.assertThrows;
import static payment.support.Events.captured;

import java.util.List;
import payment.exception.PublishException;
import payment.model.PaymentEvent;
import payment.model.ProcessingOutcome;
import payment.publisher.DeadLetterPublisher;
import payment.publisher.RetryPublisher;
import payment.support.Fixture;

/** Retry loop, exhaustion to DLQ, and publish-failure semantics. */
public class RetryAndDeadLetterTest {
    private final Fixture f = new Fixture();

    public void testRetryLoopExhaustsToDlqOnFourthDelivery() {
        PaymentEvent future = captured("pay_1", 5);
        for (int delivery = 1; delivery <= Fixture.MAX_RETRIES; delivery++) {
            assertEquals(ProcessingOutcome.RETRY_SCHEDULED, f.processor.process(future), "delivery " + delivery);
            assertEquals(delivery, f.attempts.get(future.eventId()), "attempts after delivery " + delivery);
            assertEquals(List.of(future), f.retry.drain(), "retry topic after delivery " + delivery);
        }
        assertEquals(ProcessingOutcome.DEAD_LETTERED, f.processor.process(future), "4th delivery");
        assertEquals(0, f.retry.size());
        assertEquals(1, f.dlq.deadLetters().size());
        assertEquals(future, f.dlq.deadLetters().get(0).event());
        assertEquals(0, f.attempts.get(future.eventId()), "attempts cleared after DLQ");
    }

    public void testDeadLetterReasonMentionsCause() {
        PaymentEvent firstNotAuthorized = captured("pay_1", 1);
        for (int i = 0; i < Fixture.MAX_RETRIES; i++) {
            f.processor.process(firstNotAuthorized);
        }
        f.processor.process(firstNotAuthorized);
        String reason = f.dlq.deadLetters().get(0).reason();
        assertEquals(true, reason.startsWith("invalid transition"), "reason=" + reason);
    }

    public void testAttemptsAreTrackedPerEventId() {
        PaymentEvent a = captured("pay_1", 5);
        PaymentEvent b = PaymentEvent.builder().eventId("evt_other").paymentId("pay_1").sequenceNumber(6)
                .eventType(a.eventType()).amount(1).currency("INR").createdAt(a.createdAt()).signature("s").build();
        f.processor.process(a);
        f.processor.process(a);
        f.processor.process(b);
        assertEquals(2, f.attempts.get(a.eventId()));
        assertEquals(1, f.attempts.get(b.eventId()));
    }

    public void testRetryPublishFailureDoesNotIncrementAttempts() {
        RetryPublisher broken = (event, attempt) -> { throw new PublishException("retry topic down"); };
        EventProcessor processor = new EventProcessor(f.repo, f.service, f.attempts, broken, f.dlq, Fixture.MAX_RETRIES);
        PaymentEvent future = captured("pay_1", 5);
        assertThrows(PublishException.class, () -> processor.process(future));
        assertEquals(0, f.attempts.get(future.eventId()));
        assertEquals(0L, f.state("pay_1").lastProcessedSequence());
        assertEquals(0, f.dlq.deadLetters().size());
    }

    public void testDlqPublishFailurePropagatesAndKeepsAttempts() {
        DeadLetterPublisher broken = (event, reason) -> { throw new PublishException("dlq down"); };
        EventProcessor processor = new EventProcessor(f.repo, f.service, f.attempts, f.retry, broken, Fixture.MAX_RETRIES);
        PaymentEvent future = captured("pay_1", 5);
        for (int i = 0; i < Fixture.MAX_RETRIES; i++) {
            processor.process(future);
        }
        assertThrows(PublishException.class, () -> processor.process(future));
        assertEquals(Fixture.MAX_RETRIES, f.attempts.get(future.eventId()), "still exhausted, redelivery will DLQ again");
    }
}
