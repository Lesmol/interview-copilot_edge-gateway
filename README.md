# interview-copilot_edge-gateway

The only public entry point of Interview Co-Pilot. Spring Cloud Gateway (WebFlux) on Spring Boot 4.1 / Java 25.

- Validates the user's JWT (signature via JWKS, issuer, expiry, scope `interview-copilot/user`). All user authentication happens here.
- Strips any inbound `X-User-Id`, sets it from the token's `sub`, and does not forward the user's token.
- Rate limits (in memory): per user, and a stricter per-client-address limit on the public account routes. Only `/api/v1/**` is routed.
- Per-user rate limit (in memory), `X-Request-Id`, CORS (only if the browser is not same-origin), unbuffered SSE pass-through.

| Route | Target | Required scope |
| --- | --- | --- |
| `/api/v1/**` | `BFF_URL` | `interview-copilot/user` |
| `POST /api/v1/accounts/**` | `BFF_URL` | none (sign-up and password reset; stricter per-address rate limit) |
| `/actuator/health/**` | local | none |

Configuration is environment variables only; see `.env.example`. `OIDC_ISSUER_URI` is the public issuer used to check `iss`; `OIDC_JWKS_URI` is where keys are fetched (may be an internal URL).

```
./mvnw verify
```

Run the whole system with [interview-copilot_workbench](https://github.com/Lesmol/interview-copilot_workbench). Licence: Apache-2.0.

## API versioning

Every HTTP API is versioned in the path: `/api/v1/...`. The version in the path is the contract version, separate from the artifact version. Each OpenAPI document carries the same contract version in `info.version` (currently `1.0.0`).

- Compatible changes (new endpoints, new optional fields) keep `/api/v1` and bump the minor or patch of `info.version`.
- A breaking change adds `/api/v2` next to `/api/v1`; both are served until consumers have moved, and the old one is removed in a later release.
- The gateways route only `/api/v1/**`, so a version nobody has deployed yet is a clean 404 at the edge, not a surprise further in.
