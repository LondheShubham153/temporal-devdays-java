---
layout: default
---

# Best practices

- **Make all compensations idempotent.** They may run when the forward Activity never
  executed, or run multiple times on retry. Use idempotency keys for safe re-execution.
- **Register compensations before Activity Execution.** Cleanup runs even if the
  Activity fails after partial completion; the compensation no-ops if nothing happened.
- **Use idempotency keys for forward Activities.** Pass a unique identifier, such as
  a Workflow ID, so retries do not create duplicate side effects.
- **Set a Start-To-Close Timeout on compensation Activities.** Avoid
  `setScheduleToCloseTimeout` on compensations, and Workflow-level timeouts. Let compensations retry until they
  succeed.

<!--
Source: Temporal docs, Saga pattern page (Best practices).

Two minutes for both best practices slides. These are the docs page's best
practices, in its order.

Say which ones the lab code shows and which it deliberately does not:
- Shown: idempotent compensations (the `IfReserved` / `IfCharged` guards),
  register before execution, `setStartToCloseTimeout` on every
  Activity, compensations included, and compensations on their own stub with
  no attempt limit and backoff, while forward steps give up after two attempts.
- Not shown: idempotency keys (`chargePayment` fails non-retryable instead).
- Differs: the lab's undo options keep a 5 minute `setScheduleToCloseTimeout`,
  so a broken lab still finishes. Say this out loud: in production, leave it
  off, let compensations retry until they succeed, and alert a human on long
  failures. It is the line most likely to be copied into production by mistake.
-->

---
layout: default
---

# Best practices, continued

- **Keep compensation payloads small.** Pass references (IDs, URLs) instead of full
  data objects to avoid exceeding the 2 MB payload limit.
- **Log compensation failures but continue.** If a compensation fails, log the error
  and continue with the remaining compensations. In production, alert for manual
  intervention on persistent failures.
- **Re-throw the original error after compensating.** Always re-throw the original
  error after running compensations so the Workflow reports the correct failure
  reason.

<!--
Source: Temporal docs, Saga pattern page (Best practices).

In the lab code: continue is `setContinueWithError(true)` on the Saga's
options, and re-throwing is the `throw e` at the end of the catch block. The
Saga helper does not log a failed compensation: it swallows it, and the
failure is visible only as an `ActivityTaskFailed` Event in the History. If
you need the log line and the alert, write the unwind yourself: a
`Deque<Runnable>` and a loop with a try/catch around each undo. The lab passes
the whole `Order` record, a handful of small fields; pass a reference once
the input can grow.
-->

---
layout: default
---

# Common pitfalls

<div class="dense">

- **Non-idempotent compensations.** Compensations may run when the forward Activity
  never executed, or multiple times on retry. All compensations must be idempotent.
- **Forgetting to register a compensation.** If a step succeeds but its compensation
  was never registered, a later failure leaves that step's effects in place.
- **Compensations that can fail permanently.** If a compensation fails with a
  non-retryable error, the Saga cannot fully roll back. Use generous Retry Policies.
- **Large payloads in compensation state.** Passing large objects through the
  compensation chain can exceed the 2 MB payload limit. Use references instead.

</div>

<style>
.dense :is(p, li) { font-size: 1.2rem; line-height: 1.45; }
</style>

<!--
Source: Temporal docs, Saga pattern page (Common pitfalls).

One minute. The docs page's pitfalls.
-->
