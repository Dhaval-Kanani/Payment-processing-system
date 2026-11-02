package payment.publisher;

import static payment.support.Assert.assertEquals;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;

import java.util.List;
import payment.model.PaymentEvent;
import payment.publisher.inmemory.InMemoryDeadLetterPublisher;
import payment.publisher.inmemory.InMemoryRetryPublisher;

public class InMemoryPublishersTest {
    public void testRetryDrainReturnsInOrderAndEmpties() {
        InMemoryRetryPublisher retry = new InMemoryRetryPublisher();
        PaymentEvent a = authorized("pay_1", 1);
        PaymentEvent b = captured("pay_1", 2);
        retry.publish(a, 1);
        retry.publish(b, 1);
        assertEquals(2, retry.size());
        assertEquals(List.of(a, b), retry.drain());
        assertEquals(0, retry.size());
        assertEquals(List.of(), retry.drain());
    }

    public void testDeadLetterKeepsReason() {
        InMemoryDeadLetterPublisher dlq = new InMemoryDeadLetterPublisher();
        PaymentEvent a = authorized("pay_1", 5);
        dlq.publish(a, "future sequence");
        assertEquals(1, dlq.deadLetters().size());
        assertEquals(a, dlq.deadLetters().get(0).event());
        assertEquals("future sequence", dlq.deadLetters().get(0).reason());
    }
}
