package com.petroad.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    private final Set<String> trustedProxies;

    public ClientIpResolver(LoginThrottleProperties properties) {
        trustedProxies = properties.trustedProxyAddresses().stream()
                .filter(address -> !address.isBlank()).map(ClientIpResolver::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String peer = normalize(request.getRemoteAddr());
        if (!trustedProxies.contains(peer)) return peer;
        String forwarded = request.getHeader("CF-Connecting-IP");
        return forwarded == null ? peer : normalize(forwarded);
    }

    private static String normalize(String address) {
        // Only numeric addresses: never resolve untrusted header values through DNS.
        if (address == null || !(address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                || (address.contains(":") && address.matches("[0-9a-fA-F:.]+")))) {
            throw new IllegalArgumentException("올바르지 않은 클라이언트 IP 주소입니다.");
        }
        try { return InetAddress.getByName(address).getHostAddress(); }
        catch (UnknownHostException e) { throw new IllegalArgumentException("올바르지 않은 클라이언트 IP 주소입니다."); }
    }
}
