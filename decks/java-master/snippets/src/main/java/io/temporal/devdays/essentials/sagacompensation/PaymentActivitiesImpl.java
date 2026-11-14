package io.temporal.devdays.essentials.sagacompensation;

import static io.temporal.devdays.essentials.sagacompensation.PaymentApi.payments;

import io.temporal.devdays.essentials.saga.Order;

/** The compensation Activity on "A compensation Activity" in 17-saga-04-when-to-register.md. */
public class PaymentActivitiesImpl implements PaymentActivities {
  @Override
  public void chargePayment(Order order) {
    payments.charge(order.orderId());
  }

  // part: 17-saga-04-when-to-register.md "heading: A compensation Activity" 1
  // PaymentActivitiesImpl.java
  @Override
  public void refundPayment(Order order) {
    String idempotencyKey = "refund-" + order.orderId();
    payments.refund(order.orderId(), idempotencyKey);
  }
  // end part
}
