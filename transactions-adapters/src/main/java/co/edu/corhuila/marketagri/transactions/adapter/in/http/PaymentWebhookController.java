package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult;
import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Outcome;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-43 (api-contract, amended 2026-11-04; ADR-019): anonymous, the checksum is the authentication.
 * Every validly signed event answers {@code 200 {received: true}}, also an unknown or settled
 * transaction (Wompi retries anything else); what happened goes to the log.
 */
@PublicEndpoint
@RestController
public class PaymentWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentWebhookController.class);
    private static final Map<String, Boolean> RECEIVED = Map.of("received", true);

    private final WompiEventReader reader;
    private final ApplyPaymentResult applyPaymentResult;

    public PaymentWebhookController(WompiEventReader reader, ApplyPaymentResult applyPaymentResult) {
        this.reader = reader;
        this.applyPaymentResult = applyPaymentResult;
    }

    @PostMapping("/api/v1/transactions/webhooks/payment-gateway")
    public Map<String, Boolean> receive(@RequestBody JsonNode event,
                                        @RequestHeader(name = "X-Event-Checksum", required = false) String checksum,
                                        @RequestAttribute(CorrelationFilter.HEADER) String correlationId) {
        if (!reader.signatureValid(event, checksum)) {
            LOG.warn("Payment event with a missing or wrong checksum ignored");
            throw new InvalidSignatureException();
        }
        reader.command(event, correlationId).ifPresentOrElse(command -> {
            Outcome outcome = applyPaymentResult.apply(command);
            if (outcome == Outcome.REFUSED_BY_GUARD) {
                LOG.warn("Security: payment event {} for transaction {} refused: {}", command.result().gatewayTransactionId(),
                        command.transactionId(), outcome);
            } else {
                LOG.info("Payment event {} for transaction {}: {}", command.result().gatewayTransactionId(),
                        command.transactionId(), outcome);
            }
        }, () -> LOG.info("Payment event {} ignored", event.path("event").asText()));
        return RECEIVED;
    }
}
