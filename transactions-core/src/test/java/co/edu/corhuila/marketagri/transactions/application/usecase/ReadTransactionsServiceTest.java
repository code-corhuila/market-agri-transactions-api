package co.edu.corhuila.marketagri.transactions.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionRepository;
import co.edu.corhuila.marketagri.transactions.domain.model.DomainException;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.AdminTransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Participant;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E-41 (404 before 403) and E-42 (the filter, newest first, pages) in the core. */
class ReadTransactionsServiceTest {

    private static final UUID CARLOS = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID ANA = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");

    /** The repository in memory, with the order of the contract. */
    private static final class InMemory implements TransactionRepository {
        final List<Transaction> stored = new ArrayList<>();

        @Override
        public Optional<Transaction> findById(UUID id) {
            return stored.stream().filter(transaction -> transaction.id().equals(id)).findFirst();
        }

        @Override
        public Page<Transaction> findPage(TransactionFilter filter, PageRequest request) {
            List<Transaction> matching = stored.stream().filter(filter::matches)
                    .sorted(Comparator.comparing(Transaction::createdAt).reversed().thenComparing(Transaction::id))
                    .toList();
            List<Transaction> page = matching.stream().skip(request.offset()).limit(request.limit()).toList();
            return new Page<>(page, request, matching.size());
        }

        @Override
        public Page<Transaction> findPage(AdminTransactionFilter filter, PageRequest request) {
            List<Transaction> matching = stored.stream().filter(filter::matches)
                    .sorted(Comparator.comparing(Transaction::createdAt).reversed().thenComparing(Transaction::id))
                    .toList();
            List<Transaction> page = matching.stream().skip(request.offset()).limit(request.limit()).toList();
            return new Page<>(page, request, matching.size());
        }
    }

    private final InMemory repository = new InMemory();
    private final ReadTransactionsService service = new ReadTransactionsService(repository);

    private Transaction stored(UUID buyer, UUID producer, String createdAt) {
        Transaction transaction = Transaction.open(UUID.randomUUID(), new PurchaseSnapshot(UUID.randomUUID(), buyer,
                "Carlos Pérez", producer, "Ana Ruiz", UUID.randomUUID(), "Café", Quantity.of("1"), ProductUnit.ARROBA,
                Money.ofCents(680_000)), Instant.parse(createdAt));
        repository.stored.add(transaction);
        return transaction;
    }

    @Test
    void aParticipantReadsThePayment() {
        Transaction transaction = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");

        assertThat(service.get(transaction.id(), CARLOS)).isEqualTo(transaction);
        assertThat(service.get(transaction.id(), ANA)).isEqualTo(transaction);
    }

    @Test
    void anUnknownPaymentIsNotFoundEvenForAStranger() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void aStrangerIsNotAParticipant() {
        Transaction transaction = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");

        assertThatThrownBy(() -> service.get(transaction.id(), UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("NOT_A_PARTICIPANT"));
    }

    @Test
    void mineListsOnlyTheCallersSideNewestFirstInPages() {
        Transaction older = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");
        Transaction newer = stored(CARLOS, ANA, "2026-11-04T16:00:00Z");
        stored(UUID.randomUUID(), ANA, "2026-11-04T17:00:00Z");

        Page<Transaction> first = service.mine(new TransactionFilter(Participant.BUYER, CARLOS, null),
                new PageRequest(1, 1));
        Page<Transaction> sales = service.mine(new TransactionFilter(Participant.PRODUCER, ANA, null),
                new PageRequest(1, 20));

        assertThat(first.items()).containsExactly(newer);
        assertThat(first.total()).isEqualTo(2L);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(service.mine(new TransactionFilter(Participant.BUYER, CARLOS, null), new PageRequest(2, 1))
                .items()).containsExactly(older);
        assertThat(sales.total()).isEqualTo(3L);
    }

    @Test
    void anAdminReadsAnyTransactionAndFiltersTheListByEveryOptionalField() {
        Transaction pending = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");
        Transaction failed = stored(CARLOS, UUID.randomUUID(), "2026-11-04T16:00:00Z").failedToOpen();
        repository.stored.set(1, failed);

        assertThat(service.adminGet(pending.id())).isEqualTo(pending);
        assertThat(service.admin(new AdminTransactionFilter(TransactionStatus.FAILED, null, CARLOS),
                new PageRequest(1, 20)).items()).containsExactly(failed);
        assertThat(service.admin(new AdminTransactionFilter(null, ANA, null), new PageRequest(1, 20))
                .items()).containsExactly(pending);
    }
}
