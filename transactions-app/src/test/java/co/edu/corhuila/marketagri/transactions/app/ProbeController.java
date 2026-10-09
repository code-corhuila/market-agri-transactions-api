package co.edu.corhuila.marketagri.transactions.app;

import java.util.Map;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only routes that exercise the error body before the real endpoints exist (D3–D5).
 * It lives in the test sources: it never reaches the jar.
 */
@RestController
public class ProbeController {

    public record ProbeRequest(@NotBlank(message = "Es obligatorio.") String reference,
                        @NotNull(message = "Es obligatorio.") Long amountCents) {
    }

    @PostMapping("/probe")
    public Map<String, String> create(@RequestHeader("Idempotency-Key") String key,
                               @Valid @RequestBody ProbeRequest request) {
        return Map.of("reference", request.reference());
    }

    @GetMapping("/probe/{id}")
    public Map<String, String> read(@PathVariable UUID id) {
        return Map.of("id", id.toString());
    }

    @GetMapping("/probe/failure")
    public Map<String, String> failure() {
        throw new IllegalStateException("SELECT * FROM transactions.secret_table");
    }
}
