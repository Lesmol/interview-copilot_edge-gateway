# Decisions

- **In-memory fixed-window rate limiter per user** (default 600 requests/minute). Fine for one instance; use a shared store (for example Redis) when scaling out.
- CORS is off by default (`CORS_ALLOWED_ORIGINS` empty) because the frontend container proxies `/api` same-origin.
