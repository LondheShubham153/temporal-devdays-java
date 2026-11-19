package io.temporal.devdays.essentials.why;

import static io.temporal.devdays.essentials.why.Bank.bank;

// part: 01-why-temporal.md "The Temporal version" 1
// BankActivitiesImpl.java
public class BankActivitiesImpl implements BankActivities {
  @Override
  public void withdraw(Transfer t) {
    bank.withdraw(t.fromAccount(), t.amount());
  }

  @Override
  public void deposit(Transfer t) {
    bank.deposit(t.toAccount(), t.amount());
  }
}
// end part
