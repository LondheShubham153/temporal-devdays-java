package io.temporal.devdays.essentials.why;

import io.temporal.activity.ActivityInterface;

/** The Activity interface behind "The Temporal version" in 01-why-temporal.md. */
@ActivityInterface
public interface BankActivities {
  void withdraw(Transfer t);

  void deposit(Transfer t);
}
