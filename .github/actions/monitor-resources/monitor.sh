#!/usr/bin/env bash
# Streams one resource usage line every MONITOR_INTERVAL_SECONDS seconds (default 10).
# Launched in the background by the monitor-resources action; see action.yml.
while true; do
  # total= would shrink below 16GB if the hypervisor reclaims guest memory (ballooning).
  MEM=$(free -m | awk '/^Mem:/ {printf "total=%sMB used=%sMB available=%sMB", $2, $3, $7}')
  SWAP=$(free -m | awk '/^Swap:/ {printf "swap-used=%sMB", $3}')
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
  PRS=$(ps -eo rss= | awk '{s += $1} END {printf "proc-rss-sum=%dMB", s / 1024}')
  # Classify JVMs by their command line: every daemon would otherwise just print as "java".
  TOP=$(ps -eo rss=,args= --sort=-rss | head -3 | awk '{
    name = $2; sub(/.*\//, "", name)
    if ($0 ~ /GradleDaemon/) name = "gradle-daemon"
    else if ($0 ~ /KotlinCompileDaemon/) name = "kotlin-daemon"
    else if ($0 ~ /GradleWorkerMain/) name = "gradle-worker"
    else if ($0 ~ /screenshot/) name = "screenshot-engine"
    printf "%s=%dMB ", name, $1 / 1024}')
  echo "memory-monitor [$MONITOR_LABEL] $(date -u '+%H:%M:%S') $MEM $SWAP $PRS $STEAL $STALL $LOAD $DISK top: $TOP"
  sleep "${MONITOR_INTERVAL_SECONDS:-10}"
done
