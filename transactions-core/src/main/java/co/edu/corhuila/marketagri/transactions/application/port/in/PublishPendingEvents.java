package co.edu.corhuila.marketagri.transactions.application.port.in;

/** One run of the outbox relay (ADR-014): publishes what is waiting, in order. */
public interface PublishPendingEvents {

    /** How many events were published and marked in this run. */
    int publishPending();
}
