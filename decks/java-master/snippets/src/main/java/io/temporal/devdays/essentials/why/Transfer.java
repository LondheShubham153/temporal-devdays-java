package io.temporal.devdays.essentials.why;

/** One bank transfer, the example that runs through 01-why-temporal.md. Amounts are in dollars. */
public record Transfer(String transferId, String fromAccount, String toAccount, long amount) {}
