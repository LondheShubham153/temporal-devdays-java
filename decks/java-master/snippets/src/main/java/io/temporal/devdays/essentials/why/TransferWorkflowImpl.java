package io.temporal.devdays.essentials.why;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

// part: 01-why-temporal.md "The Temporal version" 2
// TransferWorkflowImpl.java
public class TransferWorkflowImpl implements TransferWorkflow {
  private final BankActivities activities =
      Workflow.newActivityStub(
          BankActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setDoNotRetry(InsufficientFundsException.class.getName())
                      .build())
              .build());

  @Override
  public void transfer(Transfer t) {
    activities.withdraw(t);
    activities.deposit(t);
  }
}
// end part
