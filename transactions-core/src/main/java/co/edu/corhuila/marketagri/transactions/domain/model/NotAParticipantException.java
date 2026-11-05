package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** E-41 row 2: the caller is neither the buyer nor the producer of the payment. */
public class NotAParticipantException extends DomainException {

    public NotAParticipantException(UUID transactionId) {
        super("NOT_A_PARTICIPANT", "caller is not a participant of transaction " + transactionId);
    }
}
