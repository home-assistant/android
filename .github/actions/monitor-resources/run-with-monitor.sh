#!/usr/bin/env bash
# Runs the given command with monitor.sh streaming samples in the background and kills the
# monitor when the command exits. Single entry point for places where the monitor-resources
# action cannot wrap the step, such as the emulator-runner `script` input, which executes
# every script line in a separate shell and would orphan a monitor started on its own line.
set -euo pipefail
MONITOR_LABEL="${MONITOR_LABEL:-$GITHUB_JOB/$*}" "$(dirname "$0")/monitor.sh" &
MONITOR_PID=$!
trap 'kill "$MONITOR_PID" 2>/dev/null || true' EXIT TERM INT
"$@"
