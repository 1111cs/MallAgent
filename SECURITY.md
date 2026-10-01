# Security Policy

## Secrets

Do not commit model API keys, database credentials, cookies, login tokens, signed URLs or runtime HMAC secrets. Use environment variables and keep local `.env` files outside version control.

The values in `.env.example` are placeholders. The fixed users, orders and devices under `fixtures/` are synthetic test data.

## Reporting

If you discover a vulnerability, avoid publishing credentials or exploit details in a public issue. Contact the maintainer at `a123123dd@yeah.net` with a minimal reproduction and affected version.

## Scope

MallAgent is an educational and portfolio project. It is not connected to Huawei production systems and must not be used as evidence of access to Huawei internal services or data.

