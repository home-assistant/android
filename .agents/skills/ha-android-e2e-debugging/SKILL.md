---
name: ha-android-e2e-debugging
description: Home Assistant Android end-to-end (Maestro) failure triage. Use when the E2E workflow fails, when reading its artifacts (Maestro report, logcat, Home Assistant logs), or when deciding whether a failure comes from the app, from Home Assistant, or from flakiness.
---

# HA Android E2E Debugging

The `E2E` workflow (`.github/workflows/e2e.yml`) runs the `.maestro/onboarding.yaml` flow every night at 05:00 UTC. It starts a Home Assistant container (the `dev` image unless the run overrides it), boots one emulator per API level from 29 up to `androidSdk-target`, installs `app-full-debug.apk` on each, and shards the same flow across all of them. Every shard talks to that one container.

Read the sources in section 2 in order and stop at the first one that explains the failure, then always write the report in section 3. Once a source explains it, skip the rest.

## 1. Collect the artifacts

```bash
gh run download <run-id> --name e2e-artifacts --dir e2e-artifacts
```

| Path | Content |
| --- | --- |
| `maestro-results/<timestamp>/onboarding-shard-<N>/` | One directory per shard: `commands.json` trace, `logs/maestro.log`, `logs/device-logcat.txt`, and `screenshots/step-*.png` with `screen-hierarchy/step-*.json` captured at each tap and at the failure |
| `logcat-api<N>-<serial>.txt` | Full device logcat, one file per emulator, named by API level |
| `homeassistant.log` | Timestamped container log |
| `homeassistant-config.json` | `/api/config` response: Home Assistant `version`, loaded `components` |
| `homeassistant-container.json` | `docker inspect` of the container: image digest and labels |

## 2. Sources

### 2.1 Maestro report

Find the failed command and its shard in one pass:

```bash
jq -c 'to_entries[] | select(.value.metadata.status == "FAILED") | {shard: input_filename, step: (.key + 1), command: .value.command, error: .value.metadata.error.message}' \
  e2e-artifacts/maestro-results/*/onboarding-shard-*/commands.json
```

`step` is the zero-padded number in that shard's `screenshots/step-<step>-*.png` and `screen-hierarchy/step-<step>-*.json`; open both. An element visible in the screenshot but absent from the hierarchy never reached the accessibility tree; a broken screen shows in the screenshot itself.

### 2.2 logcat

Only when 2.1 explains nothing. Shard numbers are not API levels. The shard's `logs/device-logcat.txt` is the same device as one `logcat-api<N>-*.txt` in another format, so match them on a message: take an early line of the shard's logcat, strip everything up to the first `): `, and `grep -l -F` the rest across `logcat-api*.txt`. Useful filters on the matched file:

```bash
grep -nE 'AndroidRuntime|FATAL' e2e-artifacts/logcat-api<N>-*.txt | tail -40
grep -nE 'io\.homeassistant|chromium|WebSocket|okhttp' e2e-artifacts/logcat-api<N>-*.txt | tail -40
```

Look for a crash or ANR at the failure timestamp, TLS or DNS errors reaching `homeassistant.internal`, WebSocket disconnects, and `chromium` renderer errors.

### 2.3 Home Assistant logs

Only when 2.1 and 2.2 explain nothing.

```bash
grep -nE ' (ERROR|WARNING) ' e2e-artifacts/homeassistant.log | tail -40
jq -c '{version, mobile_app: (.components | index("mobile_app") != null)}' e2e-artifacts/homeassistant-config.json
jq -r '.[0].Config.Image' e2e-artifacts/homeassistant-container.json
```

Read the errors around the failure time, and confirm `mobile_app` is loaded: the workflow verifies it at startup, but a later integration failure can still break onboarding. An error that the same grep also finds in `e2e-artifacts-last-green/homeassistant.log` did not break this run. The `frontend.js.*` logger name carries the frontend build date, useful for the next step.

### 2.4 Upstream: core and frontend

Only when 2.1 to 2.3 explain nothing. A typical upstream symptom is a clean split by API level: every shard below some API level fails at the same step while every shard above it passes. The frontend ships one bundle to all WebViews, so a change that relies on a newer web feature breaks the older WebViews together.

The `dev` image moves every night, so the useful comparison is against the last run that passed. The triage workflow downloads it into `e2e-artifacts-last-green/` before the agent starts; only when working locally, fetch it yourself:

```bash
gh run list --workflow=e2e.yml --status success --limit 1 --json databaseId,createdAt
gh run download <green-run-id> --name e2e-artifacts --dir e2e-artifacts-last-green
```

Diff the two `homeassistant-config.json` files to get the Home Assistant versions on either side of the break, then look at what landed between those dates:

- [`home-assistant/core`](https://github.com/home-assistant/core).
- [`home-assistant/frontend`](https://github.com/home-assistant/frontend).

## 3. Report

Everything lands in the `e2e-failure` issue in this repository: a comment when one is already open, a new issue otherwise. Never open anything on another repository; an upstream finding goes in the same comment for a maintainer to carry over.

Write:

1. **Verdict** — Say how confident you are and what would confirm it.
2. **Evidence** — the failing Maestro command, the logcat and Home Assistant log lines the verdict rests on, and the Home Assistant versions of the failed and the last green run, read from the two `homeassistant-config.json` files. Quote the lines, don't paraphrase them.
3. **Culprit commit** — only when you went through 2.4. Give it as `owner/repo@sha` with its title and date, and say what in it explains the failure. When you only narrowed it to a range, give the range and say it is a range. When you stopped before 2.4, say so in one line.
4. **Proposed fix** — a concrete patch: name the file and the change, whether here, in core, or in the frontend, so a maintainer can apply or forward it. When the right answer is to adapt the Maestro flow to an intentional upstream change, say that.
5. **A link to the failed run.**

Say plainly when the evidence supports no verdict, and list what you checked. A short honest report beats a confident guess.
