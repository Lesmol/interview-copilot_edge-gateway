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

    RateLimitFilter(@Value("${edge.rate-limit.requests-per-minute:600}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> auth.getName())
                .defaultIfEmpty("anonymous")
                .flatMap(user -> allow(user)
                        ? chain.filter(exchange)
                        : tooManyRequests(exchange));
    }

    private boolean allow(String user) {
        long minute = System.currentTimeMillis() / 60_000;
        Window window = windows.compute(user, (k, w) -> w == null || w.minute() != minute
                ? new Window(minute, new AtomicInteger()) : w);
        return window.count().incrementAndGet() <= requestsPerMinute;
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
