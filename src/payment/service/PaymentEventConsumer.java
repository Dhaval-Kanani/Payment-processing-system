package payment.service;

import payment.exception.ValidationException;
import payment.model.ConsumeResult;
import payment.model.PaymentEvent;
import payment.model.ProcessingOutcome;

/**
 * Entry point for a delivered message. Decides ACK / NACK.
 * ValidationException is deliberately not caught: a malformed message is a bad request,
 * not something redelivery can fix.
 */
public class PaymentEventConsumer {
    private final EventValidator validator;
    private final EventProcessor processor;

    public PaymentEventConsumer(EventValidator validator, EventProcessor processor) {
        this.validator = validator;
        this.processor = processor;
    }

    public ConsumeResult onMessage(PaymentEvent event) {
        validator.validate(event);
        try {
            ProcessingOutcome outcome = processor.process(event);
            return ConsumeResult.ack(outcome);
        } catch (ValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            return ConsumeResult.nack(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
