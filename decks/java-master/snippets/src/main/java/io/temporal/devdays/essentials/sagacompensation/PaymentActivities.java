package io.temporal.devdays.essentials.sagacompensation;

import io.temporal.activity.ActivityInterface;
import io.temporal.devdays.essentials.saga.Order;

/** The forward step and its undo, behind "A compensation Activity". */
@ActivityInterface
public interface PaymentActivities {
  void chargePayment(Order order);

  void refundPayment(Order order);
}
