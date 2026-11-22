---
layout: eyebrow-hero
eyebrow: IMPLEMENTATION
---

# The order fulfillment Workflow

Register a compensation before or after each step, and run all compensations in
**reverse order** on failure.

<!--
Source: Temporal docs, Saga pattern page (Implementation).

Twenty seconds. The docs page shows a compact version of this Workflow. The
lab's version is the same shape with one thing the docs' own best practices ask
for: a failed compensation must not stop the ones after it. The Java SDK ships
a Saga helper, `io.temporal.workflow.Saga`, and the lab uses it:
`addCompensation` pushes one undo, `compensate()` runs them newest first. The
slide's version catches only `ActivityFailure`; the code slide says why.

The other way to write it: no helper, just a `Deque<Runnable>` in the Workflow,
`push` each undo, and a loop with its own try/catch that logs each failed
compensation. Same behaviour, more code, and you get to log every failure
yourself. The Saga helper swallows a failed compensation silently when
`continueWithError` is on; the failure still shows in the Event History as an
`ActivityTaskFailed` Event.
-->

---
layout: code-walkthrough
heading: Implementation
notes:
  - "The Workflow: an order in, a result out"
  - "A list of undos, kept in Workflow state. Replay rebuilds it after a crash"
  - "Everything that can fail goes inside the <code>try</code>"
  - "Register the undo <strong>before</strong> the step, at the front of the list. Nothing runs yet"
  - "Step 1 runs as an Activity. Its undo is already on record"
  - "Same pair for the charge, so a charge that lands and then fails is still refunded"
  - 'Last step, no undo needed. No courier fails with <code class="ref">CourierUnavailable</code>'
  - "Happy path: return the tracking number. The undo list is simply dropped"
  - "Compensate <strong>Activity failures</strong> only. A bug in Workflow code retries instead of refunding"
  - "Log it. The Workflow logger is replay-aware"
  - "Walk the list front to back: newest first, so refund, then release"
  - "Each undo is itself an Activity, with its own timeouts and retries"
  - "Each undo has its own <code>catch</code>. One failure is logged, the rest still run"
  - "Rethrow the original failure. <strong>Compensating is not succeeding</strong>"
---

::code::

```txt {all|1|2|4|5|6|8-9|11|12|14|15|17|19|18-21|23}
workflow fulfill(order):
  undos = []

  try:
    undos.addToFront(releaseInventory)
    reserveInventory(order)

    undos.addToFront(refundPayment)
    chargePayment(order)

    trackingNumber = dispatchCourier(order)
    return trackingNumber

  catch activityFailure:
    log("fulfillment failed, unwinding")

    for each undo in undos:
      try:
        undo(order)
      catch error:
        log("compensation failed"), keep going

    rethrow activityFailure
```

<!--
Pseudocode of workshops/saga-order-fulfillment/solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java.
The next slide is the same Workflow in real Java; this one is the shape,
without the syntax.

Five minutes, one click per note. Out of frame, as fields of the class: two
`Workflow.newActivityStub(OrderActivities.class, ...)` stubs over the same five
Activities. `activities`, for the forward steps, has a 10 second Start-To-Close
Timeout and two attempts: a step that keeps failing gives up fast so the order
can unwind. `undos`, for the compensations, has no attempt limit and backs off
from 500 ms to at most 10 s, for up to 5 minutes: an undo that gives up strands
the stock or the money. The logger is
`Workflow.getLogger(OrderFulfillmentWorkflowImpl.class)`. The SDK does not know
which of the five Activities is an undo; the stub you register it on decides how
it retries.

The walk is in execution order: forward through the `try`, then the `catch` and
the unwind. The builds below name the Java each pseudocode line becomes on the
next slide. The Java SDK ships a Saga helper, `io.temporal.workflow.Saga`, that
is exactly this list: `undos` is the `Saga`, `addToFront` is
`saga.addCompensation`, each step is a call on the Activity stub, `catch
activityFailure` is `catch (ActivityFailure e)`, and the whole loop, its inner
try/catch included, is `saga.compensate()` with `setContinueWithError(true)`.

The bubbles are the short version. The full explanation for each build:

- **Build 1 - Line 1: a Workflow definition.** `OrderFulfillmentWorkflow` is a `@WorkflowInterface` with one `@WorkflowMethod`, `fulfill`; the class implements it. The Worker registers the class, and a client starts it on the fulfillment-tasks Task Queue. It takes an Order and returns an OrderResult, both records; Temporal serializes them as JSON.

- **Build 2 - Line 2: the compensation list.** In Java, a `Saga`: a plain object, a list of undos, kept in Workflow state. If the Worker crashes, replay rebuilds it, so the Workflow still knows what it owes. Its options come back on Build 13.

- **Build 3 - Line 4: the failure boundary.** Every step that can fail goes inside the try. A failing Activity lands in the catch below, which is where the unwind starts.

- **Build 4 - Line 5: register before the step.** `saga.addCompensation(undos::releaseInventoryIfReserved, order)` takes a method reference on the `undos` Activity stub and its argument, and stores them. Nothing runs yet. When it does run, it retries with the `undos` stub's options.

- **Build 5 - Line 6: step 1 runs.** Calling a method on the Activity stub schedules reserveInventory on the Task Queue and blocks the Workflow until its result comes back. The reservation lands in the inventory system, outside the Workflow. Its undo is already on record.

- **Build 6 - Lines 8-9: the same pair for the charge.** Register refundPaymentIfCharged, then charge. Registering first means a charge that lands and then reports failure is still refunded.

- **Build 7 - Line 11: step 3 has no compensation.** dispatchCourier is last, so no later step can fail and leave it standing. When there is no courier, it throws a non-retryable ApplicationFailure of type CourierUnavailable, and the Workflow sees it as an ActivityFailure whose cause is that ApplicationFailure.

- **Build 8 - Line 12: the happy path.** If dispatch succeeds, the Workflow returns the tracking number. The Saga is simply dropped: nothing is undone.

- **Build 9 - Line 14: compensate Activity failures only.** In Java, `catch (ActivityFailure e)`. An ActivityFailure is the business failure a Saga compensates. Anything else, such as a NullPointerException from a bug in Workflow code, is not caught, so not a single undo is scheduled. An exception that is not a Temporal failure fails the Workflow Task, not the Workflow, so Temporal retries the Task and you can fix and redeploy without refunding anyone. One thing this slide does not handle: if someone cancels the Workflow, the Activity in flight fails with an ActivityFailure whose cause is a CanceledFailure, so this catch does run, but every undo it schedules is in the cancelled scope and is cancelled at once (and `continueWithError` hides that). The Workshop runs the unwind in `Workflow.newDetachedCancellationScope`, so the undos still run.

- **Build 10 - Line 15: log the failure.** The logger from `Workflow.getLogger` is replay-aware: it does not repeat this line when the Workflow replays its history. `Shared.rootCause` digs out the Activity's own failure, here CourierUnavailable.

- **Build 11 - Line 17: newest first.** Because each undo went on the front, walking the list runs refundPaymentIfCharged, then releaseInventoryIfReserved. The undo order mirrors the forward order. `saga.compensate()` does this walk for you. By default it runs one undo at a time; `setParallelCompensation(true)` would start them all at once.

- **Build 12 - Line 19: run one compensation.** Each undo is an Activity call on the `undos` stub, so it gets its own timeouts and a retry policy that does not give up after two attempts.

- **Build 13 - Lines 18-21: one failure must not strand the rest.** Each undo has its own try/catch. If one fails after its retries, the unwind moves on to the next. In Java that is `setContinueWithError(true)` on the Saga's options; the default is false, which stops the unwind at the first failed undo. The Saga does not log the failure for you; it shows in the Event History as an `ActivityTaskFailed` Event. In production, this is where you alert a human.

- **Build 14 - Line 23: re-throw.** `throw e` re-throws the original ActivityFailure, a Temporal failure, so the Workflow Execution ends Failed and the caller learns the order did not go through. Compensating is not succeeding.
-->

---
layout: default
---

# Implementation: the unwind

<div class="uw">
<div class="uw-code">

```java {all|1-4|7|8|10|11|13|15-18|18|18|19}
@Override
public OrderResult fulfill(Order order) {
  Saga saga =
      new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

  try {
    saga.addCompensation(undos::releaseInventoryIfReserved, order);
    activities.reserveInventory(order);

    saga.addCompensation(undos::refundPaymentIfCharged, order);
    activities.chargePayment(order);

    String trackingNumber = activities.dispatchCourier(order);
    return new OrderResult(order.orderId(), trackingNumber);
  } catch (ActivityFailure e) {
    log.warn("fulfillment failed for {}, unwinding: {}",
        order.orderId(), Shared.rootCause(e));
    saga.compensate();
    throw e;
  }
}
```

</div>
<SagaUnwind />
</div>

<style>
h1 { font-size: 1.8rem !important; margin-bottom: 0.5rem !important; }
.uw { display: grid; grid-template-columns: minmax(0, 1fr) 36%; gap: 1rem; height: 26.5rem; }
.uw-code { min-width: 0; overflow: hidden; }
.uw-code {
  --slidev-code-font-size: 0.6rem;
  --slidev-code-line-height: 0.86rem;
}
.uw-code :deep(pre.shiki) { margin: 0; padding: 0.55rem 0.7rem; }
.uw-code :deep(pre code), .uw-code :deep(pre .line) { font-size: 0.6rem !important; line-height: 0.86rem !important; }
</style>

<!--
Source: workshops/saga-order-fulfillment/solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java,
with the Workshop's `newSaga()` and `unwind()` helpers inlined (the Workshop
keeps them separate so TODO 1 has one place to edit), and
components/SagaUnwind.vue.
Code: snippets/src/main/java/io/temporal/devdays/essentials/saga/OrderFulfillmentWorkflowImpl.java

Three minutes, ten clicks, one order whose courier fails. This is the previous
slide's pseudocode as real Java: the undo list is a `Saga`, and its loop is
`saga.compensate()`. This time watch the right-hand side.

- **Stack.** Each `addCompensation` puts an undo on top. Nothing on the stack
  runs until the courier fails. Then `compensate()` starts from the top: the
  refund, registered last, runs first. The Saga does not actually remove
  anything from its list; the picture shows what is still owed.
- **Event History.** This is what the Temporal UI shows for the same run, in
  order: the forward Activities and the failed `dispatchCourier` come first,
  the two compensation Activities come second, and `WorkflowExecutionFailed`
  closes it. Workflow Task Events are left out, as the UI's compact view does.

Open a real failed run in the Temporal UI after this slide, or in the lab, and
point at the same order of Events. The Workflow Type in the UI is
`OrderFulfillmentWorkflow`, the interface's name.
-->
