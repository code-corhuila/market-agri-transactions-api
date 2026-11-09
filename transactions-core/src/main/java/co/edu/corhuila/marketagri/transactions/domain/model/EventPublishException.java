package co.edu.corhuila.marketagri.transactions.domain.model;

/** The broker did not confirm an event (unreachable, refused, or no confirm in time): it stays in the outbox. */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
