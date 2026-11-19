package io.temporal.devdays.shipment;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.ActivityCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Packs a shipment, one item at a time. */
public class ShipmentActivitiesImpl implements ShipmentActivities {
    private static final Logger log = LoggerFactory.getLogger(ShipmentActivitiesImpl.class);

    @Override
    public ShipmentResult packShipment(ShipmentInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        int attempt = context.getInfo().getAttempt();

        // TODO 1c start: start from the count the last attempt Heartbeated, or 0 on a first attempt
        int packed = 0;
        // TODO 1c end

        // packed is a count of items already done, so the next item is packed + 1,
        // numbered the way the "packed item N of 40" lines below number them.
        int resumedFrom = packed;
        if (resumedFrom == 0) {
            log.info("packing {}, attempt {}: no Heartbeat recorded, starting at item 1 of {}",
                    input.shipmentId(), attempt, input.itemCount());
        } else {
            log.info("packing {}, attempt {}: last recorded Heartbeat says {} packed, resuming at item {} of {}",
                    input.shipmentId(), attempt, resumedFrom, resumedFrom + 1, input.itemCount());
        }

        try {
            for (int item = packed; item < input.itemCount(); item++) {
                packOneItem();
                packed = item + 1;
                log.info("packed item {} of {}", packed, input.itemCount());

                // TODO 1b start: Heartbeat the number of items packed so far
                // TODO 1b end
            }
        } catch (ActivityCompletionException e) {
            // Cancellation reaches a running Activity through its Heartbeat: the
            // next heartbeat() call throws. Let it out.
            log.info("interrupted after {} items packed", packed);
            throw e;
        }

        return new ShipmentResult(input.shipmentId(), packed, attempt, resumedFrom);
    }

    private static void packOneItem() {
        try {
            Thread.sleep(Shared.MILLIS_PER_ITEM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Activity.wrap(e);
        }
    }
}
