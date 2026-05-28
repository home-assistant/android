#!/usr/bin/env sh

if [ "$username" = "ha" ] && [ "$password" = "ha" ]; then
  cat <<'EOF'
name = Maestro E2E
group = system-admin
local_only = false
EOF
  exit 0
fi

exit 1
