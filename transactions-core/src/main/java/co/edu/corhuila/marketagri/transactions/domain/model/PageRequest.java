package co.edu.corhuila.marketagri.transactions.domain.model;

/** A bounded page of a list (D-C24, Anexo C): {@code page ≥ 1}, {@code 1 ≤ limit ≤ 100}. */
public record PageRequest(int page, int limit) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1: " + page);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ": " + limit);
        }
    }

    /** Items skipped before this page. */
    public long offset() {
        return (long) (page - 1) * limit;
    }
}
