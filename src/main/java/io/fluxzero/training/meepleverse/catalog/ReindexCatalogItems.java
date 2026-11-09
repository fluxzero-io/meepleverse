package io.fluxzero.training.meepleverse.catalog;

import io.fluxzero.sdk.tracking.Consumer;
import io.fluxzero.sdk.tracking.handling.HandleDocument;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;
import org.springframework.stereotype.Component;

@Component
@Consumer(name = "catalog-category-facets", minIndex = 0)
public class ReindexCatalogItems {
    @HandleDocument
    Item reindex(Item item) {
        return item;
    }
}
