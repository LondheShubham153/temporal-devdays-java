package io.temporal.devdays.essentials.saga;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Order fulfillment as a Saga, as in the Saga Workshop's solution
 * (workshops/saga-order-fulfillment). The Workshop splits the Saga's construction and the unwind
 * into newSaga() and unwind() for its TODO 1, and runs the unwind in a detached cancellation scope;
 * the slide shows them inline and leaves the scope out.
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {
  private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

  // As in the Workshop. A forward step that keeps failing should give up fast, so the order can
  // unwind: two quick attempts.
  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setInitialInterval(Duration.ofMillis(100))
                      .setMaximumAttempts(2)
                      .build())
              .build());

  // An undo that gives up strands the stock or the money, so it keeps retrying with backoff: no
  // attempt limit, 500 ms doubling to at most 10 s between attempts, for up to 5 minutes in all.
  private static final ActivityOptions UNDO_OPTIONS =
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .setScheduleToCloseTimeout(Duration.ofMinutes(5))
          .setRetryOptions(
              RetryOptions.newBuilder()
                  .setInitialInterval(Duration.ofMillis(500))
                  .setBackoffCoefficient(2)
                  .setMaximumInterval(Duration.ofSeconds(10))
                  .build())
          .build();

  // The same five Activities with UNDO_OPTIONS. An undo retries with the stub it was registered on.
  private final OrderActivities undos =
      Workflow.newActivityStub(OrderActivities.class, UNDO_OPTIONS);

  // slide: 17-saga-03-implementation.md "# Implementation: the unwind"
  @Override
  public OrderResult fulfill(Order order) {
    Saga saga =
        new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

    try {
      saga.addCompensation(undos::releaseInventoryIfReserved, order);
      activities.reserveInventory(order);

      saga.addCompensation(undos::refundPaymentIfCharged, order);
      activities.chargePayment(order);

      String trackingNumber = activities.dispatchCourier(order);
      return new OrderResult(order.orderId(), trackingNumber);
    } catch (ActivityFailure e) {
      log.warn("fulfillment failed for {}, unwinding: {}",
          order.orderId(), Shared.rootCause(e));
      saga.compensate();
      throw e;
    }
  }
  // end slide
}
