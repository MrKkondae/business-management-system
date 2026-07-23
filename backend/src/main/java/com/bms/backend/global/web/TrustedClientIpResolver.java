package com.bms.backend.global.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class TrustedClientIpResolver {

    private static final int MAX_FORWARDED_FOR_LENGTH = 1_024;

    private final List<IpAddressMatcher> trustedProxies;

    public TrustedClientIpResolver(
            @Value("${bms.web.trusted-proxies:}") String trustedProxyRanges) {
        this.trustedProxies = Arrays.stream(trustedProxyRanges.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeIp(request.getRemoteAddr());
        if (remoteAddress == null || !isTrusted(remoteAddress)) {
            return remoteAddress == null ? "" : remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null
                || forwardedFor.isBlank()
                || forwardedFor.length() > MAX_FORWARDED_FOR_LENGTH) {
            return remoteAddress;
        }

        List<String> chain = new ArrayList<>();
        for (String value : forwardedFor.split(",")) {
            String address = normalizeIp(value);
            if (address == null) {
                return remoteAddress;
            }
            chain.add(address);
        }
        chain.add(remoteAddress);

        for (int index = chain.size() - 1; index >= 0; index--) {
            String address = chain.get(index);
            if (!isTrusted(address)) {
                return address;
            }
        }
        return chain.get(0);
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String normalizeIp(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()
                || candidate.length() > 45
                || !candidate.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            new IpAddressMatcher(candidate);
            return candidate;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
