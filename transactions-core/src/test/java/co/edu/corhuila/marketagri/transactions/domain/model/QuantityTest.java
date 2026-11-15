package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** I-06 `quantity`: greater than zero, at most two decimals, what numeric(12,2) holds. */
class QuantityTest {

    @Test
    void zeroOrNegativeIsRefused() {
        assertThatThrownBy(() -> Quantity.of("0")).isInstanceOf(InvalidPurchaseException.class);
        assertThatThrownBy(() -> Quantity.of("-1")).isInstanceOf(InvalidPurchaseException.class);
    }

    @Test
    void moreThanTwoDecimalsIsRefused() {
        assertThatThrownBy(() -> Quantity.of("1.005")).isInstanceOf(InvalidPurchaseException.class);
        assertThat(Quantity.of("1.500").toString()).isEqualTo("1.50");
    }

    @Test
    void moreThanTheColumnHoldsIsRefused() {
        assertThat(Quantity.of("9999999999.99").toString()).isEqualTo("9999999999.99");
        assertThatThrownBy(() -> Quantity.of("10000000000")).isInstanceOf(InvalidPurchaseException.class);
    }

    @Test
    void theSameAmountWrittenTwoWaysIsTheSameQuantity() {
        assertThat(Quantity.of("5")).isEqualTo(Quantity.of("5.00"));
    }
}
