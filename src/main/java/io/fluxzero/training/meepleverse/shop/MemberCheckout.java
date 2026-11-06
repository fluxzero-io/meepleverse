package io.fluxzero.training.meepleverse.shop;

import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.authentication.NoUserRequired;
import io.fluxzero.sdk.web.*;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import io.fluxzero.training.meepleverse.orders.OrdersEndpoint.OrderReceipt;
import io.fluxzero.training.meepleverse.orders.api.PlaceOrder;
import io.fluxzero.training.meepleverse.orders.api.model.*;
import io.fluxzero.training.meepleverse.orders.view.OrderStatus;
import io.fluxzero.training.meepleverse.payments.api.model.PaymentId;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.fluxzero.training.meepleverse.shop.ShopLogin.*;

@Component
@NoUserRequired
@Path("/shop-account")
public class MemberCheckout {
    private final ShopLogin login;
    public MemberCheckout(ShopLogin login) { this.login = login; }

    record Purchase(List<ItemId> itemIds) {}
    record Summary(OrderId orderId, List<ItemId> itemIds) {}
    record Account(String name, String csrf, Addressee delivery, String paymentMethod) {}

    /** Each checkout authorizes a new transaction against the same fictional payment account. */
    record Profile(Addressee delivery, String walletId) {
        String paymentMethod() { return "Meeple Pay · " + walletId; }
        PaymentId authorizeDemoPurchase() { return new PaymentId(walletId + "-" + UUID.randomUUID()); }
    }

    @HandleGet("/profile")
    WebResponse profile(WebRequest request) {
        var member = login.member(request);
        if (member == null) return noStore().payload(Map.of("signedIn", false)).build();
        var profile = profileFor(member);
        return noStore().payload(new Account(member.name(), member.csrf(), profile.delivery(), profile.paymentMethod())).build();
    }

    private Profile profileFor(Member member) {
        var store = Fluxzero.get().keyValueStore();
        var key = "shop-profile:" + member.key();
        Profile saved = store.get(key);
        if (saved != null) return saved;
        var alias = member.key().substring(0, 10);
        var profile = new Profile(new Addressee(member.name(), "Game Lane", "42", "1234 AB", "Meepleville",
                "Netherlands", "player-" + alias + "@example.test", ""), "MP-" + alias);
        return store.storeIfAbsent(key, profile) ? profile : store.get(key);
    }

    @HandlePost("/delivery")
    WebResponse saveDelivery(WebRequest request, Addressee address) {
        var member = login.writable(request);
        if (member == null) return denied();
        if (address == null || java.util.stream.Stream.of(address.fullName(), address.street(), address.houseNumber(),
                address.postalCode(), address.city(), address.country(), address.email())
                .anyMatch(s -> s == null || s.isBlank() || s.length() > 200)
                || address.phone() != null && address.phone().length() > 200) {
            return noStore().status(400).payload("Complete the delivery address").build();
        }
        Fluxzero.get().keyValueStore().store("shop-profile:" + member.key(), new Profile(address, profileFor(member).walletId()),
                io.fluxzero.common.Guarantee.STORED);
        return noStore().payload(Map.of("saved", true)).build();
    }

    @HandlePost("/orders")
    CompletableFuture<WebResponse> buy(WebRequest request, Purchase purchase) {
        var member = login.writable(request);
        if (member == null) return CompletableFuture.completedFuture(denied());
        var ids = purchase == null ? null : purchase.itemIds();
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.size() != new HashSet<>(ids).size()
                || ids.stream().anyMatch(id -> id == null || Fluxzero.loadAggregate(id).get() == null)) {
            return CompletableFuture.completedFuture(noStore().status(400).payload("Choose available games").build());
        }
        var id = Fluxzero.generateId(OrderId.class);
        var profile = profileFor(member);
        var details = new OrderDetails(ids, profile.authorizeDemoPurchase(), profile.delivery());
        return Fluxzero.sendCommand(new PlaceOrder(id, details, member.key()))
                .thenApply(ignored -> noStore().payload(new OrderReceipt(id)).build());
    }

    @HandleGet("/orders")
    WebResponse orders(WebRequest request) {
        var member = login.member(request);
        if (member == null) return denied();
        var orders = Fluxzero.search(Order.class).match(member.key(), "customerId").sortByTimestamp(true)
                .<Order>fetch(100).stream().filter(o -> member.key().equals(o.customerId()))
                .map(o -> new Summary(o.orderId(), o.details().itemIds())).toList();
        return noStore().payload(orders).build();
    }

    @HandleGet("/orders/{orderId}")
    WebResponse order(WebRequest request, @PathParam OrderId orderId) {
        var member = login.member(request);
        if (member == null) return denied();
        var order = Fluxzero.loadAggregate(orderId).get();
        if (order == null || !member.key().equals(order.customerId())) {
            return noStore().status(404).payload("Order not found").build();
        }
        return noStore().payload(OrderStatus.from(order)).build();
    }
}
