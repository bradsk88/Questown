#!/bin/sh
# Run N autotest servers in parallel, each in its own game dir and port.
#
#   tools/autotest-parallel.sh <N> <scenario-filter> [extra -D args...]
#   tools/autotest-parallel.sh 3 village/day1
#   tools/autotest-parallel.sh 4 'farmer/harvest_wheat [held_tool_warp]' -Dquestown.autotest.tickrate=400
#
# Captures the exact server JVM command `./gradlew runServer` launches (the module-path /
# classpath split and MOD_CLASSES must be replicated verbatim - references/questown-dev-launch.md
# "Parallel confirmation sweep"), then starts N copies from build/autotest-par/run-<i>.
# Logs: build/autotest-par/run-<i>.log. Parallel load changes timing: a flake under load is
# data, not a launcher failure.
set -eu

N=${1:?usage: autotest-parallel.sh <N> <scenario-filter> [extra -D args]}
FILTER=${2:?usage: autotest-parallel.sh <N> <scenario-filter> [extra -D args]}
shift 2
REPO=$(cd "$(dirname "$0")/.." && pwd)
OUT=$REPO/build/autotest-par
mkdir -p "$OUT"

if pgrep -f '[B]ootstrapLauncher' >/dev/null; then
    echo "A Minecraft server/client is already running; stop it first." >&2
    exit 1
fi

# 1. Capture: launch once through gradle, read the JVM's argv + MOD_CLASSES, stop it.
echo "Capturing the server command (gradle build + launch)..."
(cd "$REPO" && LANG=C.UTF-8 ./gradlew runServer -Dquestown.autotest=true \
    -Dquestown.autotest.only=__capture__ > "$OUT/capture.log" 2>&1) &
CAP_PID=""
i=0
while [ $i -lt 300 ]; do
    for p in $(pgrep -f '[B]ootstrapLauncher'); do
        if [ "$(readlink "/proc/$p/cwd")" = "$REPO/run" ]; then CAP_PID=$p; fi
    done
    [ -n "$CAP_PID" ] && break
    i=$((i + 1)); sleep 1
done
if [ -z "$CAP_PID" ]; then
    echo "Capture failed; see $OUT/capture.log" >&2
    exit 1
fi
rm -rf "$OUT/cmd"; mkdir "$OUT/cmd"
python3 - "$CAP_PID" "$OUT/cmd" <<'EOF'
import sys
pid, out = sys.argv[1], sys.argv[2]
args = open(f"/proc/{pid}/cmdline", "rb").read().split(b"\0")[:-1]
env = open(f"/proc/{pid}/environ", "rb").read().split(b"\0")
mod = [e for e in env if e.startswith(b"MOD_CLASSES=")]
open(f"{out}/args", "wb").write(b"\0".join(args))
open(f"{out}/mod_classes", "wb").write(mod[0].split(b"=", 1)[1] if mod else b"")
EOF
kill "$CAP_PID"
wait 2>/dev/null || true

# 2. Launch N copies.
PIDS=""
i=1
while [ "$i" -le "$N" ]; do
    D=$OUT/run-$i
    rm -rf "$D"; mkdir -p "$D"
    cp -r "$REPO/run/config" "$REPO/run/defaultconfigs" "$REPO/run/eula.txt" "$D/" 2>/dev/null || true
    sed -e "s/^server-port=.*/server-port=$((25700 + i))/" \
        -e "s/^enable-rcon=.*/enable-rcon=false/" \
        -e "s/^online-mode=.*/online-mode=false/" \
        "$REPO/run/server.properties" > "$D/server.properties"
    python3 - "$OUT/cmd" "$D" "$FILTER" "$@" > "$D/argv" <<'EOF'
import sys
cmd, game_dir, flt, extra = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
args = [a.decode() for a in open(f"{cmd}/args", "rb").read().split(b"\0")]
java, rest = args[0], args[1:]
rest = [a for a in rest if not a.startswith("-Dquestown.autotest.only=")]
extra_keys = {e.split("=", 1)[0] for e in extra}
rest = [a for a in rest if a.split("=", 1)[0] not in extra_keys]
gi = rest.index("--gameDir")
rest[gi + 1] = game_dir
props = ["-Xmx3G", "-Xms1G", f"-Dquestown.autotest.only={flt}"] + extra
sys.stdout.write("\0".join([java] + props + rest))
EOF
    (cd "$D" && MOD_CLASSES=$(cat "$OUT/cmd/mod_classes") LANG=C.UTF-8 \
        xargs -0 -a "$D/argv" sh -c 'exec "$@"' sh > "$OUT/run-$i.log" 2>&1) &
    PIDS="$PIDS $!"
    echo "run $i: started (port $((25700 + i)), log $OUT/run-$i.log)"
    i=$((i + 1))
done

# 3. Wait for each run's tally, then stop it (by pid: never a pattern kill).
i=1
for P in $PIDS; do
    L=$OUT/run-$i.log
    while kill -0 "$P" 2>/dev/null && ! grep -aq '\[autotest\] RESULT:' "$L"; do sleep 5; done
    sleep 2
    for J in $(pgrep -f '[B]ootstrapLauncher'); do
        if [ "$(readlink "/proc/$J/cwd")" = "$OUT/run-$i" ]; then kill "$J"; fi
    done
    echo "run $i: $(grep -a '\[autotest\] RESULT:' "$L" | tail -1 | sed 's/.*RESULT/RESULT/' || echo 'no RESULT line')"
    grep -aE '\[FAIL\] scenario|\[village\] FAIL ' "$L" | sed -E 's/.*\[(autotest|village)\] /    /' || true
    i=$((i + 1))
done
wait 2>/dev/null || true
