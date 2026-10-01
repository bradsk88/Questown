#!/usr/bin/env bash
# Content gate: vision-verify captured screens against manifest expectations.
# Run BEFORE banking a capture set - distinct file sizes prove screens differ
# from each other; only this gate proves they show the right thing.
#
#   check.sh <screens-dir> [manifest.json] [screen]
#     <screens-dir>   dir of <name>.png captures (goldens/1.19.2, live/...)
#     [manifest]      default: manifest.json next to this script
#     [screen]        check one screen instead of all
#
# Exits 0 only if every checked screen matches its expect block.
set -uo pipefail
DIR=${1:?screens dir}
MAN=$(cd "$(dirname "$0")" && pwd)/manifest.json
ONE=${3:-}
# ergonomic swap: check.sh <dir> <screen>
if [ -n "${2:-}" ] && [ ! -f "$2" ]; then ONE=$2; fi
PYTHONUNBUFFERED=1 python3 - "$DIR" "$MAN" "$ONE" <<'PYEOF'
import json, base64, urllib.request, sys, os

d, man, one = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(man))
# Vision backend: default = the live Nova GPU proxy (qwen3.8:27b has
# vision). The vast-era box 154.37.220.220 is dead (port answers TCP,
# HTTP returns empty), so it is no longer a default. Override with
# VISION_URL / VISION_MODEL (e.g. back to OpenRouter).
# Precedence: the manifest's `check` block overrides these defaults for
# the MODEL - a dead/renamed model pinned there 404s the whole gate even
# when the default URL is fine. Switch both together.
# Reasoning level "medium": low/under levels spend the token budget on
# reasoning and return null content; medium answers in seconds.
import os as _os
url = _os.environ.get('VISION_URL')
if not url:
    # The local proxy's port rotates per session, and even models.json can
    # be stale, so probe candidates and use the first that is live. Order:
    # providers carrying the target model first, then remaining localhost
    # providers, then the legacy port.
    target = m.get('check', {}).get('model', 'qwen3.8:27b')
    cands, seen = [], set()
    def _add(c):
        c = (c or '').rstrip('/')
        if c.startswith('http://localhost') and c not in seen:
            seen.add(c); cands.append(c)
    provs = []
    try:
        with open(_os.path.expanduser('~/.pi/agent/models.json')) as _f:
            provs = [p for p in json.load(_f).get('providers', {}).values()
                     if str(p.get('baseUrl', '')).startswith('http://localhost')]
    except Exception:
        pass
    provs.sort(key=lambda p: not any(x.get('id') == target for x in p.get('models', [])))
    for p in provs:
        _add(p.get('baseUrl'))
    _add('http://localhost:42827/v1')
    import urllib.request as _ur
    for c in cands:
        try:
            if _ur.urlopen(c + '/models', timeout=4).status in (200, 401, 403):
                url = c; break
        except Exception:
            continue
    if not url:
        raise SystemExit('FAIL: no live Nova vision endpoint; set VISION_URL')
    url += '/chat/completions'
key = ''
if _os.environ.get('VISION_KEY_FILE'):
    key = open(_os.environ['VISION_KEY_FILE']).read().strip()
model = m.get('check', {}).get('model', _os.environ.get('VISION_MODEL', 'qwen38-nvfp4'))

fail = 0
for s in m['screens']:
    name = s['name']
    if one and name != one:
        continue
    p = os.path.join(d, name + '.png')
    if not os.path.exists(p):
        print('FAIL %s: missing %s' % (name, p)); fail = 1; continue
    expect = s.get('expect', {})
    want = expect.get('panelLabel') or expect.get('activeTab', '')
    # the six tabbed screens have a tab-icon strip; standalone panels (the
    # work board) do not - a wrong MENU description misfires the gate there
    menu_desc = expect.get('menu') or (
        "a centre panel over a dimmed world, with a row of small tab icons "
        "across the top edge")
    prompt = (
        "Look at this Minecraft screenshot. Answer ONLY these lines, each "
        "just 'yes' or 'no' after the colon:\n"
        "MENU: (is %s on screen?)\n"
        "TAB: (is that panel showing the '%s' view, or content clearly about %s, rather than another panel?)\n"
        "DEATH: (is this the 'You Died' death screen?)\n"
        "TITLE: (is this the title or connect screen?)" % (menu_desc, want, want))
    b64 = base64.b64encode(open(p, 'rb').read()).decode()
    body = json.dumps({"model": model, "max_tokens": 3000,
        "chat_template_kwargs": {"reasoning_effort": "medium"},
        "messages": [{"role": "user",
        "content": [{"type": "image_url",
                     "image_url": {"url": "data:image/png;base64," + b64}},
                    {"type": "text", "text": prompt}]}]})
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Authorization"] = "Bearer " + key
    req = urllib.request.Request(url, data=body.encode(), headers=headers)
    ans = None
    import signal
    class HardTimeout(Exception): pass
    def _on_alarm(signum, frame): raise HardTimeout()
    for attempt in (1, 2):
        signal.signal(signal.SIGALRM, _on_alarm)
        signal.alarm(45)
        try:
            msg = json.load(urllib.request.urlopen(req, timeout=40))["choices"][0]["message"]
            ans = msg.get('content')
            if not ans:
                raise RuntimeError('empty content')
            break
        except Exception as e:
            if attempt == 2:
                print('FAIL %s: vision call error: %s' % (name, e)); fail = 1; break
            import time; time.sleep(2)
        finally:
            signal.alarm(0)
    if ans is None:
        continue

    got = {}
    for line in ans.splitlines():
        i = line.find(':')
        if i > 0:
            got[line[:i].strip().upper()] = line[i+1:].strip().lower()
    def yn(k):
        v = got.get(k)
        return v if v in ('yes', 'no') else None
    bad = []
    menu, tab, death, title = yn('MENU'), yn('TAB'), yn('DEATH'), yn('TITLE')
    if menu is None:
        bad.append('unparsed MENU (raw: %r)' % ans[:120])
    else:
        if menu != 'yes': bad.append('no menu on screen')
        if tab is not None and tab != 'yes': bad.append('the %s tab is NOT the active tab' % want)
        if tab is None: bad.append('unparsed TAB (raw: %r)' % ans[:120])
        if death == 'yes': bad.append('death screen')
        if title == 'yes': bad.append('title screen')
    if bad:
        print('FAIL %s: %s' % (name, '; '.join(bad))); fail = 1
    else:
        print('PASS %s (menu+active tab confirmed)' % name)
sys.exit(1 if fail else 0)
PYEOF
