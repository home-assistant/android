---
name: ha-android-e2e-debugging
description: Home Assistant Android end-to-end (Maestro) failure triage. Use when the E2E workflow fails, when reading its artifacts (Maestro report, logcat, Home Assistant logs), or when deciding whether a failure comes from the app, from Home Assistant, or from flakiness.
---

# HA Android E2E Debugging

The `E2E` workflow (`.github/workflows/e2e.yml`) runs the `.maestro/onboarding.yaml` flow every night at 05:00 UTC. It starts a Home Assistant container (the `dev` image unless the run overrides it), boots one emulator per API level from 29 up to `androidSdk-target`, installs `app-full-debug.apk` on each, and shards the same flow across all of them. Every shard talks to that one container.

Read the sources in the order below and stop at the first one that explains the failure. Most failures are already visible in the failing Maestro command; opening the Home Assistant log first usually wastes time.

## 0. Collect the artifacts

```bash
gh run download <run-id> --name e2e-artifacts --dir e2e
```

| Path | Content |
| --- | --- |
| `maestro-results/` | Per-shard `commands-*.json` trace, `maestro.log`, and `screenshot-*.png` captured on failure |
| `logcat-api<N>-<serial>.txt` | Full device logcat, one file per emulator, named by API level |
| `homeassistant.log` | Timestamped container log |
| `homeassistant-config.json` | `/api/config` response: Home Assistant `version`, loaded `components` |
| `homeassistant-container.json` | `docker inspect` of the container: image digest and labels |

## 1. Maestro report

Find the first command whose status is `FAILED` in `maestro-results/commands-*.json`, then read `maestro.log` around it and open the `screenshot-*.png` taken at that point. The screenshot is the fastest way to tell a genuinely broken screen from an element that merely never reached the accessibility tree.

## 2. logcat

Match the failing shard's API level to `logcat-api<N>-*.txt`. Useful filters:

```bash
grep -nE 'AndroidRuntime|FATAL|E ' e2e/logcat-api30-*.txt
grep -nE 'io\.homeassistant|chromium|WebSocket|okhttp' e2e/logcat-api30-*.txt
```

Look for a crash or ANR at the failure timestamp, TLS or DNS errors reaching `homeassistant.internal`, WebSocket disconnects, and `chromium` renderer errors.

## 3. Home Assistant logs

Check `homeassistant.log` for errors at the same timestamp, and confirm in `homeassistant-config.json` that `mobile_app` is in `components` — the workflow verifies this at startup, but a later integration failure can still break onboarding. `homeassistant-container.json` gives the exact image digest, which matters for the next step.

## 4. Upstream: core and frontend

Only once the app and the flow are ruled out. The `dev` image moves every night, so the useful comparison is against the last run that passed:

```bash
gh run list --workflow=e2e.yml --status success --limit 1 --json databaseId,createdAt
gh run download <green-run-id> --name e2e-artifacts --dir e2e-green
```

Diff the two `homeassistant-config.json` files to get the Home Assistant versions on either side of the break, then look at what landed between those dates:

- [`home-assistant/core`](https://github.com/home-assistant/core).
- [`home-assistant/frontend`](https://github.com/home-assistant/frontend).

## 5. Report

Everything lands in the `e2e-failure` issue in this repository: a comment when one is already open, a new issue otherwise. Never open an issue or a pull request on `home-assistant/core` or `home-assistant/frontend`. An upstream finding is reported here, in the same comment, for a maintainer to carry over.

Write:

1. **Verdict** — Say how confident you are and what would confirm it.
2. **Evidence** — the failing Maestro command, the logcat and Home Assistant log lines the verdict rests on, and the Home Assistant versions of the failed and the last green run. Quote the lines, don't paraphrase them.
3. **Culprit commit** — when you identified one, give it as `owner/repo@sha` with its title and date, and say what in it explains the failure. When you only narrowed it to a range, give the range and say it is a range.
4. **Proposed fix** — a concrete patch, in whichever repository it belongs to. Name the file and the change, whether it is here, in core, or in the frontend, so a maintainer can apply or forward it. When the right answer is to adapt the Maestro flow to an intentional upstream change, say that instead.
5. **A link to the failed run.**

Say plainly when the evidence supports no verdict, and list what you checked. A short honest report beats a confident guess.
