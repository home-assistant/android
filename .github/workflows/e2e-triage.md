---
# Dispatched by the E2E workflow when it fails, so a green night never spends an
# agent run. Triggering it by hand against an older run ID is supported too.
on:
  workflow_dispatch:
    inputs:
      run-id:
        description: "Run ID of the failed E2E run to triage"
        required: true
        type: string

# The agent itself never writes. Opening and commenting on issues happens in the
# safe-outputs jobs, which get their own scoped permissions.
permissions:
  contents: read
  actions: read
  issues: read
  copilot-requests: write

# One slot per triaged run: triages of different runs go in parallel, while a second
# dispatch for the same run waits instead of racing it on the e2e-failure issue.
concurrency:
  job-discriminator: ${{ github.event.inputs.run-id }}

engine: copilot

network: defaults

timeout-minutes: 20

tools:
  github:
    toolsets: [actions, issues, repos]
  bash: ["ls", "cat", "head", "tail", "grep", "wc", "find", "jq", "file"]

# The artifacts are fetched deterministically rather than through the API: the
# MCP artifact tool only returns a download URL, and the agent reads plain files
# far more reliably than it unpacks archives.
pre-agent-steps:
  - name: Download artifacts of the failed run
    uses: actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1
    with:
      name: e2e-artifacts
      path: e2e-artifacts
      run-id: ${{ github.event.inputs.run-id }}
      github-token: ${{ secrets.GITHUB_TOKEN }}

  # Best effort: gives the agent the Home Assistant version that last worked, so
  # it can scope an upstream regression to a range of days.
  - name: Download artifacts of the last successful run
    continue-on-error: true
    env:
      GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      GH_REPO: ${{ github.repository }}
    run: |
      RUN=$(gh run list --workflow=e2e.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId')
      if [ -n "$RUN" ]; then
        gh run download "$RUN" --name e2e-artifacts --dir e2e-artifacts-last-green
      fi

  - name: Write triage context
    env:
      RUN_ID: ${{ github.event.inputs.run-id }}
      RUN_URL: ${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.event.inputs.run-id }}
    run: |
      {
        echo "Failed run ID: $RUN_ID"
        echo "Failed run URL: $RUN_URL"
      } > triage-context.txt

safe-outputs:
  create-issue:
    title-prefix: "Nightly E2E failure: "
    labels: [e2e-failure]
    max: 1
  add-comment:
    target: "*"
    max: 1
---

# E2E failure triage

The nightly `E2E` workflow failed. Work out why, and report it. You are diagnosing only: do not change any file, and do not open a pull request.

## What you have

- `triage-context.txt`, holding the ID and URL of the failed run. Read it first and use that URL in everything you report.
- The artifacts of that run, already unpacked in `e2e-artifacts/`.
- The artifacts of the last run that passed, in `e2e-artifacts-last-green/`, when one was available.
- The GitHub Actions tools, for the console output of the job and the step that failed.

## How to investigate

Read `.agents/skills/ha-android-e2e-debugging/SKILL.md` and follow the procedure in it. Skip its step 1: the artifacts are already on disk. That skill is the source of truth for the triage order, for what each artifact contains, and for the known flake patterns. Do not invent your own order.

Start by identifying which step of the workflow failed. A failure in the build, the Home Assistant container, or the emulator session is not a Maestro failure at all, and the Maestro report will be empty or missing.

Stop investigating as soon as one source explains the failure, and write the report. Look at upstream core and frontend only when the Maestro report, the logcat, and the Home Assistant log all leave the failure unexplained. A run that reaches a verdict and then keeps searching for a culprit commit runs out of time without reporting anything, which is worse than a report without a culprit.

## Constraints

- The job is killed after 20 minutes, startup included, and a killed run reports nothing. Run `date` first, and post the report before 17 minutes have passed, saying what you did not get to.
- The shell accepts only `ls`, `cat`, `head`, `tail`, `grep`, `wc`, `find`, `jq`, `file`, `date`, `echo`, `printf`, `pwd`, `sort`, `uniq`, and `yq`. Anything else, including `cd`, `python3`, `curl`, `cp`, and `touch`, is denied, and a denied command stays denied when rephrased. Use `jq` and `grep` with absolute paths instead, and call `missing_tool` if something is truly impossible.
- The workspace and the artifacts are read-only. Write only under `/tmp/gh-aw/`.
- The `gh` CLI is not in the shell. GitHub is reached through two tools: `github`, read-only, for the `e2e-failure` issue search below, the failed job's console output, and the commit range for step 2.4 when you reach it; and `safeoutputs`, whose `create_issue` and `add_comment` calls publish your report. The last green run is already in `e2e-artifacts-last-green/`, so nothing needs downloading.

## What to report

Step 3 of the skill defines what a report contains and where it goes. Follow it exactly, and do not add sections of your own. In particular: everything goes into this repository's `e2e-failure` issue, including an upstream finding and the fix you propose for it. You have no write access to `home-assistant/core` or `home-assistant/frontend`, and must not try to open anything there.

Search the repository for an open issue labelled `e2e-failure`.

- **If one exists**, add a comment to it. Lead with whether this is the failure already described there or a new one, then give only what is new. Do not restate the issue back at itself.
- **If none exists**, create one. The title is prefixed for you, so supply only a short description of the failure itself, for example `login page never loaded on API 30`.
