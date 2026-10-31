#!/usr/bin/env python3
"""Ask the vision backend one question about one screenshot.
Usage: peek.py <png> <prompt>"""
import json, base64, urllib.request, signal, sys, os, time

def timeout_handler(signum, frame):
    raise TimeoutError()
signal.signal(signal.SIGALRM, timeout_handler)
url = os.environ.get('VISION_URL', 'http://154.37.220.220:30389/v1/chat/completions')
if os.environ.get('VISION_KEY_FILE'):
    key = open(os.environ['VISION_KEY_FILE']).read().strip()
else:
    key = open('/home/retro/.config/vastai/qwen_vllm_key').read().strip()

b = base64.b64encode(open(sys.argv[1], 'rb').read()).decode()
prompt = sys.argv[2]
body = json.dumps({
    "model": "qwen38-nvfp4",
    "max_tokens": 400,
    "chat_template_kwargs": {"reasoning_effort": "low"},
    "messages": [{"role": "user", "content": [
        {"type": "text", "text": prompt},
        {"type": "image_url", "image_url": {"url": "data:image/png;base64," + b}},
    ]}],
}).encode()
req = urllib.request.Request(
    url, data=body,
    headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"})
done = False
for attempt in range(4):
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
    time.sleep(3)
if not done:
    print("FAILED")
