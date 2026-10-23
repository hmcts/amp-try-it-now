package uk.gov.hmcts.amp.tin.scenarios;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The canonical form of a request, and the thing a recording is keyed on.
 *
 * <p>Deliberately NOT a named parameter. The estate has APIs taking one path parameter, two, three,
 * and two query parameters; two of them spell the same concept {@code case_urn} and {@code caseURN}.
 * Keying on the resolved request rather than on any one parameter works for all of them and needs no
 * per-API code.
 *
 * <p>The short code prefix is stripped, because it identifies the API and is already carried in
 * {@code api_id}. Query parameters are sorted so that the order a caller happens to send them in
 * cannot change the key.
 */
public final class RequestKey {

    private RequestKey() {
    }

    public static String of(final String shortCode, final String path, final String queryString) {
        final String withoutPrefix = stripPrefix(shortCode, path);
        final String normalisedPath = trimSlashes(withoutPrefix).toLowerCase(Locale.ROOT);
        final String query = normaliseQuery(queryString);
        return query.isEmpty() ? normalisedPath : normalisedPath + "?" + query;
    }

    private static String stripPrefix(final String shortCode, final String path) {
        final String trimmed = trimSlashes(path);
        final String prefix = shortCode.toLowerCase(Locale.ROOT);
        if (trimmed.toLowerCase(Locale.ROOT).equals(prefix)) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT).startsWith(prefix + "/")
                ? trimmed.substring(prefix.length() + 1)
                : trimmed;
    }

    private static String trimSlashes(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    /** Sorted, lower-cased, and decoded once so that %2F and / key the same. */
    private static String normaliseQuery(final String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return Arrays.stream(queryString.split("&"))
                .filter(pair -> !pair.isBlank())
                .map(RequestKey::normalisePair)
                .sorted()
                .collect(Collectors.joining("&"));
    }

    private static String normalisePair(final String pair) {
        final int eq = pair.indexOf('=');
        if (eq < 0) {
            return decode(pair).toLowerCase(Locale.ROOT);
        }
        return decode(pair.substring(0, eq)).toLowerCase(Locale.ROOT)
                + "=" + decode(pair.substring(eq + 1)).toLowerCase(Locale.ROOT);
    }

    private static String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
