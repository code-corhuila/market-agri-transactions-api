package co.edu.corhuila.marketagri.transactions.application.usecase;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult;
import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Outcome;
import co.edu.corhuila.marketagri.transactions.application.port.in.SettleExpiredPayments;
import co.edu.corhuila.marketagri.transactions.application.port.out.ExpiredPayments;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentLookup;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentLookupException;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The expiry job (E-43; FR-025, amended 2026-11-04): a payment PENDING {@link #GRACE} after its checkout
 * is checked by reference. A final result is applied as the webhook would; no payment, or a pending one,
 * fails it ({@code EXPIRED}); no answer leaves it PENDING until the next run.
 */
public final class SettleExpiredPaymentsService implements SettleExpiredPayments {

    /** The webhook gets five minutes after the checkout ends before the provider is asked. */
    public static final Duration GRACE = Duration.ofMinutes(5);
    private static final String CORRELATION_ID = "expiry-job";

    private final ExpiredPayments expired;
    private final PaymentLookup lookup;
    private final ApplyPaymentResult applyPaymentResult;
    private final Clock clock;
    private final int batchSize;

    public SettleExpiredPaymentsService(ExpiredPayments expired, PaymentLookup lookup,
                                        ApplyPaymentResult applyPaymentResult, Clock clock, int batchSize) {
        this.expired = expired;
        this.lookup = lookup;
        this.applyPaymentResult = applyPaymentResult;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public int settleExpired() {
        int settled = 0;
        for (UUID id : expired.pendingExpiredBefore(clock.instant().minus(GRACE), batchSize)) {
            try {
                settled += settle(id, lookup.findByReference(id)) ? 1 : 0;
            } catch (PaymentLookupException noAnswer) {
                // No answer from the provider: it stays PENDING until the next run.
            }
        }
        return settled;
    }

    private boolean settle(UUID id, Optional<PaymentResult> result) {
        Outcome outcome = result.isPresent() && result.get().outcome() != PaymentOutcome.PENDING
                ? applyPaymentResult.apply(new ApplyPaymentResult.Command(id, result.get(), CORRELATION_ID))
                : applyPaymentResult.expire(id, CORRELATION_ID);
        return outcome == Outcome.CONFIRMED || outcome == Outcome.FAILED;
    }
}
