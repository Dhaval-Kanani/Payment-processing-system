package payment.repository;

import static payment.support.Assert.assertEquals;
import static payment.support.Assert.assertTrue;

import java.util.Optional;
import payment.model.PaymentState;
import payment.model.PaymentStatus;
import payment.repository.inmemory.InMemoryPaymentStateRepository;

public class InMemoryPaymentStateRepositoryTest {
    private final InMemoryPaymentStateRepository repo = new InMemoryPaymentStateRepository();

    public void testFindUnknownReturnsEmpty() {
        assertEquals(Optional.empty(), repo.findByPaymentId("pay_x"));
    }

    public void testSaveThenFind() {
        PaymentState s = PaymentState.initial("pay_1").withApplied(PaymentStatus.AUTHORIZED, 1);
        repo.save(s);
        assertEquals(Optional.of(s), repo.findByPaymentId("pay_1"));
    }

    public void testSaveReplacesExisting() {
        repo.save(PaymentState.initial("pay_1").withApplied(PaymentStatus.AUTHORIZED, 1));
        repo.save(PaymentState.initial("pay_1").withApplied(PaymentStatus.CAPTURED, 2));
        PaymentState found = repo.findByPaymentId("pay_1").orElseThrow();
        assertEquals(PaymentStatus.CAPTURED, found.status());
        assertEquals(2L, found.lastProcessedSequence());
        assertTrue(found.lastProcessedSequence() == 2, "sequence replaced");
    }
}
