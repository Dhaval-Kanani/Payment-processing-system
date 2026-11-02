# Payment Processing Event System — NOTES

## 1. Functional requirements
1. **FR1 Ingest** — `PaymentEventConsumer.onMessage(PaymentEvent)` receives an event and returns a decision: `ACK` (processed or ignored) or `NACK` (redeliver).
2. **FR2 Load state** — processor loads `PaymentState` by `paymentId`; if absent, treat as new with `lastProcessedSequence = 0` and no status.
3. **FR3 Duplicate / stale** — `event.sequenceNumber <= lastProcessedSequence` → ignore, ACK, no state change.
4. **FR4 In-order apply** — `event.sequenceNumber == lastProcessedSequence + 1` → validate state transition, apply atomically (status + `lastProcessedSequence` updated together), ACK.
5. **FR5 Future event** — `event.sequenceNumber > lastProcessedSequence + 1` → publish to delayed retry topic, **then** ACK. Ack must not happen if publish fails (→ NACK).
6. **FR6 Retry loop** — a retried event is re-fed through the same consumer logic (FR2–FR5). Attempt count per `eventId` is kept in an in-memory store.
7. **FR7 Retry exhaustion** — when attempts for an `eventId` exceed the retry limit (3), publish to DLQ topic, then ACK.
8. **FR8 State machine** — allowed transitions:
   - `NEW → AUTHORIZED` (payment.authorized)
   - `AUTHORIZED → CAPTURED` (payment.captured)
   - `CAPTURED → REFUNDED` (payment.refund)
   - `NEW | AUTHORIZED → FAILED` (payment.failed)
   - `FAILED`, `REFUNDED` are terminal.
9. **FR9 First event must be `payment.authorized`** — if the first in-sequence event for a new payment is not `payment.authorized`, publish to retry topic then ACK (do not apply, do not silently ignore).
10. **FR10 Invalid transition (non-first event)** — in-sequence event whose type is not allowed from current status → publish to retry topic then ACK (same path as FR9); exhausts to DLQ like any retry.
11. **FR11 Apply failure** — if applying the event throws (repository/downstream mock error) → NACK (broker redelivers); no partial state change.
12. **FR12 Publish failure** — if retry or DLQ publish throws → NACK.
13. **FR13 Downstream** — downstream notification is a mockable interface invoked inside the apply step.

## 2. Non-functional requirements
- **Thread-safety:** per-payment lock (`ConcurrentHashMap<String, ReentrantLock>`), so FR2–FR4 for one payment is serialized; different payments process in parallel.
- **Atomicity:** status + `lastProcessedSequence` written as one immutable `PaymentState` replacement under the lock.
- **At-least-once:** ACK only after side effects (apply or publish) succeed.
- **Extensibility:** repository and publishers behind interfaces so Kafka/DB impls can be swapped.
- **Testability:** no static mutable state; constructor injection.

## 3. Constraints & assumptions (user's, verbatim or near)
- `sequence_number` is added to the payload (Q1: "Yes").
- First event: `lastProcessedSequence` assumed 0.
- Retry attempt count stored in in-memory data.
- Retry limit: 3.
- Single process, no libraries/frameworks. "publish to retry topic" is enough; demo/tests re-feed the message manually.
- Per-payment lock for concurrency.
- Apply failure → redeliver (NACK). Publish failure → NACK.
- Build: plain `javac` + hand-rolled test runner (no Maven/JUnit).
- Downstream service operations mocked where required.

## 4. Out of scope
- Signature validation, amount rounding, currency conversion.
- REST API / controller, real broker (Kafka), real delay scheduling.
- Persistence across restarts.
- Distributed locking.

## 5. Edge cases (to be tested)
- null event / null or blank `eventId` / `paymentId` / sequence ≤ 0 / unknown type → `ValidationException` thrown out of the consumer (bad request; neither ACK nor NACK).
- Unknown event type string.
- First event for a payment with sequence 1 (`NEW → AUTHORIZED`).
- First event for a payment with sequence 1 but type `captured` → retry topic (FR9).
- Duplicate delivery of already-processed event (same seq) → ACK, no change.
- Out-of-order: seq 3 arrives before seq 2 → retry; then seq 2 → applied; re-fed seq 3 → applied.
- Retry exhaustion: future event re-fed 4 times → DLQ on the 4th.
- Attempt counter reset/cleanup after successful apply or DLQ.
- Event on terminal state (`FAILED`/`REFUNDED`) with valid sequence.
- Concurrent delivery of seq 1 and seq 2 for the same payment from two threads → final state consistent, both eventually applied or one retried.
- Concurrent duplicate deliveries of the same event → applied exactly once.
- Publisher throws → NACK, state unchanged, attempt count not incremented.
- Apply throws (downstream mock fails) → NACK, `lastProcessedSequence` unchanged.

## 6. Open questions
- (none open)

## 7. Decisions log
- Steps 2–8 implemented; 58 tests green via `./test.sh` (run 3x for the concurrency suite). Demo: `./run.sh`.
- Old `src/Main.java` (default package, problem statement only) deleted; demo lives in `src/payment/Main.java`.
- Retry/DLQ logic was written in step 4 together with the processor (no throwaway stub); step 5 added its tests.
- Concurrent seq 1 and seq 2 for one payment: whichever wins the lock goes first; if seq 2 wins it is retried and applies on re-feed (test asserts both orders).
- Q1: retry limit 3 = 3 retries; DLQ on the 4th delivery of the same `eventId`.
- Q2: in-sequence invalid transition → retry topic (FR10).
- Q3: malformed event → throw `ValidationException` (bad request) from the consumer.
- Q4: failed publish does **not** increment the attempt counter (increment only after successful retry publish).
- Q5: transition rules live in `PaymentStateService`.

## 8. Possible extensions (not implemented)
- Real delayed scheduling with injected `Clock`.
- Idempotency store keyed by `eventId` (not needed: sequence comparison covers duplicates).
- Kafka / DB implementations of the interfaces.

---

# Low-Level Design

## 1. Entities / models (`model`)
| Class | Fields | Notes |
|---|---|---|
| `EventType` (enum) | `AUTHORIZED("payment.authorized")`, `CAPTURED("payment.captured")`, `REFUND("payment.refund")`, `FAILED("payment.failed")` | `static EventType fromWire(String)` throws `ValidationException` |
| `PaymentEvent` (immutable, Builder) | `String eventId`, `String paymentId`, `long sequenceNumber`, `EventType eventType`, `long amount` (minor units), `String currency`, `Instant createdAt`, `String signature` | `equals/hashCode` on `eventId` |
| `PaymentStatus` (enum) | `NEW, AUTHORIZED, CAPTURED, REFUNDED, FAILED` | `NEW` = no event applied yet |
| `PaymentState` (immutable) | `String paymentId`, `PaymentStatus status`, `long lastProcessedSequence` | `static initial(paymentId)` → `NEW, 0`; `withApplied(status, seq)` returns new instance |
| `ProcessingOutcome` (enum) | `APPLIED, IGNORED_DUPLICATE, RETRY_SCHEDULED, DEAD_LETTERED` | returned by `EventProcessor` |
| `AckDecision` (enum) | `ACK, NACK` | |
| `ConsumeResult` (record) | `AckDecision ack`, `ProcessingOutcome outcome` (null on NACK), `String error` (null on ACK) | returned by consumer; readable in demo/tests |

## 2. Interfaces
```java
// repository
interface PaymentStateRepository {
    Optional<PaymentState> findByPaymentId(String paymentId);
    void save(PaymentState state);
}
interface RetryAttemptStore {            // in-memory retry counter per eventId
    int get(String eventId);             // 0 if absent
    int increment(String eventId);       // returns new value
    void clear(String eventId);
}
// publisher
interface RetryPublisher      { void publish(PaymentEvent event, int attempt); }
interface DeadLetterPublisher { void publish(PaymentEvent event, String reason); }
interface DownstreamNotifier  { void notify(PaymentState newState, PaymentEvent event); } // mock downstream (FR13)
// service
class EventValidator          { void validate(PaymentEvent event); }            // throws ValidationException
class PaymentStateService     { boolean canApply(PaymentState, PaymentEvent);
                                PaymentState apply(PaymentState, PaymentEvent); } // throws InvalidTransitionException
class EventProcessor          { ProcessingOutcome process(PaymentEvent event); }
class PaymentEventConsumer    { ConsumeResult onMessage(PaymentEvent event); }
```
Exceptions (`exception`): `ValidationException`, `InvalidTransitionException`, `PublishException` (all unchecked).

## 3. Schema (in-memory)
| Store | Structure | Why |
|---|---|---|
| `InMemoryPaymentStateRepository` | `ConcurrentHashMap<String, PaymentState>` keyed by `paymentId` | single `put` of an immutable object = atomic replace |
| `InMemoryRetryAttemptStore` | `ConcurrentHashMap<String, Integer>` keyed by `eventId`; `merge(id, 1, Integer::sum)` | atomic increment |
| `InMemoryRetryPublisher` | `ConcurrentLinkedQueue<PaymentEvent>` + `List<PaymentEvent> drain()` | tests/demo re-feed manually |
| `InMemoryDeadLetterPublisher` | `CopyOnWriteArrayList<DeadLetter(event, reason)>` | inspectable in tests |
| `EventProcessor` locks | `ConcurrentHashMap<String, ReentrantLock>` keyed by `paymentId`, `computeIfAbsent` | per-payment lock (your choice) |

DB equivalent (if asked): `payment_state(payment_id PK, status, last_processed_sequence)`; `retry_attempt(event_id PK, attempts)`.

## 4. Design patterns
- **Repository** — `PaymentStateRepository` isolates storage; DB impl swappable.
- **Builder** — `PaymentEvent` has 8 fields.
- **Immutable value objects** — `PaymentState` replaced wholesale → atomic update with one `put`.
- **State machine (enum + allowed-transition map)** inside `PaymentStateService` — enforces FR8.
- **Constructor injection** throughout — mocks/fakes in tests, no static state.
- No Strategy/Observer: no requirement calls for them.

## 5. Class relationships
```
PaymentEventConsumer ──uses──> EventValidator
        │
        └──uses──> EventProcessor ──uses──> PaymentStateRepository <|-- InMemoryPaymentStateRepository
                        │        ──uses──> RetryAttemptStore       <|-- InMemoryRetryAttemptStore
                        │        ──uses──> RetryPublisher          <|-- InMemoryRetryPublisher
                        │        ──uses──> DeadLetterPublisher     <|-- InMemoryDeadLetterPublisher
                        └──uses──> PaymentStateService ──uses──> PaymentStateRepository
                                                        ──uses──> DownstreamNotifier <|-- LoggingDownstreamNotifier (mock)
PaymentEvent ──> EventType        PaymentState ──> PaymentStatus
```

## 6. Key flows
**Consume**
1. `consumer.onMessage(e)` → `validator.validate(e)`; `ValidationException` propagates (bad request).
2. `processor.process(e)` inside `try`; any other `RuntimeException` → `ConsumeResult(NACK, null, msg)`.
3. Otherwise `ConsumeResult(ACK, outcome, null)`.

**EventProcessor.process(e)** (whole body under `locks[paymentId]`)
1. `state = repo.findByPaymentId(id).orElse(PaymentState.initial(id))`.
2. `seq <= last` → `attempts.clear(eventId)`; return `IGNORED_DUPLICATE`.
3. `seq == last + 1`:
   - `!service.canApply(state, e)` (covers FR9 first-not-authorized and FR10) → `retryOrDeadLetter(e, "invalid transition …")`.
   - else `service.apply(state, e)`; `attempts.clear(eventId)`; return `APPLIED`. Exceptions propagate → NACK.
4. `seq > last + 1` → `retryOrDeadLetter(e, "future sequence")`.

**retryOrDeadLetter(e, reason)**
1. `n = attempts.get(eventId)`; if `n >= maxRetries(3)` → `dlq.publish(e, reason)`; `attempts.clear`; return `DEAD_LETTERED`.
2. else `retry.publish(e, n + 1)` **then** `attempts.increment(eventId)`; return `RETRY_SCHEDULED`. Publish throws → nothing incremented → NACK.

**PaymentStateService.apply(state, e)**
1. `canApply` check → else `InvalidTransitionException`.
2. `next = state.withApplied(statusFor(e.type), e.seq)`.
3. `downstream.notify(next, e)` **then** `repo.save(next)`. Downstream throws → nothing saved → NACK (redelivery; downstream is at-least-once by NFR).

## 7. Build / layout (no Maven)
```
src/payment/{model,repository,repository/inmemory,publisher,publisher/inmemory,service,exception}/…
src/payment/Main.java
test/payment/…Test.java + test/payment/TestRunner.java   (hand-rolled: reflection over public methods named test*)
build.sh  → javac -d out/production/test $(find src -name '*.java')
test.sh   → javac -d out/test -cp out/production/test $(find test -name '*.java') && java -cp out/production/test:out/test payment.TestRunner
run.sh    → ./build.sh && java -cp out/production/test payment.Main   (demo: 7 scenarios)
```
