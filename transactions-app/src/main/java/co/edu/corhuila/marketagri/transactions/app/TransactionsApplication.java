package co.edu.corhuila.marketagri.transactions.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Scans the app and the adapters; the core carries no Spring annotation,
 * so its types are instantiated explicitly in {@link TransactionsConfiguration}.
 */
@SpringBootApplication(scanBasePackages = {
        "co.edu.corhuila.marketagri.transactions.app",
        "co.edu.corhuila.marketagri.transactions.adapter"})
public class TransactionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionsApplication.class, args);
    }
}
