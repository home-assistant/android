#!/usr/bin/env bash
# Runs the given command while streaming one resource usage line every MONITOR_INTERVAL_SECONDS
# seconds (default 10), and stops sampling when the command exits. Single entry point for the
# monitor-resources action and for places where that action cannot wrap the step, such as the
# emulator-runner `script` input, which executes every script line in a separate shell and would
# orphan a monitor started on its own line.
set -euo pipefail

# Peaks live in a file rather than in shell variables: the sampler runs in a background subshell
# that is killed on exit, so nothing it holds in memory survives to the summary.
PEAK_FILE=$(mktemp)

# Sampling is best effort: a probe missing from the runner image must not stop the monitor,
# hence `set +e` for the loop that the strict mode above would otherwise abort.
sample_resources() {
  set +e
  local peak_used=0 peak_used_at="?" min_avail=-1 peak_swap=0 peak_rss=0
  while true; do
    TS=$(date -u '+%H:%M:%S')
    # total= would shrink below 16GB if the hypervisor reclaims guest memory (ballooning).
    read -r MEM_TOTAL MEM_USED MEM_AVAIL SWAP_USED < <(free -m | awk '/^Mem:/ {t = $2; u = $3; a = $7} /^Swap:/ {print t, u, a, $3}')
    MEM="total=${MEM_TOTAL:-0}MB used=${MEM_USED:-0}MB available=${MEM_AVAIL:-0}MB"
    SWAP="swap-used=${SWAP_USED:-0}MB"
    # steal= is the share of time the hypervisor did not schedule our vCPUs (host contention).
    STEAL=$(vmstat 1 2 | awk 'NR==2 {for (i = 1; i <= NF; i++) if ($i == "st") c = i} END {printf "steal=%s%%", $c}')
    # mem-stall= is the share of time tasks stalled waiting on memory (climbs before an OOM kill).
    STALL=$(awk -F 'avg10=' '/^some/ {split($2, a, " "); printf "mem-stall=%s%%", a[1]}' /proc/pressure/memory)
    LOAD=$(awk '{printf "load=%s", $1}' /proc/loadavg)
    DISK=$(df -m --output=avail / | awk 'NR==2 {printf "disk-avail=%sMB", $1}')
    # A gap between used= and proc-rss-sum= growing by gigabytes means memory is held
    # by the kernel rather than by processes: an inflating balloon looks like that from
    # inside the guest when total= does not shrink, but so does reclaim lag right after
    # a large process exits, so read the gap together with the surrounding samples.
    RSS_SUM=$(ps -eo rss= | awk '{s += $1} END {printf "%d", s / 1024}')
    PRS="proc-rss-sum=${RSS_SUM:-0}MB"
    # Classify JVMs by their command line: every daemon would otherwise just print as "java".
    TOP=$(ps -eo rss=,args= --sort=-rss | head -3 | awk '{
      name = $2; sub(/.*\//, "", name)
      if ($0 ~ /GradleDaemon/) name = "gradle-daemon"
      else if ($0 ~ /KotlinCompileDaemon/) name = "kotlin-daemon"
      else if ($0 ~ /GradleWorkerMain/) name = "gradle-worker"
      else if ($0 ~ /screenshot/) name = "screenshot-engine"
      printf "%s=%dMB ", name, $1 / 1024}')
    echo "memory-monitor [$MONITOR_LABEL] $TS $MEM $SWAP $PRS $STEAL $STALL $LOAD $DISK top: $TOP"

    (( ${MEM_USED:-0} > peak_used )) && { peak_used=${MEM_USED:-0}; peak_used_at=$TS; }
    (( min_avail < 0 || ${MEM_AVAIL:-0} < min_avail )) && min_avail=${MEM_AVAIL:-0}
    (( ${SWAP_USED:-0} > peak_swap )) && peak_swap=${SWAP_USED:-0}
    (( ${RSS_SUM:-0} > peak_rss )) && peak_rss=${RSS_SUM:-0}
    # Rewritten in full every sample so the summary reflects the last complete sample even if
    # the runner kills the monitor mid-write.
    printf 'total=%sMB peak-used=%sMB (at %s) min-available=%sMB peak-swap-used=%sMB peak-proc-rss-sum=%sMB\n' \
      "${MEM_TOTAL:-0}" "$peak_used" "$peak_used_at" "$min_avail" "$peak_swap" "$peak_rss" > "$PEAK_FILE.tmp" &&
      mv "$PEAK_FILE.tmp" "$PEAK_FILE"

    sleep "${MONITOR_INTERVAL_SECONDS:-10}"
  done
}

# The peaks only reach the log if the shell itself survives the command: when the VM dies the
# streamed sample lines above remain the sole record.
report_peaks() {
  kill "$MONITOR_PID" 2>/dev/null || true
  if [[ -s "$PEAK_FILE" ]]; then
    echo "memory-monitor [$MONITOR_LABEL] peak $(cat "$PEAK_FILE")"
  fi
  rm -f "$PEAK_FILE" "$PEAK_FILE.tmp"
}

MONITOR_LABEL="${MONITOR_LABEL:-$GITHUB_JOB/$*}"
sample_resources &
MONITOR_PID=$!
trap report_peaks EXIT TERM INT
"$@"
