package co.edu.corhuila.marketagri.transactions.application.usecase;

import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentGatewayException;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionMismatchException;
import java.net.URI;
import java.time.Clock;

/**
 * I-06 as D-C35 describes it: the payment and its key are inserted in one transaction; if the id
 * or the key was already used, the transaction rolls back and the original is compared with the
 * request (same data → the original with {@code 200}, other data → {@code TRANSACTION_MISMATCH}).
 * The checkout link is built before storing, so a payment whose link cannot be built is stored
 * {@code FAILED} instead of {@code PENDING} (I-06 row 4).
 */
public final class CreatePaymentService implements CreatePayment {

    private final TransactionCreations creations;
    private final PaymentGateway gateway;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreatePaymentService(TransactionCreations creations, PaymentGateway gateway, UnitOfWork unitOfWork,
                                Clock clock) {
        this.creations = creations;
        this.gateway = gateway;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Payment create(Command command) {
        PurchaseSnapshot purchase = command.purchase();
        Transaction opened = Transaction.open(command.transactionId(), purchase, clock.instant());
        URI checkoutUrl = null;
        PaymentGatewayException gatewayError = null;
        try {
            checkoutUrl = gateway.checkoutUrl(opened);
        } catch (PaymentGatewayException refused) {
            gatewayError = refused;
        }
        Transaction toStore = gatewayError == null ? opened : opened.failedToOpen();
        try {
            unitOfWork.inTransaction(() -> {
                if (!creations.add(toStore)
                        || !creations.claimKey(command.callerId(), command.idempotencyKey(), toStore.id())) {
                    throw new AlreadyCreated();
                }
                return toStore;
            });
        } catch (AlreadyCreated replay) {
            return replay(command, purchase);
        }
        if (gatewayError != null) {
            throw gatewayError;
        }
        return new Payment(toStore, checkoutUrl, true);
    }

    private Payment replay(Command command, PurchaseSnapshot purchase) {
        Transaction original = creations.transactionForKey(command.callerId(), command.idempotencyKey())
                .orElseThrow(() -> new TransactionMismatchException(command.transactionId()));
        if (!original.id().equals(command.transactionId()) || !original.purchase().equals(purchase)) {
            throw new TransactionMismatchException(command.transactionId());
        }
        if (original.neverOpened()) {
            throw new PaymentGatewayException("the original payment could not be opened: " + original.id());
        }
        return new Payment(original, gateway.checkoutUrl(original), false);
    }

    /** Only rolls the unit of work back; never leaves this class. */
    private static final class AlreadyCreated extends RuntimeException {
        AlreadyCreated() {
            super(null, null, false, false);
        }
    }
}
