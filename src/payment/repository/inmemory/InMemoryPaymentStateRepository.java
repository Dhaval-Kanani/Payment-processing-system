package payment.repository.inmemory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import payment.model.PaymentState;
import payment.repository.PaymentStateRepository;

/** ConcurrentHashMap keyed by paymentId; a single put of an immutable state is an atomic replace. */
public class InMemoryPaymentStateRepository implements PaymentStateRepository {
    private final Map<String, PaymentState> states = new ConcurrentHashMap<>();

    @Override
    public Optional<PaymentState> findByPaymentId(String paymentId) {
        return Optional.ofNullable(states.get(paymentId));
    }

    @Override
    public void save(PaymentState state) {
        states.put(state.paymentId(), state);
    }
}
