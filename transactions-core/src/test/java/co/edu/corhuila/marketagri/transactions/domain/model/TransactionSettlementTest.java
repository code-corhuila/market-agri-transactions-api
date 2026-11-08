package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The only ways out of PENDING: a verified result of the provider (E-43, ADR-019). The guards of
 * the contract come before CONFIRMED, and CONFIRMED and FAILED are terminal (AGGR-INV-004, -005).
 */
class TransactionSettlementTest {

    private static final Instant LATER = Instant.parse("2026-11-04T15:07:30.987654Z");
    private static final Instant LATER_IN_SECONDS = Instant.parse("2026-11-04T15:07:30Z");
    private static final long AMOUNT = 8_500_000;

    private static Transaction pending() {
        return Transaction.open(TransactionTest.ID, TransactionTest.purchase("1"), TransactionTest.NOW);
    }

    private static PaymentResult result(PaymentOutcome outcome, long amountCents, String currency) {
        return new PaymentResult("1234-1610641025-49201", outcome, amountCents, currency, PaymentMethod.NEQUI);
    }

    @Test
    void anApprovedResultConfirmsWithItsMethodAndProviderId() {
        Transaction confirmed = pending().confirm(result(PaymentOutcome.APPROVED, AMOUNT, "COP"), LATER);

        assertThat(confirmed.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(confirmed.paymentMethod()).isEqualTo(PaymentMethod.NEQUI);
        assertThat(confirmed.gatewayTransactionId()).isEqualTo("1234-1610641025-49201");
        assertThat(confirmed.confirmedAt()).isEqualTo(LATER_IN_SECONDS);
        assertThat(confirmed.failedAt()).isNull();
        assertThat(confirmed.amount()).isEqualTo(pending().amount());
    }

    @Test
    void anotherAmountIsRefusedBeforeConfirming() {
        assertThatThrownBy(() -> pending().confirm(result(PaymentOutcome.APPROVED, AMOUNT - 1, "COP"), LATER))
                .isInstanceOf(PaymentResultMismatchException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void anotherCurrencyIsRefusedBeforeConfirming() {
        assertThatThrownBy(() -> pending().confirm(result(PaymentOutcome.APPROVED, AMOUNT, "USD"), LATER))
                .isInstanceOf(PaymentResultMismatchException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void aRejectedResultFailsWithItsMethodAndProviderId() {
        Transaction failed = pending().fail(result(PaymentOutcome.REJECTED, AMOUNT, "COP"), LATER);

        assertThat(failed.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failed.paymentMethod()).isEqualTo(PaymentMethod.NEQUI);
        assertThat(failed.gatewayTransactionId()).isEqualTo("1234-1610641025-49201");
        assertThat(failed.failedAt()).isEqualTo(LATER_IN_SECONDS);
        assertThat(failed.confirmedAt()).isNull();
        assertThat(failed.neverOpened()).isFalse();
    }

    @Test
    void aResultConfirmsOrFailsOnlyWithItsOwnOutcome() {
        assertThatThrownBy(() -> pending().confirm(result(PaymentOutcome.REJECTED, AMOUNT, "COP"), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending().fail(result(PaymentOutcome.APPROVED, AMOUNT, "COP"), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending().fail(result(PaymentOutcome.PENDING, AMOUNT, "COP"), LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmedAndFailedAreTerminal() {
        Transaction confirmed = pending().confirm(result(PaymentOutcome.APPROVED, AMOUNT, "COP"), LATER);
        Transaction failed = pending().fail(result(PaymentOutcome.REJECTED, AMOUNT, "COP"), LATER);

        assertThat(pending().isPending()).isTrue();
        assertThat(confirmed.isPending()).isFalse();
        assertThatThrownBy(() -> confirmed.fail(result(PaymentOutcome.REJECTED, AMOUNT, "COP"), LATER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.confirm(result(PaymentOutcome.APPROVED, AMOUNT, "COP"), LATER))
                .isInstanceOf(IllegalStateException.class);
    }
}
