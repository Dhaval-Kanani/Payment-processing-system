package payment.service;

import static payment.support.Assert.assertThrows;
import static payment.support.Assert.assertTrue;
import static payment.support.Events.authorized;

import java.time.Instant;
import payment.exception.ValidationException;
import payment.model.EventType;
import payment.model.PaymentEvent;

public class EventValidatorTest {
    private final EventValidator validator = new EventValidator();

    private static PaymentEvent.Builder valid() {
        return PaymentEvent.builder().eventId("evt_1").paymentId("pay_1").sequenceNumber(1)
                .eventType(EventType.AUTHORIZED).amount(100).currency("INR")
                .createdAt(Instant.parse("2026-10-30T10:00:00Z")).signature("sig");
    }

    public void testValidEventPasses() {
        validator.validate(authorized("pay_1", 1));
    }

    public void testNullEvent() {
        assertThrows(ValidationException.class, () -> validator.validate(null));
    }

    public void testBlankEventId() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(valid().eventId("  ").build()));
        assertTrue(ex.getMessage().contains("event_id"), ex.getMessage());
    }

    public void testNullPaymentId() {
        assertThrows(ValidationException.class, () -> validator.validate(valid().paymentId(null).build()));
    }

    public void testSequenceZeroAndNegative() {
        assertThrows(ValidationException.class, () -> validator.validate(valid().sequenceNumber(0).build()));
        assertThrows(ValidationException.class, () -> validator.validate(valid().sequenceNumber(-1).build()));
    }

    public void testNullEventType() {
        assertThrows(ValidationException.class, () -> validator.validate(valid().eventType(null).build()));
    }

    public void testNegativeAmount() {
        assertThrows(ValidationException.class, () -> validator.validate(valid().amount(-1).build()));
    }

    public void testZeroAmountAllowed() {
        validator.validate(valid().amount(0).build());
    }

    public void testBlankCurrencyAndNullCreatedAt() {
        assertThrows(ValidationException.class, () -> validator.validate(valid().currency("").build()));
        assertThrows(ValidationException.class, () -> validator.validate(valid().createdAt(null).build()));
    }

    public void testUnknownWireTypeIsValidationError() {
        assertThrows(ValidationException.class, () -> EventType.fromWire("payment.unknown"));
    }
}
