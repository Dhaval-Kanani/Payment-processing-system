package payment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled runner: instantiates each listed class and invokes every public
 * no-arg method whose name starts with "test". Exit code 1 on any failure.
 */
public final class TestRunner {
    private static final List<Class<?>> SUITES = List.of(
            payment.repository.InMemoryPaymentStateRepositoryTest.class,
            payment.repository.InMemoryRetryAttemptStoreTest.class,
            payment.publisher.InMemoryPublishersTest.class,
            payment.service.PaymentStateServiceTest.class,
            payment.service.EventProcessorTest.class,
            payment.service.RetryAndDeadLetterTest.class,
            payment.service.EventValidatorTest.class,
            payment.service.PaymentEventConsumerTest.class,
            payment.service.EdgeCaseTest.class,
            payment.service.ConcurrencyTest.class
    );

    public static void main(String[] args) throws Exception {
        int passed = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> suite : SUITES) {
            for (Method m : suite.getMethods()) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0
                        || Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                String name = suite.getSimpleName() + "." + m.getName();
                try {
                    Object instance = suite.getDeclaredConstructor().newInstance();
                    m.invoke(instance);
                    passed++;
                    System.out.println("PASS " + name);
                } catch (Throwable t) {
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    failures.add(name + ": " + cause);
                    System.out.println("FAIL " + name + "\n     " + cause);
                    if (!(cause instanceof AssertionError)) {
                        cause.printStackTrace(System.out);
                    }
                }
            }
        }
        System.out.println("\n" + passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("  " + f));
            System.exit(1);
        }
    }
}
