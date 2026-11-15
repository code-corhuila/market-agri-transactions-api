package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.adapter.in.http.Caller;
import co.edu.corhuila.marketagri.transactions.adapter.in.http.RequiredRole;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only routes that need a token: a user route, an ADMIN-only route (as E-44 and E-45 will
 * be) and an internal route. They answer the caller the interceptor left in the request. Test
 * sources only: never in the jar.
 */
@RestController
public class SecuredProbeController {

    @GetMapping("/probe/secured")
    public Map<String, String> user(@RequestAttribute(Caller.ATTRIBUTE) Caller caller) {
        return Map.of("subject", caller.subject(), "role", caller.role());
    }

    @RequiredRole("ADMIN")
    @GetMapping("/probe/admin")
    public Map<String, String> admin(@RequestAttribute(Caller.ATTRIBUTE) Caller caller) {
        return Map.of("subject", caller.subject(), "role", caller.role());
    }

    @GetMapping("/internal/v1/transactions/probe")
    public Map<String, String> internal(@RequestAttribute(Caller.ATTRIBUTE) Caller caller) {
        return Map.of("subject", caller.subject(), "role", caller.role());
    }
}
