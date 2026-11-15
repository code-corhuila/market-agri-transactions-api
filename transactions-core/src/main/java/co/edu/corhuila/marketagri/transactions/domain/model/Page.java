package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.List;

/** One page and the total of items that match, not only this page (D-C24). */
public record Page<T>(List<T> items, PageRequest request, long total) {

    public Page {
        items = List.copyOf(items);
    }

    /** {@code ceil(total / limit)}, {@code 0} when nothing matches. */
    public int totalPages() {
        return (int) ((total + request.limit() - 1) / request.limit());
    }
}
