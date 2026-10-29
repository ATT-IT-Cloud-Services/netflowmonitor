# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 1.x | ✅ |
| < 1.0 | ❌ |

## Reporting a vulnerability

Please report suspected vulnerabilities **privately** to
[service@att-it.com](mailto:service@att-it.com).

Include the affected repository and version/commit, impact, reproduction steps
and any suggested remediation. Do not open a public issue for an undisclosed
vulnerability. We aim to acknowledge reports within three business days.

## Scope

NetFlow v9 ingestion, parser memory safety, exporter authentication, metric exposure and denial-of-service resistance.

## General expectations

- Never commit API keys, passwords, tokens, private keys or production
  credentials. Use environment variables or a deployment secret store.
- Keep dependencies and base images up to date.
- Treat user-controlled input, repository content and webhook payloads as
  untrusted.
- Do not test against systems or data you do not own or have permission to test.

## Safe harbour

Good-faith research that respects this policy, avoids privacy violations, data
destruction and service disruption, and gives us reasonable time to remediate
will not be pursued by us.

© ATT IT / Philippe Simon Pflug. All rights reserved.
