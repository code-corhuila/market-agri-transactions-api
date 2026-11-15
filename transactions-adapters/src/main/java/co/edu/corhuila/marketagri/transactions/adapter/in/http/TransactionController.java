package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.application.port.in.ReadTransactions;
import co.edu.corhuila.marketagri.transactions.application.port.out.LedgerEntries;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Participant;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public reads of transactions (api-contract §4.4): E-41 and E-42, for a buyer or a producer. */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final Set<String> MINE_PARAMETERS = Set.of("status", "page", "limit");
    private static final String BUYER = "BUYER";

    private final ReadTransactions read;
    private final LedgerEntries ledgerEntries;

    public TransactionController(ReadTransactions read, LedgerEntries ledgerEntries) {
        this.read = read;
        this.ledgerEntries = ledgerEntries;
    }

    /**
     * E-42: a buyer gets their purchases and a producer their sales, the side taken from the
     * token's role. The literal path wins over {@code /{id}}, so {@code mine} is never read as an id.
     */
    @RequiredRole({"BUYER", "PRODUCER"})
    @GetMapping("/mine")
    public PageView<TransactionView> mine(@RequestAttribute(Caller.ATTRIBUTE) Caller caller,
                                          @RequestParam Map<String, String> query) {
        QueryParameters.allowOnly(query, MINE_PARAMETERS);
        Participant side = BUYER.equals(caller.role()) ? Participant.BUYER : Participant.PRODUCER;
        TransactionFilter filter = new TransactionFilter(side, caller.userId(),
                QueryParameters.code(query, "status", TransactionStatus.class));
        var page = read.mine(filter, QueryParameters.pageRequest(query));
        Map<UUID, co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry> ledgerByTransaction =
                "PRODUCER".equals(caller.role()) ? ledgerEntries.findByTransactionIds(page.items().stream()
                        .map(Transaction::id).collect(Collectors.toSet())) : Map.of();
        return PageView.of(page, transaction -> viewOf(transaction, caller, ledgerByTransaction));
    }

    /** E-41: only a participant; an unknown id is {@code 404}, a malformed one {@code 400} (D-C23). */
    @RequiredRole({"BUYER", "PRODUCER"})
    @GetMapping("/{id}")
    public TransactionView get(@RequestAttribute(Caller.ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        Transaction transaction = read.get(id, caller.userId());
        Map<UUID, co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry> ledgerByTransaction =
                "PRODUCER".equals(caller.role()) ? ledgerEntries.findByTransactionIds(Set.of(transaction.id())) : Map.of();
        return viewOf(transaction, caller, ledgerByTransaction);
    }

    /** D-C14: a buyer must never receive the producer's disbursement record. */
    private static TransactionView viewOf(Transaction transaction, Caller caller,
                                          Map<UUID, co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry> ledgerByTransaction) {
        boolean producerReadsOwnSale = "PRODUCER".equals(caller.role())
                && transaction.purchase().producerId().equals(caller.userId());
        return TransactionView.of(transaction, producerReadsOwnSale ? ledgerByTransaction.get(transaction.id()) : null);
    }

    /** Kept here so that {@link PageRequest} limits and messages stay next to the routes that use them. */
    private static final class QueryParameters {

        static void allowOnly(Map<String, String> query, Set<String> allowed) {
            query.keySet().stream().filter(name -> !allowed.contains(name)).findFirst()
                    .ifPresent(name -> {
                        throw new InvalidParameterException(name, "Parámetro no permitido.");
                    });
        }

        static PageRequest pageRequest(Map<String, String> query) {
            int page = number(query, "page", PageRequest.DEFAULT_PAGE, Integer.MAX_VALUE,
                    "Debe ser un número entero mayor o igual a 1.");
            int limit = number(query, "limit", PageRequest.DEFAULT_LIMIT, PageRequest.MAX_LIMIT,
                    "Debe ser un número entero entre 1 y " + PageRequest.MAX_LIMIT + ".");
            return new PageRequest(page, limit);
        }

        /** An enum of the contract, exactly as written ({@code PENDING}, not {@code pending}); absent is {@code null}. */
        static <E extends Enum<E>> E code(Map<String, String> query, String name, Class<E> type) {
            String raw = query.get(name);
            if (raw == null) {
                return null;
            }
            try {
                return Enum.valueOf(type, raw);
            } catch (IllegalArgumentException unknown) {
                throw new InvalidParameterException(name, "Valor no permitido.");
            }
        }

        private static int number(Map<String, String> query, String name, int fallback, int max, String message) {
            String raw = query.get(name);
            if (raw == null) {
                return fallback;
            }
            try {
                int value = Integer.parseInt(raw.trim());
                if (value < 1 || value > max) {
                    throw new InvalidParameterException(name, message);
                }
                return value;
            } catch (NumberFormatException notANumber) {
                throw new InvalidParameterException(name, message);
            }
        }
    }
}
