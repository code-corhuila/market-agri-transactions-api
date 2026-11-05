package co.edu.corhuila.marketagri.transactions.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much of the product was bought, in its {@link ProductUnit}: greater than zero, at most two
 * decimals (D-C7: 12.5 kg) and at most what {@code numeric(12,2)} holds. Always kept with scale 2,
 * so {@code 5} and {@code 5.00} are the same quantity (and the same request on a replay).
 */
public record Quantity(BigDecimal value) {

    private static final int SCALE = 2;
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    public Quantity {
        if (value.signum() <= 0) {
            throw new InvalidPurchaseException("quantity must be greater than zero: " + value);
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidPurchaseException("quantity has more than two decimals: " + value);
        }
        if (value.compareTo(MAX) > 0) {
            throw new InvalidPurchaseException("quantity above " + MAX + ": " + value);
        }
        value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
