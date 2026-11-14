---
layout: default
---

# Idempotency key best practices

- **The same key on every retry.** No `UUID.randomUUID()` in an Activity.
- **A natural key first,** named for the operation: `order-create-{id}`
- **No natural key?** Workflow Run ID + Activity ID.
- **In a Workflow?** Pass it in from the Client, or use `Workflow.randomUUID()`.
- **The service you call enforces it,** not the Activity.
- **Your own store?** A unique constraint before acting, a TTL after.

<!--
Sources: docs.temporal.io/activity-definition#idempotency;
temporal.io/blog/idempotency-and-durable-execution#idempotency-keys;
temporal.io/blog/error-handling-in-distributed-systems#idempotency-the-unsung-hero;
temporal.io/blog/saga-pattern-made-easy#idempotency.

Six bullets in three pairs, in the order a developer meets them: pick a key,
generate it safely, then know who enforces it.

- Same key on every retry. This is the whole point. A key minted inside the
  Activity changes per attempt, so the service sees every retry as new.
- Natural key first. If the data already has an `orderId` or `transferId`, use
  it, and make the key say what the operation is (`order-create-…`, not just
  the ID) so two different operations on one order cannot collide.
- Run ID + Activity ID is the fallback when there is no natural key. The next
  slide shows it.
- Inside a Workflow, `UUID.randomUUID()` or a timestamp breaks replay. Have the
  Client generate the key and pass it in (closest to the user, which also
  covers a double-tapped button), or use `Workflow.randomUUID()`, which draws
  from the Workflow's seeded random and replays to the same value.
  `Workflow.sideEffect` also works: it runs once and records the result in
  Event History.
- The Activity does not deduplicate anything. The receiving service checks the
  key and, on a repeat, returns the first result instead of acting again.
  Stripe's `Idempotency-Key` header is the usual example.
- If you own the receiver: insert the key into a table with a unique constraint
  *before* doing the work, in the same transaction. A duplicate-key error means
  it already happened: skip and return success. Add a timestamp and prune old
  keys, or the table grows forever.
-->

---
layout: code-stack
heading: An idempotency key that survives retries
proseMax: 30
---

::code::

```java
@Override
public String postDebit(Debit debit) {
  ActivityInfo info = Activity.getExecutionContext().getInfo();
  String idempotencyKey = info.getWorkflowRunId() + "-" + info.getActivityId();

  return ledger.post(debit, idempotencyKey);
}
```

::default::

Activities run at least once. The run ID plus the Activity ID is the same on
every retry and unique across Workflows, so the ledger can drop the repeat.

<!--
Source: docs.temporal.io/activity-definition#idempotency and
docs.temporal.io/best-practices/error-handling#idempotence.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/DebitActivitiesImpl.java,
run by AntipatternsTest.

The failure this prevents: the Worker posts the debit, the ledger commits it,
and the Worker crashes (or the response times out) before Temporal hears about
it. Temporal has no record of success, so it retries. Without a key, the
customer is debited twice. The test does exactly this: the stub ledger commits
and then throws once, the retry sends the same key, and there is one posting.

The key is enforced by the service you call, not by the Activity. Most payment
APIs accept one (Stripe's `Idempotency-Key` header is the usual example). If
yours does not, an `operations` table with a unique constraint on the key does
the job.

`getWorkflowRunId()` is the run of the Workflow that scheduled the Activity. It
is `null` only for a Standalone Activity, which no Workflow scheduled; an
Activity called from a Workflow always has it. (The older `getRunId()` returns
the same value and is deprecated.)

Do not use `UUID.randomUUID()` inside the Activity: every attempt gets a new key,
which deduplicates nothing.
-->
