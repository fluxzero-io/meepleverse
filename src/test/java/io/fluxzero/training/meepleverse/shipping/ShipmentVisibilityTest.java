package io.fluxzero.training.meepleverse.shipping;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.authentication.Role;
import io.fluxzero.training.meepleverse.authentication.Sender;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.api.model.Addressee;
import io.fluxzero.training.meepleverse.orders.api.model.OrderId;
import io.fluxzero.training.meepleverse.shipping.api.SearchShipments;
import io.fluxzero.training.meepleverse.shipping.api.model.*;
import io.fluxzero.training.meepleverse.user.api.UserId;
import org.junit.jupiter.api.Test;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipmentVisibilityTest {
    final Shipment shipment = new Shipment(new ShipmentId("visibility-shipment"), new ShipmentDetails(
            List.of(new ItemId("pandemic")), new OrderId("visibility-order"),
            new Addressee("Alice Example", "Demo Street", "1", "1234AB", "Rotterdam", "NL",
                    "alice@example.com", null)));

    @Test
    void rolelessUsersRemainUnauthorized() {
        TestFixture.create().whenQueryByUser(new Sender(new UserId("viewer"), null), new SearchShipments())
                .expectExceptionalResult(UnauthorizedException.class);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void onlyAdminsSeeAddressesAndStorageStaysIntact(Role role) {
        Sender user = new Sender(new UserId("viewer"), role);
        boolean admin = user.isAdmin();
        TestFixture.create().givenDocument(shipment, shipment.shipmentId(), Shipment.class)
                .whenQueryByUser(user, new SearchShipments())
                .expectNoErrors()
                .<List<Shipment>>expectResult(shipments -> {
                    assertEquals(1, shipments.size());
                    Shipment visible = shipments.getFirst();
                    assertEquals(shipment.shipmentId(), visible.shipmentId());
                    assertEquals(shipment.details().orderId(), visible.details().orderId());
                    assertEquals(shipment.details().itemIds(), visible.details().itemIds());
                    assertEquals(admin ? shipment.details().addressee() : null, visible.details().addressee());
                    return true;
                })
                .expectThat(f -> assertEquals(shipment,
                        Fluxzero.getDocument(shipment.shipmentId(), Shipment.class).orElseThrow()));
    }
}
