package co.edu.corhuila.marketagri.transactions.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount in integer centavos (ADR-029): {@code 8500000} is {@code $ 85.000}. Always greater
 * than zero, at most the {@code unit_price_cents} column allows. The currency is always COP, so it
 * is not stored.
 */
public record Money(long cents) {

    private static final long MAX_CENTS = 999_999_999_999L;

    public Money {
        if (cents <= 0 || cents > MAX_CENTS) {
            throw new InvalidPurchaseException("money must be between 1 and " + MAX_CENTS + " centavos: " + cents);
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /**
     * The price of a quantity: {@code round_half_up(quantity × cents, 0)} with exact decimal
     * arithmetic (D-C11, ADR-029). A product so cheap that the amount rounds to zero is refused.
     */
    public Money times(Quantity quantity) {
        BigDecimal exact = quantity.value().multiply(BigDecimal.valueOf(cents));
        return new Money(exact.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }
}
