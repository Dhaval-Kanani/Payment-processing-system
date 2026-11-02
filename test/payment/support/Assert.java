package payment.support;

import java.util.Objects;

/** Minimal assertions for the hand-rolled runner. */
public final class Assert {
    private Assert() {}

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "");
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertNull(Object actual, String message) {
        if (actual != null) {
            throw new AssertionError(message + " expected null but was <" + actual + ">");
        }
    }

    public static <T extends Throwable> T assertThrows(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
