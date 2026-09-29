#!/bin/bash
# Kill any JVM tied to the Questown repos or the local gradle daemons.
# Matched by cwd + main class so the invoking shell (cmdline
# "bash tools/porttest/sweep.sh") can never match itself — an inline
# `case $cmdline in *java*` kill loop matches its own scanning shell
# and dies with a bare "(no output)".
#
# Usage: sweep.sh [project-root ...]   (default: this repo + its 119 worktree)
set -u
roots=("$@")
[ ${#roots[@]} -gt 0 ] || roots=(/workspace/IdeaProjects/Questown /workspace/IdeaProjects/Questown-119)
self=$$
for p in $(ls /proc | grep -E '^[0-9]+$'); do
  [ "$p" = "$self" ] && continue
  d=$(readlink /proc/$p/cwd 2>/dev/null) || continue
  match=0
  for r in "${roots[@]}"; do
    case "$d" in "$r"*) match=1;; esac
  done
  [ "$match" = "1" ] || case "$d" in /home/retro/.gradle*) match=1;; esac
  [ "$match" = "1" ] || continue
  c=$(tr '\0' ' ' < /proc/$p/cmdline 2>/dev/null) || continue
  case "$c" in
    *GradleWrapperMain*|*GradleDaemon*|*gradle-launcher*|*neoforged*|*net.minecraftforge*|*GradleServerMain*)
      kill -9 "$p" 2>/dev/null && echo "killed $p ($d)"
  ;;
  esac
done
echo "sweep done"
