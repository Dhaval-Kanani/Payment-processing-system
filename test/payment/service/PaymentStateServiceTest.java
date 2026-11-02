package payment.service;

import static payment.support.Assert.assertEquals;
import static payment.support.Assert.assertThrows;
import static payment.support.Assert.assertTrue;
import static payment.support.Events.authorized;
import static payment.support.Events.captured;
import static payment.support.Events.failed;
import static payment.support.Events.refund;

import payment.exception.InvalidTransitionException;
import payment.model.PaymentState;
import payment.model.PaymentStatus;
import payment.support.Fixture;

public class PaymentStateServiceTest {
    private final Fixture f = new Fixture();
    private final PaymentState fresh = PaymentState.initial("pay_1");

    public void testNewAllowsOnlyAuthorizedOrFailed() {
        assertTrue(f.service.canApply(fresh, authorized("pay_1", 1)), "authorized from NEW");
        assertTrue(f.service.canApply(fresh, failed("pay_1", 1)), "failed from NEW");
        assertTrue(!f.service.canApply(fresh, captured("pay_1", 1)), "captured from NEW");
        assertTrue(!f.service.canApply(fresh, refund("pay_1", 1)), "refund from NEW");
    }

    public void testHappyChainAuthorizedCapturedRefunded() {
        PaymentState s1 = f.service.apply(fresh, authorized("pay_1", 1));
        assertEquals(PaymentStatus.AUTHORIZED, s1.status());
        PaymentState s2 = f.service.apply(s1, captured("pay_1", 2));
        assertEquals(PaymentStatus.CAPTURED, s2.status());
        PaymentState s3 = f.service.apply(s2, refund("pay_1", 3));
        assertEquals(PaymentStatus.REFUNDED, s3.status());
        assertEquals(3L, s3.lastProcessedSequence());
        assertEquals(s3, f.repo.findByPaymentId("pay_1").orElseThrow());
    }

    public void testTerminalStatesRejectEverything() {
        PaymentState refunded = fresh.withApplied(PaymentStatus.REFUNDED, 3);
        PaymentState failedState = fresh.withApplied(PaymentStatus.FAILED, 1);
        assertTrue(!f.service.canApply(refunded, captured("pay_1", 4)), "captured after refund");
        assertTrue(!f.service.canApply(failedState, authorized("pay_1", 2)), "authorized after failed");
    }

    public void testInvalidTransitionThrowsAndSavesNothing() {
        assertThrows(InvalidTransitionException.class, () -> f.service.apply(fresh, captured("pay_1", 1)));
        assertEquals(java.util.Optional.empty(), f.repo.findByPaymentId("pay_1"));
    }

    public void testDownstreamFailureSavesNothing() {
        Fixture failing = new Fixture(Fixture.failingDownstream("downstream down"));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> failing.service.apply(fresh, authorized("pay_1", 1)));
        assertEquals("downstream down", ex.getMessage());
        assertEquals(java.util.Optional.empty(), failing.repo.findByPaymentId("pay_1"));
    }
}
