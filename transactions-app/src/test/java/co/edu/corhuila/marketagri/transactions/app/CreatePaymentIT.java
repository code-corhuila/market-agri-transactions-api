package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment.Command;
import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment.Payment;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.application.usecase.CreatePaymentService;
import co.edu.corhuila.marketagri.transactions.domain.model.DomainException;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The creation of I-06 with the real unit of work: the replay must roll the second insert back
 * and answer the original. It needs commits, and transactions_app has no DELETE, so the payments
 * stay in the throwaway database with random ids and conversations (scripts/test-database.sh).
 */
@SpringBootTest
class CreatePaymentIT extends TransactionsIntegrationTest {

    private static final UUID SAGA = UUID.fromString("5a6a0000-0000-4000-8000-000000000001");

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private UnitOfWork unitOfWork;

    private CreatePayment service() {
        return new CreatePaymentService(creations,
                transaction -> URI.create("https://checkout.test/p/?reference=" + transaction.id()),
                unitOfWork, Clock.systemUTC());
    }

    private static Command command(UUID transactionId, UUID conversation, String quantity) {
        return new Command(SAGA, transactionId.toString(), transactionId, conversation, UUID.randomUUID(),
                "Carlos Pérez", UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco",
                new BigDecimal(quantity), ProductUnit.ARROBA, 680_000);
    }

    @Test
    void aReplayAnswersTheStoredOriginalWithTheSameLink() {
        UUID id = UUID.randomUUID();
        Command first = command(id, UUID.randomUUID(), "12.5");
        Payment created = service().create(first);

        Payment replay = service().create(first);

        assertThat(created.created()).isTrue();
        assertThat(created.transaction().amount().cents()).isEqualTo(8_500_000);
        assertThat(replay.created()).isFalse();
        assertThat(replay.transaction()).isEqualTo(created.transaction());
        assertThat(replay.checkoutUrl()).isEqualTo(created.checkoutUrl());
    }

    @Test
    void theRealIndexRefusesASecondPendingPaymentForTheConversation() {
        UUID conversation = UUID.randomUUID();
        service().create(command(UUID.randomUUID(), conversation, "1"));

        assertThatThrownBy(() -> service().create(command(UUID.randomUUID(), conversation, "1")))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("TRANSACTION_IN_PROGRESS"));
    }
}
