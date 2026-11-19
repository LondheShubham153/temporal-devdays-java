---
layout: section
---

<ZiggyHero scene="dodont">

# Patterns and Anti-Patterns

The shapes that hold up in production, and the ones that page you at night.

</ZiggyHero>

<!--
Source: docs.temporal.io/design-patterns, plus the limits in
docs.temporal.io/evaluate/cloud/limits.

A bank runs through the whole section, the same one as the Signals, Queries and
Updates segments: transfers, wires, deposits, accounts. Every pattern here is a
fix for a specific way a payment goes wrong.

Pacing: three pattern slides. Resumable Activity and its demo come earlier,
right after Updates, since the pattern is built from a Signal and a Query.
-->

---
layout: code-stack
heading: The business ID is the Workflow ID
proseMax: 30
---

::code::

```java
// ❌ A random ID: a double-tapped "Send" starts two transfers
TransferWorkflow random = client.newWorkflowStub(
    TransferWorkflow.class,
    WorkflowOptions.newBuilder()
        .setTaskQueue("transfers")
        .setWorkflowId(UUID.randomUUID().toString())
        .build());
WorkflowClient.start(random::transfer, transfer);

// ✅ The transfer's own ID: the second start is rejected
TransferWorkflow byId = client.newWorkflowStub(
    TransferWorkflow.class,
    WorkflowOptions.newBuilder()
        .setTaskQueue("transfers")
        .setWorkflowId("transfer-" + transfer.transferId())
        .build());
WorkflowClient.start(byId::transfer, transfer);
```

::default::

Two running Workflows can never share an ID. Make the ID the thing that must not
happen twice, and the second start throws `WorkflowExecutionAlreadyStarted`.

<!--
Source: docs.temporal.io/design-patterns/entity-workflow#best-practices and
temporal.io/blog/idempotency-and-durable-execution#idempotent-workflows.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/TransferStarts.java,
run by AntipatternsTest, which starts each version twice.

The ❌ version is the default most people write first, because the samples use
a UUID. A mobile app retries on a flaky connection, a user double-taps, a
load balancer replays a request: each one is a second transfer.

The ✅ version uses the ID the transfer already has. The blog's advice is to
generate that key as close to the user as possible: the app creates the
transfer ID when the user opens the form, not when the backend receives it.

Two policies to know by name, without reading them out:
- `setWorkflowIdConflictPolicy` is about a *running* Workflow with the same ID.
  The default fails the start; `WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING`
  attaches to the running one instead, which is what you want for "start it if
  it isn't already going".
- `setWorkflowIdReusePolicy` is about a *closed* one. The default,
  `ALLOW_DUPLICATE`, allows a new run with the same ID once the first has
  finished. For a transfer that must never run twice, set
  `WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE`; it holds for the Namespace's
  retention period.

Bonus: the transfer is now findable by its own ID in the Web UI and the CLI.
-->

---
layout: code-stack
heading: Polling an external system
proseMax: 30
---

::code::

```java
// ❌ Every pass adds an Activity and a Timer to history
String status = wires.checkSettlement(wireId);
while (!status.equals("SETTLED")) {
  Workflow.sleep(Duration.ofMinutes(1));
  status = wires.checkSettlement(wireId);
}

// ✅ One Activity. The Retry Policy does the waiting
WireActivities wires = Workflow.newActivityStub(
    WireActivities.class,
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(10))
        .setRetryOptions(RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofMinutes(1))
            .setBackoffCoefficient(1)
            .build())
        .build());
wires.awaitSettlement(wireId);
```

::default::

`awaitSettlement` checks once and throws if the wire hasn't settled. Retries are
not recorded in history, so it can poll for days and history stays one Activity.

<!--
Source: docs.temporal.io/design-patterns/polling#infrequent-polling-resource-efficient.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/LoopPollingWorkflowImpl.java
and RetryPollingWorkflowImpl.java, run by AntipatternsTest: four checks leave
four Activities and three Timers in the ❌ history, and one Activity in the ✅
one. In the ❌ half, `wires` is the same Activity stub, built once as a field.

The scenario: a wire has gone out on a payment network that has no webhook, and
the Workflow must wait for it to settle.

`setBackoffCoefficient(1)` is what turns retries into a fixed poll interval:
every wait is the Initial Interval. The Activity throws
`ApplicationFailure.newFailure(...)`, a retryable failure, when the wire is
still pending.

The docs split polling three ways, and it is worth naming the other two:
- Every second or faster: loop *inside* one Activity, heartbeating each pass.
- Every minute or slower: this slide.
- Several steps per poll, or parameters that change between polls: a Child
  Workflow that loops with `Workflow.sleep` and calls Continue-As-New.

If the network can call you back, skip polling: complete the Activity
asynchronously from the webhook.
-->

---
layout: code-stack
heading: Sleeping in an Activity
proseMax: 30
---

::code::

```java
// ❌ An Activity that waits out a three-day hold
@Override
public void releaseAfterHold(Deposit deposit) throws InterruptedException {
  Thread.sleep(Duration.ofDays(3));
  bank.release(deposit.depositId());
}

// ✅ A durable Timer in the Workflow, then a quick Activity
@Override
public void hold(Deposit deposit) {
  Workflow.sleep(Duration.ofDays(3));
  activities.releaseFunds(deposit.depositId());
}
```

::default::

The ❌ Activity holds a Worker slot for three days and starts over if the Worker
restarts. `Workflow.sleep` is a Timer on the Server: no slot, no restart.

<!--
Source: the Temporal docs on durable Timers in Java (`Workflow.sleep`).
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/DepositActivitiesImpl.java
(❌) and DepositHoldWorkflowImpl.java (✅), run by AntipatternsTest on the
time-skipping test server: one Timer of exactly three days, then the release.

The scenario: a deposited cheque is held for three business days before the
funds are released.

Everything wrong with the ❌ version, in the order people discover it:
- It needs a Start-To-Close Timeout longer than three days.
- If the Worker restarts on day two, nothing notices until that timeout
  expires, and then the retry waits another three days from zero. A Heartbeat
  Timeout would notice sooner, but the retry still starts the wait over.
- It occupies an Activity slot, and a thread, the whole time, so a Worker can
  only hold as many deposits as it has slots.

The ✅ version costs two Events (TimerStarted, TimerFired), survives any number
of Worker restarts, and holds a million deposits as easily as one.
-->
