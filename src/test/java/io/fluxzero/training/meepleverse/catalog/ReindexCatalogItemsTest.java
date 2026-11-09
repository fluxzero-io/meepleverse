package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.common.Guarantee;
import io.fluxzero.common.api.search.SerializedDocument;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemDetails;
import io.fluxzero.training.meepleverse.catalog.api.model.ItemId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReindexCatalogItemsTest {
    @Test
    void rewritesAnExistingRevisionZeroItemUnderItsOriginalId() {
        var item = new Item(new ItemId("pandemic"), new ItemDetails("Pandemic", LocalDate.of(2008, 1, 1),
                "cooperative", BigDecimal.valueOf(45)));
        TestFixture.create(new ReindexCatalogItems()).whenExecuting(f -> {
            var current = f.documentStore().getSerializer().toDocument(item, "pandemic", "Item", null, null);
            var original = current.deserializeDocument().toBuilder().revision(0).facets(Set.of()).build();
            f.client().getSearchClient().index(List.of(new SerializedDocument(original)), Guarantee.STORED, false).join();
        }).expectNoErrors().expectThat(f -> {
            var hits = Fluxzero.search(Item.class).matchFacet("details/category", "cooperative")
                    .streamHits(SerializedDocument.class).toList();
            assertEquals(1, hits.size());
            var stored = hits.getFirst().getValue();
            assertEquals("pandemic", stored.getId());
            assertEquals(1, stored.getDocument().getRevision());
            assertEquals(item, f.documentStore().getSerializer().fromDocument(stored));
        });
    }
}
