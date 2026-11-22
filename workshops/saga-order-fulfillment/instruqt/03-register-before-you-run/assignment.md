---
slug: register-before-you-run
id: xcocahpxomdu
type: challenge
title: 3. Register Before You Run
teaser: The charge lands, then reports failure. The refund never runs. Register each
  undo first, on a stub that does not give up.
notes:
- type: text
  contents: |-
    # What happens when the charge lands and still reports failure?

    A payment gateway captures the money, then times out before it answers. The
    card is charged. Your code is told it failed.

    If the refund is only added to the Saga after the charge succeeds, it is
    never added at all. The Saga can only run the undos it was given. The money
    is stranded.

    And once it is added, the refund API may be down too. A refund that gives up
    after two attempts strands the money just the same.
tabs:
- id: 5xx84jpwpqru
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/saga-order-fulfillment
  port: 8080
- id: xddtkzxmmwpr
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: iogfskyyxxya
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: jhaditzxhhte
  title: Temporal UI
  type: service
  hostname: workshop
  path: /
  port: 8233
difficulty: advanced
timelimit: 600
enhanced_loading: null
---

# Goal

Register each undo with the Saga **before** the step it undoes, so a step that
lands its effect and then fails is still reversed, and register it on the
`undos` stub, so it keeps retrying until it goes through.

**Why it matters:** this is the failure that costs real money. The customer is
charged, the order fails, and nothing ever refunds them.

# See it first

Starting this Challenge stopped the Worker you ran in Challenge 2. Start a
fresh one in this Challenge's
[button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --charge-times-out
```

```bash,nocopy
Order failed: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
The Workflow ended Failed. That is expected: the order did not go through.

Event History of fulfillment-ORD-XXXXXX:
  steps completed: ReserveInventory
  step failed:     ChargePayment
  undos completed: ReleaseInventoryIfReserved
  undos still owed: RefundPaymentIfCharged
```

The Workflow ending Failed is expected: the charge failed, so the order did not
go through. The problem is the last line. The gateway captured 4999 cents
before it timed out, so the charge owes a refund, and **the refund never ran**.

The [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:17:08 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
[ledger] release:ORD-XXXXXX
```

The stock came back; the money did not. In the
[button label="Temporal UI" background="#444CE7"](tab-3) tab (click refresh if
the Workflow isn't listed), `ChargePayment` shows **Activity Task Failed**, and
the only undo after it is `ReleaseInventoryIfReserved`. There is no
`RefundPaymentIfCharged` event at all: the Workflow never knew it owed one.
(The history cannot show that the money moved either: the charge reports
Failed. The Client knows only because this scenario captures before it fails.)

The refund is added to the Saga on the line **after** the charge. When the
charge throws, that line never runs.

# Step 1 of 2: register the release first, on the undos stub (TODO 2a)

**Observe.** Open `OrderFulfillmentWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab (`Ctrl+P`, then
`Ctrl+G` to jump to a line).

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Lines 54-57 are the first step of `fulfill` and its undo:

```java
            // TODO 2a start: register the undo BEFORE the step, on the undos stub
            activities.reserveInventory(order);
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            // TODO 2a end
```

**Learn.** `saga.addCompensation(...)` does not run anything. It pushes "call
`releaseInventoryIfReserved(order)`" onto the Saga's stack, for `compensate()`
to run later. Here the step runs first and its undo is registered after. If
`reserveInventory` holds the stock and then fails, the second line never runs,
so the unwind has no release to run. Registered first, the undo is on the stack
whatever the step does.

The method reference also decides **how the undo retries**: it runs with the
options of the stub it names. `activities` gives every call two quick attempts,
which is right for a forward step: one that keeps failing should give up fast,
so the order can unwind. An undo that gives up strands the stock or the money.
So the class also has a second stub over the same five Activities, `undos`,
built from these options (lines 32-47, above `fulfill`):

```java
    // An undo that gives up strands the stock or the money, so it keeps retrying
    // with backoff: no attempt limit, 500 ms doubling to at most 10 s between
    // attempts, for up to 5 minutes in all.
    private static final ActivityOptions UNDO_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setScheduleToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofMillis(500))
                    .setBackoffCoefficient(2)
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .build())
            .build();

    // The same five Activities, called with UNDO_OPTIONS. An undo retries with the
    // options of the stub it was registered on.
    private final OrderActivities undos = Workflow.newActivityStub(OrderActivities.class, UNDO_OPTIONS);
```

No `setMaximumAttempts`: the undo retries with backoff until it succeeds or 5
minutes have passed. Nothing uses `undos` yet.

**Do.** Between the TODO 2a markers:

- [ ] Swap the two statements, so `saga.addCompensation(...)` comes first.
- [ ] In `saga.addCompensation(...)`, change `activities::` to `undos::`.

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Lines 54-57 of `solution/.../OrderFulfillmentWorkflowImpl.java`:

```java
            // TODO 2a start: register the undo BEFORE the step, on the undos stub
            saga.addCompensation(undos::releaseInventoryIfReserved, order);
            activities.reserveInventory(order);
            // TODO 2a end
```

</details>

# Step 2 of 2: register the refund first, on the undos stub (TODO 2b)

**Observe.**

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
Lines 59-62, just below, are the charge and its undo:

```java
            // TODO 2b start: same for the charge: undos stub, before the step
            activities.chargePayment(order);
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            // TODO 2b end
```

**Learn.** The same gap, and it costs more here. If `chargePayment` charges the
card and then reports failure, the refund was never registered, and the
customer pays for an order that never ships. And a refund on `activities` that
hits a refund API down for a few seconds fails twice and is skipped, with the
same result.

**Do.** Between the TODO 2b markers:

- [ ] Swap the two statements.
- [ ] Change `activities::` to `undos::`.

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
Lines 59-62:

```java
            // TODO 2b start: same for the charge: undos stub, before the step
            saga.addCompensation(undos::refundPaymentIfCharged, order);
            activities.chargePayment(order);
            // TODO 2b end
```

</details>

Leave TODO 3 in `OrderActivitiesImpl.java` alone; it is the next Challenge.

# Run it

Save, and the Worker you started in this Challenge restarts on its own: its tab
prints `Fulfillment Worker polling fulfillment-tasks` again. In the
[button label="Client" background="#444CE7"](tab-2) tab, run the same order
again:

```bash,run
run-starter --charge-times-out
```

```bash,nocopy
Order failed: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
The Workflow ended Failed. That is expected: the order did not go through.

Event History of fulfillment-ORD-XXXXXX:
  steps completed: ReserveInventory
  step failed:     ChargePayment
  undos completed: RefundPaymentIfCharged, ReleaseInventoryIfReserved
  undos still owed: none
```

The first line is the same as before the fix, word for word: the charge still
fails, and the Workflow still ends **Failed**, as it should. The difference is
below it: the refund completed, newest first, and nothing is still owed. The
[button label="Worker" background="#444CE7"](tab-1) tab now ends with
`[ledger] refund:ORD-XXXXXX` and then `[ledger] release:ORD-XXXXXX`.

The newest Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab is the proof:

| Event | Activity |
|---|---|
| **Activity Task Completed** | `ReserveInventory` |
| **Activity Task Failed** | `ChargePayment` |
| **Activity Task Completed** | `RefundPaymentIfCharged` |
| **Activity Task Completed** | `ReleaseInventoryIfReserved` |
| **Workflow Execution Failed** | |

# Checkpoint

Click **Check**. It passes when a charge that lands and then fails is refunded,
a reservation that lands and then fails is released, and a refund that fails
twice is retried until it goes through while the failing courier step still
gives up after two attempts. If it fails, the message names TODO 2a or 2b.

---

**Please share your feedback so we can make better content for you.** The
**Feedback** tab takes a few seconds, and it is the only way we find out which
parts of this landed.
