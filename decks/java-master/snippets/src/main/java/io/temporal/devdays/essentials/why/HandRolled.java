package io.temporal.devdays.essentials.why;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The two hand-written slides from 01-why-temporal.md: the happy path and the retry helper. Plain
 * Java with no Temporal in it, which is the point of those slides.
 */
public class HandRolled {
  private final Bank bank;

  public HandRolled(Bank bank) {
    this.bank = bank;
  }

  // slide: 01-why-temporal.md "The happy path"
  public void transfer(Transfer t) {
    bank.withdraw(t.fromAccount(), t.amount());
    bank.deposit(t.toAccount(), t.amount());
  }
  // end slide

  // slide: 01-why-temporal.md "So you add a retry"
  void withRetry(Runnable call) throws InterruptedException {
    long delay = 1_000;
    for (int attempt = 1; ; attempt++) {
      try {
        call.run();
        return;
      } catch (InsufficientFundsException e) {
        throw e;
      } catch (RuntimeException e) {
        if (attempt == 5) throw e;
        Thread.sleep(delay + ThreadLocalRandom.current().nextLong(250));
        delay = Math.min(delay * 2, 30_000);
      }
    }
  }
  // end slide
}
