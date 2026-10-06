package com.gis.servelq.configs;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CachingRequestWrapperTest {

    private static final String JSON = "{\"name\":\"مرحبا بكم\"}";

    @Test
    void keepsUtf8BodyIntactWhenContainerDefaultsToLatin1() throws Exception {
        // Tomcat's default when the client sends "application/json" without a
        // charset and CharacterEncodingFilter has not run yet.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        request.setContentType("application/json");
        request.setCharacterEncoding("ISO-8859-1");
        request.setContent(JSON.getBytes(StandardCharsets.UTF_8));

        CachingRequestWrapper wrapper = new CachingRequestWrapper(request);
        // Later in the chain CharacterEncodingFilter forces UTF-8.
        wrapper.setCharacterEncoding("UTF-8");

        assertThat(wrapper.getInputStream().readAllBytes()).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(wrapper.getReader().readLine()).isEqualTo(JSON);
    }
}
