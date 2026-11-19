package io.temporal.devdays.essentials.sagacompensation;

import io.temporal.devdays.essentials.saga.Order;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Not on a slide: the smallest Workflow that charges and then undoes the charge. */
@WorkflowInterface
public interface ChargeThenRefundWorkflow {
  @WorkflowMethod
  void chargeThenRefund(Order order);
}
