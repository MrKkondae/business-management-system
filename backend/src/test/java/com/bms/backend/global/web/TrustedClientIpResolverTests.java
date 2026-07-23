package com.bms.backend.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientIpResolverTests {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void walksForwardedChainFromTrustedProxyToFirstUntrustedClient() {
        TrustedClientIpResolver resolver =
                new TrustedClientIpResolver("10.0.0.0/8,192.168.0.0/16");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader(
                "X-Forwarded-For", "198.51.100.20, 192.168.1.20, 10.0.0.3");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void rejectsMalformedForwardedChain() {
        TrustedClientIpResolver resolver = new TrustedClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "attacker.example");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.2");
    }
}
