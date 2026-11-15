package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.domain.model.DomainException;

/** E-43: the checksum is missing or does not match ({@code 400 INVALID_SIGNATURE}). */
final class InvalidSignatureException extends DomainException {

    InvalidSignatureException() {
        super("INVALID_SIGNATURE", "the event checksum is missing or does not match");
    }
}
