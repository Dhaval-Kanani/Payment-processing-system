package payment.model;

import payment.exception.ValidationException;

public enum EventType {
    AUTHORIZED("payment.authorized"),
    CAPTURED("payment.captured"),
    REFUND("payment.refund"),
    FAILED("payment.failed");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static EventType fromWire(String wireName) {
        for (EventType type : values()) {
            if (type.wireName.equals(wireName)) {
                return type;
            }
        }
        throw new ValidationException("Unknown event_type: " + wireName);
    }
}
