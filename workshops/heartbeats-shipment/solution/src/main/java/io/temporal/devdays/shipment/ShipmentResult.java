package io.temporal.devdays.shipment;

/**
 * What packing produced, as recorded in the Workflow's Event History.
 *
 * @param finishedOnAttempt the attempt that finished the job: greater than 1 means a crash was survived
 * @param resumedFrom       the packed count that attempt read from the last Heartbeat the Server
 *                          recorded, 0 when there was none. Items after it were packed by this attempt,
 *                          including any a crashed attempt had packed but not yet Heartbeated.
 */
public record ShipmentResult(String shipmentId, int packed, int finishedOnAttempt, int resumedFrom) {}
