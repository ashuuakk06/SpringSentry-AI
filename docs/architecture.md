# SpringSentry AI — current architecture

```text
React + TypeScript (5173)
        │ /api proxy
        ▼
Spring Boot REST API (8081)
  ├─ BCrypt registration and JWT authentication
  ├─ analysis service ── BharatCode API (optional server-side key)
  ├─ H2 file database (default) / PostgreSQL (optional profile)
  └─ fixed sample test runner ── sample-projects/calculator
```

The backend owns the provider key and analysis persistence. The browser receives only an analysis response and a short-lived account JWT. Demo mode is used only when no provider key is configured. When a configured provider call fails, the backend returns an error rather than substituting a demo answer.

The test runner accepts no project path or command from a request. It runs only `mvnw -B test` in the bundled calculator sample, with a 120-second limit. The sample test result is kept separate from the submitted analysis and does not verify its suggested fix.
