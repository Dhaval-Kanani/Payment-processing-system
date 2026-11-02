package payment.repository;

import java.util.Optional;
import payment.model.PaymentState;

public interface PaymentStateRepository {
    Optional<PaymentState> findByPaymentId(String paymentId);

    void save(PaymentState state);
}
