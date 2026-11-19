package io.temporal.devdays.essentials.sagacompensation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The payment API behind "A compensation Activity" in 17-saga-04-when-to-register.md, as an
 * in-memory fake. Tests set {@code refundFailuresLeft} to make the refund endpoint fail a set
 * number of times, as if it were down.
 */
public final class PaymentApi {
  public final Set<String> charged = ConcurrentHashMap.newKeySet();
  /** Idempotency key to order ID: one entry per refund actually made. */
  public final Map<String, String> refunds = new ConcurrentHashMap<>();

  public final AtomicInteger refundCalls = new AtomicInteger();
  public final AtomicInteger refundFailuresLeft = new AtomicInteger();

  public void charge(String orderId) {
    charged.add(orderId);
  }

  public void refund(String orderId, String idempotencyKey) {
    refundCalls.incrementAndGet();
    if (refundFailuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
      throw new IllegalStateException("payment API unavailable");
    }
    // The same key twice is one refund, not two.
    refunds.putIfAbsent(idempotencyKey, orderId);
  }

  public void reset() {
    charged.clear();
    refunds.clear();
    refundCalls.set(0);
    refundFailuresLeft.set(0);
  }

  public static final PaymentApi payments = new PaymentApi();
}
