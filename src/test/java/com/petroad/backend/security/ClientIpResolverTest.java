package com.petroad.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClientIpResolverTest {
    private ClientIpResolver resolver(List<String> proxies) {
        return new ClientIpResolver(new LoginThrottleProperties(5, Duration.ofMinutes(15), Duration.ofMinutes(15),
                30, Duration.ofMinutes(1), 100, proxies));
    }

    @Test void untrustedPeerCannotSpoofAnyForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("CF-Connecting-IP", "192.0.2.20");
        request.addHeader("X-Forwarded-For", "192.0.2.30");
        assertThat(resolver(List.of()).resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test void explicitlyTrustedProxyUsesCloudflareClientAddressAndNormalizesIpv6() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "2001:db8::1");
        assertThat(resolver(List.of("127.0.0.1")).resolve(request)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "face", "192.0.2.1,192.0.2.2", "999.1.2.3", "", "::invalid"})
    void malformedTrustedHeaderIsRejectedWithoutDnsLookup(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("CF-Connecting-IP", address);
        assertThatThrownBy(() -> resolver(List.of("127.0.0.1")).resolve(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
