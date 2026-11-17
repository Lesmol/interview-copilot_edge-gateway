# Decisions

- **In-memory fixed-window rate limiter per user** (default 600 requests/minute). Fine for one instance; use a shared store (for example Redis) when scaling out.
- CORS is off by default (`CORS_ALLOWED_ORIGINS` empty) because the frontend container proxies `/api` same-origin.
- **Public routes:** only `POST /api/v1/accounts/**` is reachable without a token (sign-up and password reset). `X-User-Id` and `Authorization` are stripped on every route, so a client cannot smuggle an identity through a public path. A malformed bearer token is still rejected with 401 there.
- **Rate limits:** per user, plus 20 requests per minute per client address on the public account routes. The client address is the last `X-Forwarded-For` entry, which the frontend proxy appends.
- Only `/api/v1/**` is routed; other versions are a 404 until deployed.
