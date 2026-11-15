package co.edu.corhuila.marketagri.transactions.application.usecase;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult;
import co.edu.corhuila.marketagri.transactions.application.port.out.Ledger;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.FailureReason;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResultMismatchException;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * E-43 in the core: the payment is locked, the result is checked against it, and the new state
 * and its event are stored in the same transaction (transactional outbox, ADR-014), with the
 * producer's ledger entry when it is confirmed (AGGR-INV-006). Anything that
 * does not settle a PENDING payment changes nothing, so a redelivered event is harmless.
 */
public final class ApplyPaymentResultService implements ApplyPaymentResult {

    private final PaymentSettlements settlements;
    private final Ledger ledger;
    private final UnitOfWork unitOfWork;
    private final Supplier<UUID> eventIds;
    private final Clock clock;

    public ApplyPaymentResultService(PaymentSettlements settlements, Ledger ledger, UnitOfWork unitOfWork,
                                     Supplier<UUID> eventIds, Clock clock) {
        this.settlements = settlements;
        this.ledger = ledger;
        this.unitOfWork = unitOfWork;
        this.eventIds = eventIds;
        this.clock = clock;
    }

    @Override
    public Outcome apply(Command command) {
        PaymentResult result = command.result();
        if (result.outcome() == PaymentOutcome.PENDING) {
            return Outcome.STILL_PENDING;
        }
        return unitOfWork.inTransaction(() -> {
            Optional<Transaction> stored = settlements.lockById(command.transactionId());
            if (stored.isEmpty()) {
                return Outcome.UNKNOWN_TRANSACTION;
            }
            if (!stored.get().isPending()) {
                return Outcome.ALREADY_SETTLED;
            }
            if (settlements.gatewayTransactionIdTakenByAnother(result.gatewayTransactionId(), command.transactionId())) {
                return Outcome.REFUSED_BY_GUARD;
            }
            return settle(stored.get(), command);
        });
    }

    @Override
    public Outcome expire(UUID transactionId, String correlationId) {
        return unitOfWork.inTransaction(() -> {
            Optional<Transaction> stored = settlements.lockById(transactionId);
            if (stored.isEmpty()) {
                return Outcome.UNKNOWN_TRANSACTION;
            }
            if (!stored.get().isPending()) {
                return Outcome.ALREADY_SETTLED;
            }
            Transaction failed = stored.get().expire(clock.instant());
            settlements.settle(failed, TransactionEvent.failed(eventIds.get(), failed, FailureReason.EXPIRED,
                    correlationId, null));
            return Outcome.FAILED;
        });
    }

    private Outcome settle(Transaction pending, Command command) {
        PaymentResult result = command.result();
        String causationId = result.gatewayTransactionId();
        if (result.outcome() == PaymentOutcome.REJECTED) {
            Transaction failed = pending.fail(result, clock.instant());
            settlements.settle(failed, TransactionEvent.failed(eventIds.get(), failed, FailureReason.PAYMENT_FAILED,
                    command.correlationId(), causationId));
            return Outcome.FAILED;
        }
        Transaction confirmed;
        try {
            confirmed = pending.confirm(result, clock.instant());
        } catch (PaymentResultMismatchException mismatch) {
            return Outcome.REFUSED_BY_GUARD;
        }
        settlements.settle(confirmed, TransactionEvent.confirmed(eventIds.get(), confirmed, command.correlationId(),
                causationId));
        ledger.add(confirmed.ledgerEntry());
        return Outcome.CONFIRMED;
    }
}
