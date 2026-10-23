package uk.gov.hmcts.amp.tin.scenarios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestKeyTest {

    @Test
    @DisplayName("strips the short code, so the key is the request within an API")
    void stripsShortCode() {
        assertThat(RequestKey.from("slc", "/slc/case/TIN-ALLOCATED-01/courtschedule", null))
                .isEqualTo("case/tin-allocated-01/courtschedule");
    }

    @Test
    @DisplayName("keys a nested path with two parameters, which rcc needs")
    void nestedPath() {
        assertThat(RequestKey.from("rcc", "/rcc/courthouses/C1/courtrooms/R2", null))
                .isEqualTo("courthouses/c1/courtrooms/r2");
    }

    @Test
    @DisplayName("sorts query parameters, so the order a caller sends them cannot change the key")
    void sortsQuery() {
        assertThat(RequestKey.from("dl", "/dl/defendants/cases/ABC", "page=1&include=x"))
                .isEqualTo(RequestKey.from("dl", "/dl/defendants/cases/ABC", "include=x&page=1"));
    }

    @Test
    @DisplayName("two APIs using the same text produce the same key - api_id is what separates them")
    void sameTextAcrossApis() {
        final String slc = RequestKey.from("slc", "/slc/TIN-ALLOCATED-01", null);
        final String rcc = RequestKey.from("rcc", "/rcc/TIN-ALLOCATED-01", null);
        assertThat(slc).isEqualTo(rcc);
    }

    @Test
    @DisplayName("tolerates trailing slashes and mixed case")
    void normalises() {
        assertThat(RequestKey.from("SLC", "/slc/Case/ABC/CourtSchedule/", null))
                .isEqualTo("case/abc/courtschedule");
    }
}
