package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import java.util.List;
import java.util.function.Function;

/** {@code {data, meta}} of D-C24. */
public record PageView<T>(List<T> data, Meta meta) {

    public record Meta(int page, int limit, long total, int totalPages) {
    }

    static <S, T> PageView<T> of(Page<S> page, Function<S, T> view) {
        return new PageView<>(page.items().stream().map(view).toList(),
                new Meta(page.request().page(), page.request().limit(), page.total(), page.totalPages()));
    }
}
