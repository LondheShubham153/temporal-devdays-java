---
layout: section
---

<ZiggyHero scene="update">

# Java Updates

Synchronous write with result

</ZiggyHero>

<!--
Source: not in the original PowerPoint, which went from Queries straight to the
quiz.

Updates complete the set: Signals write, Queries read, Updates do both.

If the room has a request/response use case (an API that must tell the caller
whether the change worked), this is the segment that answers "do I need a
database next to my Workflow for that?" No.
-->

---
layout: code-walkthrough
heading: Java Updates
notes:
  - "<code>@UpdateMethod</code> declares a message the caller <strong>waits on</strong>"
  - Unlike a Signal it returns a value, and unlike a Query it can change state
  - "<code>@UpdateValidatorMethod</code> guards the Update it names"
  - "Same arguments as the Update, returns <code>void</code>. It runs before anything is written to history"
---

::code::

```java {all|6-7|7|9|10}
@WorkflowInterface
public interface AccountWorkflow {
  @WorkflowMethod
  void run(int opening);

  @UpdateMethod
  int withdraw(int amount);

  @UpdateValidatorMethod(updateName = "withdraw")
  void validateWithdraw(int amount);
}
```

<!--
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/AccountWorkflow.java,
run by UpdatesTest.

The scenario: an ATM or a payments API withdraws from an account and must tell
the customer the new balance, or refuse the withdrawal outright. A Signal
cannot answer and a Query cannot change the balance.

- **Build 1 - `@UpdateMethod`.** On the interface, like Signals and Queries.
  The method name, `withdraw`, is the Update name; `@UpdateMethod(name = "...")`
  overrides it.
- **Build 2 - the return type.** `int`: the new balance. That is the point: a
  Signal returns nothing and a Query must not change state; an Update does both.
- **Build 3 - `@UpdateValidatorMethod`.** Optional. `updateName` ties it to the
  Update it checks, by name.
- **Build 4 - the validator's shape.** The same parameters as the Update, and
  the SDK refuses one that does not return `void`. It answers one question:
  accept this Update or not.

This needed two slides where Signals and Queries needed one: the interface
carries both handlers' declarations. The next slide is the implementation.
-->

---
layout: code-walkthrough
heading: "Java Updates: the implementation"
notes:
  - "<code>@WorkflowInit</code>: the constructor sets state before any handler runs"
  - The Workflow runs until the account is empty
  - It can call Activities. The caller waits until the handler returns
  - Change state, then return the result to the caller
  - "The validator runs first, before anything is written to history"
  - Throw to <strong>reject</strong> the Update. The caller gets an error; history is untouched
---

::code::

```java {all|5-8|12|17|18-19|23|24-25}
public class AccountWorkflowImpl implements AccountWorkflow {

  private int balance;

  @WorkflowInit
  public AccountWorkflowImpl(int opening) {
    balance = opening;
  }

  @Override
  public void run(int opening) {
    Workflow.await(() -> balance == 0);
  }

  @Override
  public int withdraw(int amount) {
    activities.debitAccount(amount);
    balance -= amount;
    return balance;
  }

  @Override
  public void validateWithdraw(int amount) {
    if (amount > balance) {
      throw new IllegalArgumentException("Insufficient funds: balance is " + balance);
    }
  }
}
```

<!--
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/AccountWorkflowImpl.java,
run by UpdatesTest. `activities` is an Activity stub from
`Workflow.newActivityStub`, a field off the slide.

- **Build 1 - `@WorkflowInit`.** A constructor that takes the Workflow method's
  arguments. The SDK calls it before the Workflow method and before any Update
  or Signal handler, so the validator never sees a balance of zero just because
  the Update arrived in the same Workflow Task as the start. It is marked
  `@Experimental` in SDK 1.40.0.
- **Build 2 - the Workflow method.** It just waits. All the work happens in the
  handler.
- **Build 3 - blocking work.** An Update handler can call Activities, sleep, and
  `Workflow.await`. The caller's `account.withdraw(50)` waits until the handler
  returns, so the balance it gets back reflects a debit that actually posted.
- **Build 4 - change and answer.** The return value goes back to the caller and
  is recorded in history as `WorkflowExecutionUpdateCompleted`.
- **Build 5 - the validator.** It runs before the Update is accepted, takes the
  same arguments, and runs read-only. Calling an Activity, sleeping or awaiting
  from it fails the Workflow Task. Changing a field is not detected, and
  validators are skipped on replay, so a validator that mutates state silently
  diverges: don't.
- **Build 6 - reject.** Any exception thrown in the validator rejects the
  Update: the caller gets `WorkflowUpdateException`, and nothing is written to
  history. After acceptance it is different: throw an `ApplicationFailure` in
  the handler to fail just that Update. An `IllegalArgumentException` or a
  `NullPointerException` in the handler does not fail the Update: it fails the
  Workflow Task, which retries while the caller waits.

One gotcha worth saying, and in a bank it is the one that matters: the
validator runs when the Update arrives, but the handler calls an Activity
before it subtracts. The SDK runs one Workflow thread at a time, yet handlers
still interleave wherever one blocks: on a balance of 100, two concurrent
`withdraw(80)` calls can both pass the validator, both debit, and leave the
account at -60. The fix: serialize the handlers with a lock and re-check the
balance once you hold it. In Java that is `Workflow.newWorkflowLock()`:
`lock.lock()` at the top of the handler, `lock.unlock()` in a `finally`. It is
deterministic and safe in Workflow code; `synchronized` and the
`java.util.concurrent` locks are not
(snippets/src/main/java/io/temporal/devdays/essentials/updates/LockedAccountWorkflowImpl.java).
-->

---
layout: default
---

# Signals, Queries, and Updates

```java
int balance = account.withdraw(50);
```

<div class="three-way">

|                     | Signal              | Query          | Update                     |
| ------------------- | ------------------- | -------------- | -------------------------- |
| **Changes state**   | Yes                 | No             | Yes                        |
| **Returns a value** | No                  | Yes            | Yes                        |
| **Caller waits**    | Until it's recorded | For the answer | Until the handler finishes |
| **Can be rejected** | No                  | N/A            | Yes, by a validator        |
| **In history**      | Yes                 | No             | Yes, if accepted           |
| **Needs a Worker**  | No                  | Yes            | Yes                        |

</div>

<style>
/* table-compare splits two value columns; this table has three. */
.three-way th:not(:first-child),
.three-way td:not(:first-child) {
  width: 27%;
}
</style>

<!--
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/UpdatesClient.java,
run by UpdatesTest.

The Client line: calling the `@UpdateMethod` on the typed stub sends the Update
and waits for the result, here the balance after the withdrawal.
`WorkflowStub.fromTyped(account).startUpdate("withdraw", WorkflowUpdateStage.ACCEPTED, Integer.class, 50)`
returns a `WorkflowUpdateHandle` as soon as the Update is accepted, if the
caller wants to do something else while the handler runs; call `getResult()` on
it later. There is also Update-with-Start
(`WorkflowClient.executeUpdateWithStart` with a `WithStartWorkflowOperation`),
which starts the Workflow if it is not running yet and sends the Update in one
call: the standard shape for "open the account if needed, then make the
deposit".

Read the "Needs a Worker" row out loud. A Signal is accepted by the Server even
if every Worker is down; it waits in history. Queries and Updates need a Worker
to answer, so a caller blocked on an Update with no Workers just waits. Bound
it on your side, for example
`CompletableFuture.supplyAsync(() -> account.withdraw(50)).orTimeout(5, SECONDS)`.
Use Signals for fire-and-forget (the wire approval), Updates when the caller
needs to know (did the withdrawal go through, and what is left).

Updates have their own limits: at most 10 in flight at once per Workflow, and
2,000 per run (rejected ones do not count). Continue-As-New resets the count.
-->

---
layout: comparison
heading: "Resumable Activity (Pause On Failure)"
leftTitle: What it is
leftFeatures:
  - "An Activity's retries run out, and the Workflow parks instead of failing"
  - "It waits, using nothing, for a human to Signal in corrected input"
  - "Then it runs the same Activity again, in the same Workflow"
rightTitle: When to use it
rightFeatures:
  - "The failure is bad input someone can fix: a mistyped account number"
  - "An invalid reference, or a missing record that will be created"
  - "Restarting from scratch is worse: the customer would re-enter everything"
---

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, overview and
problem statement.

The framing that lands: retrying can't fix bad input, because the same wrong
account number fails the same way every time. The usual options are both bad:
fail the Workflow (the customer re-enters everything), or mark the error
non-retryable (same thing). What you want is for the Workflow to pause, using
nothing, until someone with authority supplies the right value, and then pick
up exactly where it stopped.

When *not* to use it: transient failures (timeouts, 503s) are the Retry
Policy's job, and failures nobody can correct should fail fast with a
non-retryable error. And it is not the Approval pattern: approval gates
progress on a decision; this recovers from a failure with corrected data.

Next slide: the room plays the on-call engineer for exactly this scenario.
-->

---
layout: default
clicks: 3
---

# Resumable Activity: you're on call

<div class="rt-slide">
<ResumableTransfer />
</div>

<style>
.rt-slide {
  height: 22rem;
}
</style>

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, and its best
practices.
Code: snippets/src/main/java/io/temporal/devdays/essentials/resumable/TransferWorkflowImpl.java
and TransferActivitiesImpl.java, run by ResumableTest; the console is
components/ResumableTransfer.vue.

Hand the room the keyboard, figuratively: "you're the engineer on call; what
do you do first?" Each click (arrow key or clicker) performs the next action,
in order. The buttons jump to an action if someone calls one out of order;
Reset goes back to the start.

The pattern in one breath: when an Activity fails because its input is wrong,
retries cannot fix it and failing the Workflow makes the customer start over.
So the Workflow catches the failure, parks on `Workflow.await`, and waits for a
Signal carrying the corrected input. Then it runs the same Activity again.

What each button teaches:
- **Send the transfer.** `executeTransfer` throws
  `ApplicationFailure.newNonRetryableFailure(..., "AccountNotFoundError")` for
  an unknown account, so there is one attempt, not the Retry Policy's three (it
  is `setMaximumAttempts(3)` for timeouts and 503s). The Workflow catches the
  `ActivityFailure` and is Running, not Failed. The Event History shows the
  Activity Type as `ExecuteTransfer`: the method name with its first letter
  capitalized, the Java SDK's default.
- **Query getStatus.** A `@QueryMethod` on the Workflow interface. The answer is `AWAITING_CORRECTION`, and the history
  does not grow. Point at that. In production, also set a Search Attribute with
  the status so operators can list every parked transfer, and alert when one
  parks rather than hoping someone opens the Web UI.
- **Signal "12345678".** `retryWithCorrection` is a `@SignalMethod`. The
  parked `Workflow.await` returns, the loop runs `executeTransfer` again, and
  the Workflow completes. Same run ID throughout: the test checks it.

Two rules worth one sentence each: catch only `ActivityFailure` (and rethrow it
when its cause is a `CanceledFailure`, so cancellation gets through), so a
Workflow bug is not parked as if it were bad data; and if a Workflow may be corrected many times,
Continue-As-New before the history grows.

This is not the Approval pattern: approval gates progress on a decision; this
recovers from a failure with corrected data. The docs' version adds an approval
Signal after the transfer, which is the Signals segment's pattern.

The live demo on the next slide is this exact scenario against a real Worker.
-->

---
layout: comparison
heading: "Resumable Activity in practice"
leftTitle: Key advantages
leftFeatures:
  - "The Workflow stays alive throughout: no restart from scratch"
  - "No external database, queue or polling mechanism"
  - "Status, corrected input and any approval are durably persisted the entire time"
rightTitle: Best practices
rightFeatures:
  - "Allow a few automatic retries before parking, for transient failures"
  - "Expose the status through a Query"
  - "Validate the corrected input in the Signal handler"
  - "Continue-As-New if many correction cycles are expected"
  - "Notify the operator (email, Slack, a ticket) when it parks"
---

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, Key advantages and
Best practices.

One minute, and every row maps to something the room just clicked through:

- **Stays alive.** The status pill said Running the whole time; one run ID from
  start to finish.
- **No database or queue.** The parked transfer, its status and the corrected
  account live in the Workflow. Nothing to provision, nothing to reconcile.
- **Durably persisted.** Kill every Worker while it is parked: the Signal is
  still accepted and the transfer resumes when a Worker comes back.

- **Retries before parking.** `setMaximumAttempts(3)` handles a timeout or a 503;
  only the non-retryable `AccountNotFoundError` parks at once.
- **Query.** That was step 2. Pair it with a Search Attribute so operators can
  list every parked transfer, not just look one up.
- **Validate in the handler.** The Workflow's handler ignores anything that is
  not 8 to 12 digits, but a Signal cannot report that back: the sender sees
  success either way. If the operator needs an error back, make the correction
  an Update with a validator.
- **Continue-As-New.** Each correction cycle adds Events; a transfer corrected
  once never needs it, a long-lived record corrected weekly does.
- **Notify.** The transition to AWAITING_CORRECTION is the place to send the
  alert, rather than hoping someone opens the Web UI.
-->

---
layout: section
---

<ZiggyHero scene="demo">

# Demo: Resumable Activity

A transfer to a mistyped account, parked, corrected, and sent.

</ZiggyHero>

<!--
The code is the previous slide's, in snippets/ (TransferWorkflowImpl.java and
TransferActivitiesImpl.java under io/temporal/devdays/essentials/resumable).
The bank knows two accounts: 10000001 and 12345678.

Terminal 1, the dev server (skip if one is running):

    temporal server start-dev

Terminal 2, the Worker, from decks/java-master/snippets:

    mvn -q compile exec:java -Dexec.mainClass=io.temporal.devdays.essentials.resumable.ResumableDemo

Terminal 3, a transfer to an account that does not exist. The Workflow Type is
the interface name, `TransferWorkflow`:

    temporal workflow start --type TransferWorkflow \
      --task-queue resumable-demo --workflow-id transfer-4471 \
      --input '{"fromAccount":"10000001","toAccount":"99999999","amount":500}'

Open the Web UI at localhost:8233 and find `transfer-4471`. Show the room the
ActivityTaskFailed Event with `AccountNotFoundError`, then point out the
Workflow is still Running, not Failed.

Ask it where it is:

    temporal workflow query --workflow-id transfer-4471 --name getStatus

It answers `"AWAITING_CORRECTION"`. Optional: send a malformed fix and query
again to show the handler ignored it:

    temporal workflow signal --workflow-id transfer-4471 \
      --name retryWithCorrection --input '"acct 1234"'

Now the real fix:

    temporal workflow signal --workflow-id transfer-4471 \
      --name retryWithCorrection --input '"12345678"'

    temporal workflow result --workflow-id transfer-4471

It completes with "Transfer of 500 to 12345678 completed". Back in the Web UI,
the whole story is one history: the failure, the Signal, the second
ExecuteTransfer, the result.

To run it again, use a new --workflow-id, or wait for this one to close: the
default reuse policy allows a new run once the first has finished.
-->
