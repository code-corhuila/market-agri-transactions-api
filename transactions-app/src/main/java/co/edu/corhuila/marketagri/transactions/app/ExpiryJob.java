package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.application.port.in.SettleExpiredPayments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the expiry job every {@code payment.expiry.interval} (FR-025); off with {@code payment.expiry.enabled=false}. */
@Component
@ConditionalOnProperty(name = "payment.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiryJob.class);

    private final SettleExpiredPayments job;

    public ExpiryJob(SettleExpiredPayments job) {
        this.job = job;
    }

    @Scheduled(fixedDelayString = "${payment.expiry.interval}")
    public void run() {
        try {
            int settled = job.settleExpired();
            if (settled > 0) {
                LOG.info("Expiry job settled {} expired payments", settled);
            }
        } catch (RuntimeException failure) {
            LOG.warn("Expiry job run failed: {}", failure.toString());
        }
    }
}
