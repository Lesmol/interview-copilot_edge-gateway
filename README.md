# interview-copilot_edge-gateway

The only public entry point of Interview Co-Pilot. Spring Cloud Gateway (WebFlux) on Spring Boot 4.1 / Java 25.

- Validates the user's JWT (signature via JWKS, issuer, expiry, scope `interview-copilot/user`). All user authentication happens here.
- Strips any inbound `X-User-Id`, sets it from the token's `sub`, and does not forward the user's token.
- Per-user rate limit (in memory), `X-Request-Id`, CORS (only if the browser is not same-origin), unbuffered SSE pass-through.

| Route | Target | Required scope |
| --- | --- | --- |
| `/api/**` | `BFF_URL` | `interview-copilot/user` |
| `/actuator/health/**` | local | none |

Configuration is environment variables only; see `.env.example`. `OIDC_ISSUER_URI` is the public issuer used to check `iss`; `OIDC_JWKS_URI` is where keys are fetched (may be an internal URL).

```
./mvnw verify
```

Run the whole system with [interview-copilot_workbench](https://github.com/Lesmol/interview-copilot_workbench). Licence: Apache-2.0.
