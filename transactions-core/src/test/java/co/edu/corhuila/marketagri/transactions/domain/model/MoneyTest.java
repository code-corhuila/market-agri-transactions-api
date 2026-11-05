package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Integer centavos (ADR-029) and the amount rule of D-C11: round_half_up(quantity × price, 0). */
class MoneyTest {

    @Test
    void zeroOrNegativeCentavosAreRefused() {
        assertThatThrownBy(() -> Money.ofCents(0))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("VALIDATION_ERROR"));
        assertThatThrownBy(() -> Money.ofCents(-1)).isInstanceOf(InvalidPurchaseException.class);
    }

    @Test
    void moreThanTheColumnHoldsIsRefused() {
        assertThat(Money.ofCents(999_999_999_999L).cents()).isEqualTo(999_999_999_999L);
        assertThatThrownBy(() -> Money.ofCents(1_000_000_000_000L)).isInstanceOf(InvalidPurchaseException.class);
    }

    @Test
    void aWholeQuantityMultipliesExactly() {
        assertThat(Money.ofCents(8_500_000).times(Quantity.of("3"))).isEqualTo(Money.ofCents(25_500_000));
    }

    @Test
    void aDecimalQuantityRoundsHalfUpToWholeCentavos() {
        assertThat(Money.ofCents(8_500_000).times(Quantity.of("2.5"))).isEqualTo(Money.ofCents(21_250_000));
        assertThat(Money.ofCents(680_000).times(Quantity.of("12.5"))).isEqualTo(Money.ofCents(8_500_000));
        assertThat(Money.ofCents(333).times(Quantity.of("0.5"))).isEqualTo(Money.ofCents(167));
        assertThat(Money.ofCents(333).times(Quantity.of("0.49"))).isEqualTo(Money.ofCents(163));
    }

    @Test
    void anAmountThatRoundsToZeroIsRefused() {
        assertThatThrownBy(() -> Money.ofCents(1).times(Quantity.of("0.01")))
                .isInstanceOf(InvalidPurchaseException.class);
    }
}
