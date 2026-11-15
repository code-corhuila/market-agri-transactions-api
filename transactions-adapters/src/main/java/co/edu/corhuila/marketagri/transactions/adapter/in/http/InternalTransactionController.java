package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API of transactions (api-contract §4.4): I-06. Only a {@code SERVICE} token gets here
 * ({@link AuthInterceptor}, D-C27), and the gateway never routes {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/v1/transactions")
public class InternalTransactionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int KEY_MIN = 8;
    private static final int KEY_MAX = 128;

    private final CreatePayment createPayment;

    public InternalTransactionController(CreatePayment createPayment) {
        this.createPayment = createPayment;
    }

    /** I-06 (checkout saga, step 3): {@code 201} the first time, {@code 200} with the original on a replay. */
    @PostMapping
    public ResponseEntity<CheckoutView> create(@RequestAttribute(Caller.ATTRIBUTE) Caller caller,
                                               @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                               @Valid @RequestBody CreatePaymentRequest body) {
        if (idempotencyKey.length() < KEY_MIN || idempotencyKey.length() > KEY_MAX) {
            throw new InvalidParameterException(IDEMPOTENCY_KEY,
                    "Debe tener entre " + KEY_MIN + " y " + KEY_MAX + " caracteres.");
        }
        CreatePayment.Payment payment = createPayment.create(body.toCommand(caller.ownerId(), idempotencyKey));
        return ResponseEntity.status(payment.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new CheckoutView(TransactionView.of(payment.transaction()), payment.checkoutUrl().toString()));
    }

    /** Response of I-06 and E-40: the payment and where the buyer pays it. */
    public record CheckoutView(TransactionView transaction, String checkoutUrl) {
    }
}
