package payment.model;

/** Consumer verdict. outcome is null on NACK; error is null on ACK. */
public record ConsumeResult(AckDecision ack, ProcessingOutcome outcome, String error) {
    public static ConsumeResult ack(ProcessingOutcome outcome) {
        return new ConsumeResult(AckDecision.ACK, outcome, null);
    }

    public static ConsumeResult nack(String error) {
        return new ConsumeResult(AckDecision.NACK, null, error);
    }
}
