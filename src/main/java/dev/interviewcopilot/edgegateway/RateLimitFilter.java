package dev.interviewcopilot.edgegateway;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Per-user fixed-window limiter, in memory (one instance). Replace with a shared store when scaling out. */
@Component
class RateLimitFilter implements GlobalFilter, Ordered {

    private record Window(long minute, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    private final int publicRequestsPerMinute;

    RateLimitFilter(@Value("${edge.rate-limit.requests-per-minute:600}") int requestsPerMinute,
                    @Value("${edge.rate-limit.public-requests-per-minute:20}") int publicRequestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
        this.publicRequestsPerMinute = publicRequestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean publicRoute = exchange.getRequest().getPath().value().startsWith("/api/v1/accounts/");
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> "user:" + auth.getName())
                .defaultIfEmpty("ip:" + clientAddress(exchange))
                .flatMap(key -> allow(publicRoute ? "public:" + key : key, publicRoute ? publicRequestsPerMinute : requestsPerMinute)
                        ? chain.filter(exchange)
                        : tooManyRequests(exchange));
    }

    /** The frontend proxy appends the real client address to X-Forwarded-For; the last entry is the one it added. */
    private static String clientAddress(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private boolean allow(String key, int limit) {
        long minute = System.currentTimeMillis() / 60_000;
        Window window = windows.compute(key, (k, w) -> w == null || w.minute() != minute
                ? new Window(minute, new AtomicInteger()) : w);
        return window.count().incrementAndGet() <= limit;
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
