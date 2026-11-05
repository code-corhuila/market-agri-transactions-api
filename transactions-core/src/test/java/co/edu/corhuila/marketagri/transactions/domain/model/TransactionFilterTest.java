package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E-42: the caller's purchases or sales, optionally of one status. */
class TransactionFilterTest {

    private static final UUID CARLOS = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID ANA = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");

    private static Transaction pending() {
        return Transaction.open(UUID.randomUUID(), new PurchaseSnapshot(UUID.randomUUID(), CARLOS, "Carlos Pérez",
                ANA, "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("1"), ProductUnit.ARROBA,
                Money.ofCents(680_000)), Instant.parse("2026-11-04T15:00:00Z"));
    }

    @Test
    void aBuyerSeesTheirPurchasesAndAProducerTheirSales() {
        Transaction transaction = pending();

        assertThat(new TransactionFilter(Participant.BUYER, CARLOS, null).matches(transaction)).isTrue();
        assertThat(new TransactionFilter(Participant.PRODUCER, ANA, null).matches(transaction)).isTrue();
    }

    @Test
    void theSideMatters() {
        Transaction transaction = pending();

        assertThat(new TransactionFilter(Participant.PRODUCER, CARLOS, null).matches(transaction)).isFalse();
        assertThat(new TransactionFilter(Participant.BUYER, ANA, null).matches(transaction)).isFalse();
        assertThat(new TransactionFilter(Participant.BUYER, UUID.randomUUID(), null).matches(transaction)).isFalse();
    }

    @Test
    void theStatusNarrowsTheList() {
        Transaction pending = pending();
        Transaction failed = pending().failedToOpen();

        TransactionFilter onlyPending = new TransactionFilter(Participant.BUYER, CARLOS, TransactionStatus.PENDING);
        assertThat(onlyPending.matches(pending)).isTrue();
        assertThat(onlyPending.matches(failed)).isFalse();
    }
}
