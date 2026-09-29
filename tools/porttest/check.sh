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
python3 - "$DIR" "$MAN" "$ONE" <<'PYEOF'
import json, base64, urllib.request, sys, os

d, man, one = sys.argv[1], sys.argv[2], sys.argv[3]
m = json.load(open(man))
key = json.load(open('/home/retro/.pi/agent/auth.json'))['openrouter']['key']
model = m.get('check', {}).get('model', 'moonshotai/kimi-k3')

fail = 0
for s in m['screens']:
    name = s['name']
    if one and name != one:
        continue
    p = os.path.join(d, name + '.png')
    if not os.path.exists(p):
        print('FAIL %s: missing %s' % (name, p)); fail = 1; continue
    expect = s.get('expect', {})
    want = expect.get('activeTab', '')
    prompt = (
        "Look at this Minecraft screenshot. Answer ONLY these four lines, "
        "in exactly this format:\n"
        "MENU: <yes|no> (is a tabbed villager menu on screen?)\n"
        "TAB: <the label of the highlighted tab in the menu's tab bar; 'none'>\n"
        "DEATH: <yes|no> (is this the 'You Died' screen?)\n"
        "TITLE: <yes|no> (is this the title/connect screen?)")
    b64 = base64.b64encode(open(p, 'rb').read()).decode()
    body = json.dumps({"model": model, "messages": [{"role": "user",
        "content": [{"type": "image_url",
                     "image_url": {"url": "data:image/png;base64," + b64}},
                    {"type": "text", "text": prompt}]}]})
    req = urllib.request.Request(
        "https://openrouter.ai/api/v1/chat/completions", data=body.encode(),
        headers={"Content-Type": "application/json",
                 "Authorization": "Bearer " + key})
    try:
        ans = json.load(urllib.request.urlopen(req, timeout=55))["choices"][0]["message"]["content"]
    except Exception as e:
        print('FAIL %s: vision call error: %s' % (name, e)); fail = 1; continue

    got = {}
    for line in ans.splitlines():
        i = line.find(':')
        if i > 0:
            got[line[:i].strip().upper()] = line[i+1:].strip().lower()
    bad = []
    if got.get('MENU') not in ('yes', 'no'):
        bad.append('unparsed MENU (raw: %r)' % ans[:120])
    else:
        if got['MENU'] != 'yes': bad.append('no menu on screen')
        if got.get('DEATH') == 'yes': bad.append('death screen')
        if got.get('TITLE') == 'yes': bad.append('title screen')
        tab = got.get('TAB', '')
        if want and tab not in ('none', '') and want not in tab and tab != want:
            bad.append('active tab %r, want %r' % (tab, want))
        elif want and tab in ('none', ''):
            bad.append('no active tab detected')
    if bad:
        print('FAIL %s: %s' % (name, '; '.join(bad))); fail = 1
    else:
        print('PASS %s (tab=%s)' % (name, got.get('TAB', want)))
sys.exit(1 if fail else 0)
PYEOF
