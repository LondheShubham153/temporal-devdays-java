package io.temporal.devdays.essentials.sagacompensation;

import static io.temporal.devdays.essentials.sagacompensation.PaymentApi.payments;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.client.WorkflowOptions;
import io.temporal.devdays.essentials.saga.Order;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 17-saga-04-when-to-register.md "A compensation Activity": the undo has no deadline and no attempt
 * cap, so it outlasts an outage of the payment API, and its idempotency key keeps the retries to a
 * single refund.
 */
class SagaCompensationTest {
  private static final String TASK_QUEUE = "saga-compensation";

  private TestWorkflowEnvironment env;

  @BeforeEach
  void setUp() {
    payments.reset();
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(ChargeThenRefundWorkflowImpl.class);
    worker.registerActivitiesImplementations(new PaymentActivitiesImpl());
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void theRefundKeepsRetryingThroughAnOutageAndRefundsOnce() {
    payments.refundFailuresLeft.set(3);

    env.getWorkflowClient()
        .newWorkflowStub(
            ChargeThenRefundWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId("refund-order-1")
                .build())
        .chargeThenRefund(Order.widget("order-1"));

    assertEquals(
        4,
        payments.refundCalls.get(),
        "ChargeThenRefundWorkflowImpl.java: the refunds stub sets no Retry Policy, so the default"
            + " keeps retrying until the payment API is back");
    assertEquals(
        List.of("order-1"),
        List.copyOf(payments.refunds.values()),
        "PaymentActivitiesImpl.refundPayment: the idempotency key means one refund, not four");
  }
}
