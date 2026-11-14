---
layout: comparison
leftEyebrow: RECOMMENDED FOR SAFETY
leftTitle: Register before Activity Execution
leftFeatures:
  - "The compensation runs even if the Activity fails after partial completion"
  - "Example: a card is charged, but the Activity fails before returning success"
  - "The compensation must be idempotent and no-op when the forward Activity never executed"
  - "The safer default when side effects may occur before failure"
rightEyebrow: APPROPRIATE WHEN SAFE
rightTitle: Register after Activity Execution
rightFeatures:
  - "Only compensates Activities that completed successfully"
  - "Simpler: no need to check whether the forward action occurred"
  - "Appropriate when Activities are truly atomic (all-or-nothing)"
  - "Risk: partial completion without compensation"
---

The choice depends on your Activity's failure characteristics.

::right::

When in doubt, register **before** execution and make compensations idempotent.

<!--
Source: Temporal docs, Saga pattern page (When to register compensations).

Two minutes.

Ask the room which of the lab's steps is truly atomic. Neither. `chargePayment`
is the docs' own example: picture a charge that is recorded, and then the Activity
throws. Registered after, `addCompensation` for the refund is never reached and
the charge is stranded. Registered before, the refund runs, and its guard makes it
safe.

Registering after has one legitimate use: when the undo needs what the step
returned, such as a payment confirmation the refund must quote:
`saga.addCompensation(activities::refundPayment, order, confirmation)` can only
be written after `processPayment` returns `confirmation`. Then the undo cannot
exist before the step, and the step had better be atomic.
Code: snippets/src/main/java/io/temporal/devdays/essentials/saga/TitleTransfer.java
(no slide shows it; SagaTest runs it).
-->

---
layout: code-walkthrough
heading: A compensation Activity
notes:
  - "An undo is an ordinary <strong>Activity</strong>: it calls the payment API to reverse the charge"
  - "An <strong>idempotency key</strong>, because the undo will be retried. Two calls, one refund"
  - "The forward step"
  - "It gets a <strong>deadline</strong>: the customer is waiting, so after 2 minutes we give up and roll back"
  - "The undo"
  - "No deadline, no attempt cap, <strong>no Retry Policy at all</strong>: the defaults retry forever, with backoff"
---

::code::

```java {all|2-6|4|9-15|14|17-22|20-22}
// PaymentActivitiesImpl.java
@Override
public void refundPayment(Order order) {
  String idempotencyKey = "refund-" + order.orderId();
  payments.refund(order.orderId(), idempotencyKey);
}

// ChargeThenRefundWorkflowImpl.java
private final PaymentActivities charges =
    Workflow.newActivityStub(
        PaymentActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setScheduleToCloseTimeout(Duration.ofMinutes(2))
            .build());

private final PaymentActivities refunds =
    Workflow.newActivityStub(
        PaymentActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .build());
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/sagacompensation/PaymentActivitiesImpl.java
and ChargeThenRefundWorkflowImpl.java, run by SagaCompensationTest: the refund
endpoint is down for three attempts, the fourth succeeds, and the customer is
refunded once. Two stubs over one `@ActivityInterface`, because each gets its
own `ActivityOptions`: `charges` for the forward step, `refunds` for the undo.

The question this answers, because someone always asks it: "if the API is
down, wouldn't the compensation fail too?" Yes, at first, and that is fine.

- **Build 1 - the Activity.** A compensation is not special to Temporal. It is
  an Activity like any other, that happens to undo something.
- **Build 2 - the key.** The undo will be retried, maybe many times, and an
  attempt can succeed at the payment API and still time out on the way back.
  The idempotency key makes the repeat harmless.
- **Builds 3 and 4 - the forward step has a deadline.**
  `setScheduleToCloseTimeout` bounds every attempt and retry together. The
  customer is waiting, so after two minutes the charge gives up, and the Saga
  rolls back.
- **Builds 5 and 6 - the undo has none.** No `setScheduleToCloseTimeout`, and
  no `setRetryOptions` at all: the default Retry Policy has unlimited attempts,
  so Temporal keeps retrying with backoff (1 second, doubling, capped at 100
  seconds between attempts) whether the API is back in ten minutes or two days.
  To hit a struggling API less often, raise `setMaximumInterval`; that spaces
  the attempts out without ever giving up. The Workflow waits durably; nothing
  is lost. A bill collector who never gets tired: the API can hide, but not
  forever.

Why the asymmetry: giving up on a forward step costs you a sale. Giving up on
an undo leaves a ghost charge on a customer's card, which is worse than being
slow.

If the API is gone for good (the vendor went out of business), retrying will
never succeed, and that is where a human comes in. Alert on attempts that have
been failing for hours; the Web UI shows the attempt count and the last error
on the pending Activity. Fix the cause, or complete it by hand, and the
Workflow carries on.
-->
