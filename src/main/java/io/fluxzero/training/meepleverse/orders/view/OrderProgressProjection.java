package io.fluxzero.training.meepleverse.orders.view;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.common.serialization.DeserializingMessage;
import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.ForeverRetryingErrorHandler;
import io.fluxzero.sdk.tracking.handling.HandleEvent;
import io.fluxzero.training.meepleverse.orders.api.AbortOrder;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.payments.api.PaymentAccepted;
import io.fluxzero.training.meepleverse.payments.api.PaymentRejected;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.ShipmentSent;
import io.fluxzero.training.meepleverse.shipping.api.ShippingFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderFailed;
import io.fluxzero.training.meepleverse.supplier.BackorderedItemArrived;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.fluxzero.training.meepleverse.orders.view.OrderObservation.Kind.*;

/** Independent of the exercise consumers: observes events and never drives fulfillment. */
@Component
@Consumer(name = "shop-order-progress", errorHandler = ForeverRetryingErrorHandler.class)
public class OrderProgressProjection {
    @HandleEvent
    void on(PlaceOrder event, DeserializingMessage message) {
        store(new OrderObservation(event.orderId(), ORDER_RECEIVED, event.details().itemIds(),
                                   null, null, message.getTimestamp()), "order");
    }

    @HandleEvent
    void on(PaymentAccepted event, DeserializingMessage message) {
        store(new OrderObservation(new OrderId(event.reference()), PAYMENT_ACCEPTED, List.of(),
                                   null, null, message.getTimestamp()), event.paymentId().toString());
    }

    @HandleEvent
    void on(PaymentRejected event, DeserializingMessage message) {
        store(new OrderObservation(new OrderId(event.reference()), PAYMENT_REJECTED, List.of(),
                                   null, null, message.getTimestamp()), event.paymentId().toString());
    }

    @HandleEvent
    void on(BackorderedItemArrived event, DeserializingMessage message) {
        store(new OrderObservation(event.orderId(), ITEM_ARRIVED, List.of(event.itemId()),
                                   null, null, message.getTimestamp()), event.itemId().toString());
    }

    @HandleEvent
    void on(BackorderFailed event, DeserializingMessage message) {
        store(new OrderObservation(event.orderId(), BACKORDER_FAILED, List.of(),
                                   null, "Backorder failed", message.getTimestamp()), "backorder");
    }

    @HandleEvent
    void on(ShipmentSent event, DeserializingMessage message) {
        // The existing command emits this BEFORE calling the carrier. It is not delivery confirmation.
        store(new OrderObservation(event.details().orderId(), SHIPMENT_RECORDED,
                                   List.of(event.details().itemId()), event.shipmentId(), null,
                                   message.getTimestamp()), event.shipmentId().toString());
    }

    @HandleEvent
    void on(ShippingFailed event, DeserializingMessage message) {
        store(new OrderObservation(event.orderId(), SHIPPING_FAILED, List.of(), event.shipmentId(),
                                   "Shipment failed", message.getTimestamp()), event.shipmentId().toString());
    }

    @HandleEvent
    void on(AbortOrder event, DeserializingMessage message) {
        store(new OrderObservation(event.orderId(), ORDER_ABORTED, List.of(), null,
                                   event.reason(), message.getTimestamp()), "abort");
    }

    private void store(OrderObservation observation, String subject) {
        // Stable fact IDs make replay/retry replacement safe without read-modify-write races.
        Fluxzero.prepareIndex(observation)
                .id(observation.orderId() + ":" + observation.kind() + ":" + subject)
                .collection(OrderObservation.class).indexAndWait();
    }
}
