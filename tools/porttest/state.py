#!/usr/bin/env python3
"""Verified state summary for the port-test branches (capture.sh stage `state`).

Prints, per project dir: which java processes belong to it (server /
client / gradle wrapper), the server's LISTEN port (from its /proc fds,
not from assumptions), the client's CWD (its gameDir), the FIFO-writer
process, a stale .qtdiag-cmd file, and fresh-shot mtime. Identity comes
from /proc/<pid>/cmdline + cwd, never from position in a process listing.
"""
import glob
import os
import re
import subprocess
import time


def list_projects():
    return sorted(glob.glob('/workspace/IdeaProjects/Questown*'))


def procs():
    out = {}
    for d in glob.glob('/proc/[0-9]*'):
        pid = d.rsplit('/', 1)[-1]
        try:
            cmd = open(d + '/cmdline', 'rb').read().split(b'\0')
        except OSError:
            continue
        cmd = ' '.join(p.decode('utf-8', 'replace') for p in cmd if p)
        if cmd:
            out[pid] = cmd
    return out


def cwd_of(pid):
    try:
        return os.readlink('/proc/%s/cwd' % pid)
    except OSError:
        return '?'


def listen_ports(pid):
    inodes = set()
    for fd in glob.glob('/proc/%s/fd/*' % pid):
        try:
            link = os.readlink(fd)
        except OSError:
            continue
        m = re.match(r'socket:\[(\d+)\]', link)
        if m:
            inodes.add(m.group(1))
    ports = set()
    for f in ('/proc/net/tcp', '/proc/net/tcp6'):
        try:
            lines = open(f).read().splitlines()[1:]
        except OSError:
            continue
        for l in lines:
            p = l.split()
            if p[3] == '0A' and p[9] in inodes:  # 0A = LISTEN
                ports.add(int(p[1].split(':')[1], 16))
    return sorted(ports)


def git_branch(proj):
    try:
        return subprocess.run(
            ['git', '-C', proj, 'branch', '--show-current'],
            capture_output=True, text=True, timeout=10
        ).stdout.strip() or '?'
    except Exception:
        return '?'


def main():
    procs_ = procs()
    for proj in list_projects():
        if not os.path.isdir(proj):
            continue
        # path-boundary match: 'Questown' must not swallow 'Questown-119'
        pat = re.compile(re.escape(proj) + r'(/|(?![\w-]))')
        print('[%s (%s)]' % (os.path.basename(proj), git_branch(proj)))
        for pid, cmd in sorted(procs_.items(), key=lambda kv: int(kv[0])):
            exe = os.path.basename(cmd.split()[0])
            if 'java' not in exe:
                continue
            if not pat.search(cmd):
                continue
            if 'GradleWrapperMain' in cmd:
                print('  gradle  pid %s  (%s)' % (
                    pid, ' '.join(cmd.split()[-2:])))
            elif 'serveruserdev' in cmd:
                print('  server  pid %s  port %s' % (
                    pid, ','.join(map(str, listen_ports(pid))) or '?'))
            elif 'clientuserdev' in cmd:
                print('  client  pid %s  gameDir(CWD) %s' % (pid, cwd_of(pid)))
        for pid, cmd in procs_.items():
            if 'sleep 999999' in cmd and 'server-cmd.fifo' in cmd and pat.search(cmd):
                print('  fifo-writer alive (pid %s)' % pid)
        for cand in ('run-client', 'run'):
            stale = os.path.join(proj, cand, '.qtdiag-cmd')
            if os.path.exists(stale):
                print('  STALE .qtdiag-cmd: %s (%d bytes)' % (
                    stale, os.path.getsize(stale)))
        sp = os.path.join(proj, 'run', 'server.properties')
        gm = '?'
        try:
            for l in open(sp):
                if l.startswith('gamemode='):
                    gm = l.strip().split('=', 1)[1]
        except OSError:
            pass
        print('  server gamemode: %s' % gm)
        for cand in ('run-client', 'run'):
            d = os.path.join(proj, cand, 'diags')
            pngs = glob.glob(d + '/*.png')
            if pngs:
                newest = max(os.path.getmtime(f) for f in pngs)
                print('  fresh shots: %d png in %s (newest %s)' % (
                    len(pngs), cand,
                    time.strftime('%m-%d %H:%M', time.localtime(newest))))
        print()


if __name__ == '__main__':
    main()
