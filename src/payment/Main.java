package payment;

import java.time.Instant;
import java.util.List;
import payment.exception.PublishException;
import payment.exception.ValidationException;
import payment.model.ConsumeResult;
import payment.model.EventType;
import payment.model.PaymentEvent;
import payment.model.PaymentState;
import payment.publisher.DownstreamNotifier;
import payment.publisher.RetryPublisher;
import payment.publisher.inmemory.InMemoryDeadLetterPublisher;
import payment.publisher.inmemory.InMemoryRetryPublisher;
import payment.publisher.inmemory.LoggingDownstreamNotifier;
import payment.repository.inmemory.InMemoryPaymentStateRepository;
import payment.repository.inmemory.InMemoryRetryAttemptStore;
import payment.service.EventProcessor;
import payment.service.EventValidator;
import payment.service.PaymentEventConsumer;
import payment.service.PaymentStateService;

/** Demo: happy path, duplicate, out-of-order with re-feed, retry exhaustion to DLQ, downstream failure, bad request. */
public final class Main {
    private static final int MAX_RETRIES = 3;

    private final InMemoryPaymentStateRepository repo = new InMemoryPaymentStateRepository();
    private final InMemoryRetryAttemptStore attempts = new InMemoryRetryAttemptStore();
    private final InMemoryRetryPublisher retryTopic = new InMemoryRetryPublisher();
    private final InMemoryDeadLetterPublisher dlqTopic = new InMemoryDeadLetterPublisher();
    private final PaymentEventConsumer consumer;

    /** @param retryOverride null = use this instance's in-memory retry topic */
    private Main(DownstreamNotifier downstream, RetryPublisher retryOverride) {
        RetryPublisher retry = retryOverride != null ? retryOverride : retryTopic;
        PaymentStateService service = new PaymentStateService(repo, downstream);
        EventProcessor processor = new EventProcessor(repo, service, attempts, retry, dlqTopic, MAX_RETRIES);
        this.consumer = new PaymentEventConsumer(new EventValidator(), processor);
    }

    public static void main(String[] args) {
        Main app = new Main(new LoggingDownstreamNotifier(), null);

        section("1. Happy path: authorized -> captured -> refund");
        app.deliver(event("evt_1", "pay_1", 1, "payment.authorized"));
        app.deliver(event("evt_2", "pay_1", 2, "payment.captured"));
        app.deliver(event("evt_3", "pay_1", 3, "payment.refund"));
        app.printState("pay_1");

        section("2. Duplicate delivery of evt_2 (seq 2 <= last 3) -> ACK, ignored");
        app.deliver(event("evt_2", "pay_1", 2, "payment.captured"));
        app.printState("pay_1");

        section("3. Out of order: seq 2 before seq 1 -> retry topic, then re-feed applies");
        app.deliver(event("evt_12", "pay_2", 2, "payment.captured"));
        app.deliver(event("evt_11", "pay_2", 1, "payment.authorized"));
        app.refeedRetryTopic();
        app.printState("pay_2");

        section("4. Retry exhaustion: first event is captured (not authorized); " + MAX_RETRIES + " retries then DLQ");
        app.deliver(event("evt_21", "pay_3", 1, "payment.captured"));
        for (int i = 0; i < MAX_RETRIES; i++) {
            app.refeedRetryTopic();
        }
        app.printDlq();
        app.printState("pay_3");

        section("5. Downstream failure -> NACK, state untouched; redelivery after recovery applies");
        Main flaky = new Main(new FlakyDownstream(1), null);
        flaky.deliver(event("evt_31", "pay_4", 1, "payment.authorized"));
        flaky.printState("pay_4");
        flaky.deliver(event("evt_31", "pay_4", 1, "payment.authorized"));
        flaky.printState("pay_4");

        section("6. Retry topic down -> NACK, attempts untouched");
        Main brokenRetry = new Main(new LoggingDownstreamNotifier(),
                (e, attempt) -> { throw new PublishException("retry topic unavailable"); });
        brokenRetry.deliver(event("evt_41", "pay_5", 7, "payment.captured"));
        System.out.println("  attempts for evt_41 = " + brokenRetry.attempts.get("evt_41"));

        section("7. Bad request: unknown event_type, sequence 0");
        app.deliverRaw("evt_51", "pay_6", 1, "payment.unknown");
        app.deliverRaw("evt_52", "pay_6", 0, "payment.authorized");
        app.printState("pay_6");
    }

    private void deliver(PaymentEvent event) {
        ConsumeResult result = consumer.onMessage(event);
        System.out.println("  deliver " + event.eventId() + " seq=" + event.sequenceNumber()
                + " " + event.eventType() + " -> " + result.ack()
                + (result.outcome() != null ? " (" + result.outcome() + ")" : "")
                + (result.error() != null ? " error=" + result.error() : ""));
    }

    /** Builds from wire values so validation failures surface like a malformed payload would. */
    private void deliverRaw(String eventId, String paymentId, long seq, String wireType) {
        try {
            deliver(event(eventId, paymentId, seq, wireType));
        } catch (ValidationException ex) {
            System.out.println("  deliver " + eventId + " -> BAD REQUEST: " + ex.getMessage());
        }
    }

    private void refeedRetryTopic() {
        List<PaymentEvent> pending = retryTopic.drain();
        System.out.println("  re-feeding " + pending.size() + " event(s) from retry topic");
        pending.forEach(this::deliver);
    }

    private void printState(String paymentId) {
        PaymentState s = repo.findByPaymentId(paymentId).orElseGet(() -> PaymentState.initial(paymentId));
        System.out.println("  state: " + s);
    }

    private void printDlq() {
        dlqTopic.deadLetters().forEach(d ->
                System.out.println("  DLQ: " + d.event().eventId() + " reason=\"" + d.reason() + "\""));
    }

    private static PaymentEvent event(String eventId, String paymentId, long seq, String wireType) {
        return PaymentEvent.builder().eventId(eventId).paymentId(paymentId).sequenceNumber(seq)
                .eventType(EventType.fromWire(wireType)).amount(10_000L).currency("INR")
                .createdAt(Instant.parse("2026-10-30T10:00:00Z")).signature("sig").build();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    /** Fails the first N notifications, then succeeds. */
    private static final class FlakyDownstream implements DownstreamNotifier {
        private int failuresLeft;

        FlakyDownstream(int failures) {
            this.failuresLeft = failures;
        }

        @Override
        public void notify(PaymentState newState, PaymentEvent event) {
            if (failuresLeft-- > 0) {
                throw new RuntimeException("downstream timeout");
            }
            System.out.println("  [downstream] " + event.eventId() + " -> " + newState);
        }
    }
}
