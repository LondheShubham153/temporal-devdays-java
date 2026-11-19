package io.temporal.devdays.essentials.why;

import static io.temporal.devdays.essentials.why.Bank.bank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 01-why-temporal.md "The Temporal version": retries are configuration. The Activities have no
 * retry loop, yet a bank that fails the deposit twice still completes the transfer, with one
 * withdrawal. Insufficient funds is listed as non-retryable, so it fails at once.
 */
class WhyTemporalTest {
  private static final String TASK_QUEUE = "why-temporal";
  private static final Transfer TRANSFER = new Transfer("4471", "alice", "bob", 500);

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    bank.reset();
    bank.balances.put("alice", 1000L);
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new BankActivitiesImpl());
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private TransferWorkflow stub(String id) {
    return client.newWorkflowStub(
        TransferWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(id).build());
  }

  @Test
  void theBankFailsTheDepositTwiceAndTheTransferCompletesWithOneWithdrawal() {
    bank.depositFailuresLeft.set(2);

    stub("transfer-1").transfer(TRANSFER);

    assertEquals(
        1,
        bank.withdrawCalls.get(),
        "TransferWorkflowImpl.java: a failed deposit retries the deposit only; the withdrawal"
            + " already happened and is not repeated");
    assertEquals(
        3,
        bank.depositCalls.get(),
        "TransferWorkflowImpl.java: the default Retry Policy retries the deposit until it succeeds");
    assertEquals(500L, bank.balances.get("alice"), "alice is debited once");
    assertEquals(500L, bank.balances.get("bob"), "bob is credited once");
  }

  @Test
  void insufficientFundsIsNotRetriedAndNothingIsDeposited() {
    bank.balances.put("alice", 100L);

    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> stub("transfer-2").transfer(TRANSFER),
            "TransferWorkflowImpl.java: insufficient funds should fail the Workflow");
    ActivityFailure activityFailure = assertInstanceOf(ActivityFailure.class, e.getCause());
    ApplicationFailure cause =
        assertInstanceOf(ApplicationFailure.class, activityFailure.getCause());
    assertEquals(
        InsufficientFundsException.class.getName(),
        cause.getType(),
        "the failure type is the exception's class name, which setDoNotRetry lists");
    assertEquals(
        1,
        bank.withdrawCalls.get(),
        "TransferWorkflowImpl.java: setDoNotRetry means one withdrawal attempt, not a retry");
    assertEquals(0, bank.depositCalls.get(), "nothing is deposited when the withdrawal fails");
  }
}
