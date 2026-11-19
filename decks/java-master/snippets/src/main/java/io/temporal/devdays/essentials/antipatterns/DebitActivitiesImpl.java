package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;

public class DebitActivitiesImpl implements DebitActivities {
  private final Ledger ledger;

  public DebitActivitiesImpl(Ledger ledger) {
    this.ledger = ledger;
  }

  // slide: 17-saga-07-related.md "An idempotency key that survives retries"
  @Override
  public String postDebit(Debit debit) {
    ActivityInfo info = Activity.getExecutionContext().getInfo();
    String idempotencyKey = info.getWorkflowRunId() + "-" + info.getActivityId();

    return ledger.post(debit, idempotencyKey);
  }
  // end slide
}
