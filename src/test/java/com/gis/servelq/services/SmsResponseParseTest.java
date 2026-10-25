package com.gis.servelq.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsResponseParseTest {

    private final SmsService service = new SmsService(null, null, null, new ObjectMapper(), null);

    @Test
    void readsDocumentedShape() {
        SmsResponse r = service.parseResponse(
                "{\"StatusCode\":\"00\",\"StatusDesc\":\"Success\",\"Proccessed\":1,\"BatchRefCode\":\"170503\"}");
        assertThat(r.getStatusCode()).isEqualTo("00");
        assertThat(r.getBatchRefCode()).isEqualTo("170503");
        assertThat(r.getProcessed()).isEqualTo(1);
    }

    @Test
    void readsLowerCaseKeys() {
        SmsResponse r = service.parseResponse("{\"statusCode\":\"01\",\"statusDesc\":\"Invalid Username /Password\"}");
        assertThat(r.getStatusCode()).isEqualTo("01");
        assertThat(r.getStatusDesc()).isEqualTo("Invalid Username /Password");
    }

    @Test
    void readsJsonEncodedAsString() {
        SmsResponse r = service.parseResponse("\"{\\\"StatusCode\\\":\\\"00\\\",\\\"BatchRefCode\\\":\\\"9\\\"}\"");
        assertThat(r.getStatusCode()).isEqualTo("00");
        assertThat(r.getBatchRefCode()).isEqualTo("9");
    }

    @Test
    void readsAspNetWrapper() {
        SmsResponse r = service.parseResponse("{\"d\":{\"StatusCode\":\"08\"}}");
        assertThat(r.getStatusCode()).isEqualTo("08");
    }

    @Test
    void unknownBodyIsNull() {
        assertThat(service.parseResponse("<html>error</html>")).isNull();
        assertThat(service.parseResponse("{\"Message\":\"An error has occurred.\"}")).isNull();
        assertThat(service.parseResponse("")).isNull();
    }
}
