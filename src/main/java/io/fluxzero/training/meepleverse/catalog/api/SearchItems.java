package io.fluxzero.training.meepleverse.catalog.api;

import io.fluxzero.common.api.search.FacetStats;
import io.fluxzero.common.api.search.constraints.ContainsConstraint;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.tracking.handling.HandleQuery;
import io.fluxzero.sdk.tracking.handling.Request;
import io.fluxzero.training.meepleverse.catalog.api.model.Item;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SearchItems(String term, String category) implements Request<SearchItems.Result> {
    private static final String CATEGORY_FACET = "details/category";

    public SearchItems() {
        this(null);
    }

    public SearchItems(String term) {
        this(term, null);
    }

    public record Result(List<Item> items, Map<String, Integer> categories) {}

    @HandleQuery
    Result handle() {
        var search = Fluxzero.search(Item.class);
        if (term != null && !term.isBlank()) {
            search = search.constraint(ContainsConstraint.contains(term.strip(), true, true, "details/name"));
        }
        Map<String, Integer> counts = new TreeMap<>();
        for (FacetStats facet : search.facetStats()) {
            if (CATEGORY_FACET.equals(facet.getName())) {
                counts.put(facet.getValue(), facet.getCount());
            }
        }
        if (category != null && !category.isBlank()) {
            search = search.matchFacet(CATEGORY_FACET, category);
        }
        var items = search.fetchAll(Item.class);
        return new Result(items, counts);
    }
}
