package io.temporal.devdays.essentials.why;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bank's API behind 01-why-temporal.md, as an in-memory fake. Tests set {@code
 * depositFailuresLeft} to make the deposit fail a set number of times, as if the bank were down,
 * and read the call counts to see how often each endpoint was hit.
 */
public final class Bank {
  public final Map<String, Long> balances = new ConcurrentHashMap<>();
  public final AtomicInteger depositFailuresLeft = new AtomicInteger();
  public final AtomicInteger withdrawCalls = new AtomicInteger();
  public final AtomicInteger depositCalls = new AtomicInteger();

  public void withdraw(String account, long amount) {
    withdrawCalls.incrementAndGet();
    long balance = balances.getOrDefault(account, 0L);
    if (balance < amount) {
      throw new InsufficientFundsException(account + " has " + balance);
    }
    balances.put(account, balance - amount);
  }

  public void deposit(String account, long amount) {
    depositCalls.incrementAndGet();
    if (depositFailuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
      throw new IllegalStateException("bank unavailable");
    }
    balances.merge(account, amount, Long::sum);
  }

  public void reset() {
    balances.clear();
    depositFailuresLeft.set(0);
    withdrawCalls.set(0);
    depositCalls.set(0);
  }

  public static final Bank bank = new Bank();
}
