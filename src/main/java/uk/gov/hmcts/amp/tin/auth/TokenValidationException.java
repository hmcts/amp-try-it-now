package uk.gov.hmcts.amp.tin.auth;

import lombok.Getter;

/** A refusal, carrying enough to answer with the right status and nothing a caller could abuse. */
@Getter
public class TokenValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public TokenValidationException(final Reason reason, final String detail) {
        super(detail);
        this.reason = reason;
    }

    public enum Reason {
        MISSING_TOKEN("invalid_request", "No bearer token was presented.", true),
        MALFORMED_TOKEN("invalid_token", "The token could not be read.", true),
        UNTRUSTED_SIGNATURE("invalid_token", "The token signature did not verify.", true),
        WRONG_AUDIENCE("invalid_token", "The token was minted for another resource.", true),
        WRONG_ISSUER("invalid_token", "The token was not issued by the expected tenant.", true),
        EXPIRED("invalid_token", "The token has expired.", true),
        UNKNOWN_API("not_found", "No API is registered at this path.", true),
        /** Authenticated and unentitled - a 403, not a 401. */
        MISSING_ROLE("insufficient_scope", "The token carries no role for this API.", false);

        private final String errorCode;
        private final String description;
        private final boolean authenticationFailure;

        Reason(final String errorCode, final String description, final boolean authenticationFailure) {
            this.errorCode = errorCode;
            this.description = description;
            this.authenticationFailure = authenticationFailure;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getDescription() {
            return description;
        }

        public boolean isAuthenticationFailure() {
            return authenticationFailure;
        }
    }
}
