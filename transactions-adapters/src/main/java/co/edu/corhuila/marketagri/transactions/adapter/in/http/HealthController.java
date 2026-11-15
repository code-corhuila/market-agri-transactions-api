package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness (api-contract E-60): checks nothing but the process, so a database outage never
 * makes the platform restart a healthy container. Readiness, with the database and the broker,
 * is {@code GET /health/ready} (E-61) and arrives with persistence (D3).
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
