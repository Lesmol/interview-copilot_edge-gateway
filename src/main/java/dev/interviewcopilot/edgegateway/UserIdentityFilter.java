package dev.interviewcopilot.edgegateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Drops any client-supplied {@code X-User-Id}, sets it from the token's {@code sub},
 * and does not forward the user's token to the BFF.
 */
@Component
class UserIdentityFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(auth -> ((Jwt) auth.getPrincipal()).getSubject())
                .map(subject -> forward(exchange, subject))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    private static ServerWebExchange forward(ServerWebExchange exchange, String subject) {
        var request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID_HEADER);
                    h.remove(HttpHeaders.AUTHORIZATION);
                    h.set(USER_ID_HEADER, subject);
                }).build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
