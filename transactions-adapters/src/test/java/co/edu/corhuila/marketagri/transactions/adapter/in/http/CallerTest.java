package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The owner of an Idempotency-Key: a service sub (ADR-020) is not a UUID, so it is turned into one. */
class CallerTest {

    @Test
    void theSameServiceAlwaysOwnsItsKeysUnderTheSameUuid() {
        Caller workflow = new Caller("service:workflow", "SERVICE", null);

        assertThat(workflow.ownerId()).isEqualTo(new Caller("service:workflow", "SERVICE", null).ownerId());
        assertThat(workflow.ownerId()).isNotEqualTo(new Caller("service:chat", "SERVICE", null).ownerId());
        assertThat(workflow.ownerId().version()).isEqualTo(3);
    }
}
