#!/usr/bin/env python3
"""Describe the visible differences between two screenshots of the same UI.
Usage: compare2.py <golden.png> <wip.png>"""
import json, base64, urllib.request, signal, sys, os

def timeout_handler(signum, frame):
    raise TimeoutError()
signal.signal(signal.SIGALRM, timeout_handler)
url = os.environ.get('VISION_URL', 'http://154.37.220.220:30389/v1/chat/completions')
if os.environ.get('VISION_KEY_FILE'):
    key = open(os.environ['VISION_KEY_FILE']).read().strip()
else:
    key = open('/home/retro/.config/vastai/qwen_vllm_key').read().strip()

def img(p):
    return base64.b64encode(open(p, 'rb').read()).decode()

g, w = img(sys.argv[1]), img(sys.argv[2])
body = json.dumps({
    "model": "qwen38-nvfp4",
    "max_tokens": 1200,
    "chat_template_kwargs": {"reasoning_effort": "low"},
    "messages": [{"role": "user", "content": [
        {"type": "text", "text": "Two screenshots of the same Minecraft mod UI screen: image 1 is the KNOWN-GOOD older version, image 2 is the ported newer version. Ignore minor font antialiasing and slight vanilla-texture differences. List the VISIBLE LAYOUT OR CONTENT differences (what is missing, added, moved, different colors, different lists/text, different panel structure). Be specific and short."},
        {"type": "image_url", "image_url": {"url": "data:image/png;base64," + g}},
        {"type": "image_url", "image_url": {"url": "data:image/png;base64," + w}},
    ]}],
}).encode()
req = urllib.request.Request(
    url,
    data=body,
    headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"})
done = False
for attempt in range(5):
    signal.alarm(50)
    try:
        r = urllib.request.urlopen(req, timeout=45)
        m = json.loads(r.read())['choices'][0]['message']
        c = m.get('content')
        if c:
            print(c)
            done = True
            break
        print(f"(attempt {attempt+1}: empty content, retrying)")
    except Exception as e:
        print(f"(attempt {attempt+1} failed: {e})")
    finally:
        signal.alarm(0)
    import time; time.sleep(4)
if not done:
    print("FAILED")
