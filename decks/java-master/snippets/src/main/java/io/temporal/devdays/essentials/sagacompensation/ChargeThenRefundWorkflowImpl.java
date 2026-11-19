package io.temporal.devdays.essentials.sagacompensation;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.saga.Order;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * The two Activity stubs from "A compensation Activity" in 17-saga-04-when-to-register.md: the
 * forward step with a deadline, and the undo with none. The Workflow method is not on the slide;
 * it charges and then refunds, so the test can watch the refund outlast an outage.
 */
public class ChargeThenRefundWorkflowImpl implements ChargeThenRefundWorkflow {
  // part: 17-saga-04-when-to-register.md "heading: A compensation Activity" 2
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
  // end part

  @Override
  public void chargeThenRefund(Order order) {
    charges.chargePayment(order);
    refunds.refundPayment(order);
  }
}
