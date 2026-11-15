package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AGGR-INV-006: one entry only from a confirmed payment, the whole amount and no fee (006b, D-C14). */
class LedgerEntryTest {

    private static final Instant CREATED = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-04T15:07:30.987654Z");
    private static final UUID PRODUCER = UUID.fromString("0a000000-0000-4000-8000-000000000002");

    private static Transaction pending() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                PRODUCER, "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", new Quantity(new BigDecimal("12.5")),
                ProductUnit.KILOGRAMO, Money.ofCents(680_000));
        return Transaction.open(UUID.randomUUID(), purchase, CREATED);
    }

    private static Transaction confirmed() {
        return pending().confirm(new PaymentResult("w-1", PaymentOutcome.APPROVED, 8_500_000, "COP",
                PaymentMethod.NEQUI), CONFIRMED_AT);
    }

    @Test
    void aConfirmedPaymentOwesItsWholeAmountToItsProducerPendingDisbursement() {
        Transaction confirmed = confirmed();

        LedgerEntry entry = confirmed.ledgerEntry();

        assertThat(entry.transactionId()).isEqualTo(confirmed.id());
        assertThat(entry.producerId()).isEqualTo(PRODUCER);
        assertThat(entry.grossAmount()).isEqualTo(Money.ofCents(8_500_000));
        assertThat(entry.platformFeeCents()).isZero();
        assertThat(entry.netAmountCents()).isEqualTo(8_500_000);
        assertThat(entry.status()).isEqualTo(LedgerStatus.PENDING_DISBURSEMENT);
        assertThat(entry.createdAt()).isEqualTo(confirmed.confirmedAt()).isEqualTo("2026-11-04T15:07:30Z");
    }

    @Test
    void aPendingOrFailedPaymentHasNoLedgerEntry() {
        Transaction pending = pending();
        Transaction failed = pending().fail(new PaymentResult("w-2", PaymentOutcome.REJECTED, 8_500_000, "COP",
                PaymentMethod.CARD), CONFIRMED_AT);
        Transaction expired = pending().expire(CONFIRMED_AT);

        assertThatThrownBy(pending::ledgerEntry).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(failed::ledgerEntry).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(expired::ledgerEntry).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theNetIsTheGrossLessAFeeThatStaysBetweenZeroAndTheGross() {
        UUID tx = UUID.randomUUID();
        Money gross = Money.ofCents(1_000);

        assertThat(new LedgerEntry(tx, PRODUCER, gross, 1_000, LedgerStatus.PENDING_DISBURSEMENT, CREATED)
                .netAmountCents()).isZero();
        assertThat(new LedgerEntry(tx, PRODUCER, gross, 250, LedgerStatus.PENDING_DISBURSEMENT, CREATED)
                .netAmountCents()).isEqualTo(750);
        assertThatThrownBy(() -> new LedgerEntry(tx, PRODUCER, gross, -1, LedgerStatus.PENDING_DISBURSEMENT, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LedgerEntry(tx, PRODUCER, gross, 1_001, LedgerStatus.PENDING_DISBURSEMENT,
                CREATED)).isInstanceOf(IllegalArgumentException.class);
    }
}
