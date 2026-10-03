#!/usr/bin/env python3
"""job-producers — answer "which jobs produce (or need) item X" from the job data.

Usage:
  scripts/job-producers.py <item-substring>      e.g. coal, sword, wheat
  scripts/job-producers.py --tag <tag>           e.g. #questown:swords
  scripts/job-producers.py --room <room-substr>  jobs anchored to a room
  scripts/job-producers.py --list                every job, compact

The job schema (verified against baker_bread / blacksmith_wood_axe /
hunter_unmapped_sword_full / miner_short, 2026-10-03):
  OUTPUT side  = initial_request (the job's product ask) + result
      result.type item          -> result.item
      result.type biome_loot    -> every entry of loot_tables/jobs/<prefix>/*.json
                                   (the union, weight-ranked, is the output space)
      result.type crafting_table-> vanilla recipe (result.fallback item)
  INPUT side   = work_states[].ingredients (consumed), work_states[].tools (held)
  BASE ROOM    = room + block
  TREE         = parent chain, priority (higher wins), unlocks_title

Engine ground truth: TownQuests.producerRootsForItem inverts
ServerJobsRegistry.getResults (per work: work.results) — i.e. exactly the
OUTPUT side above. Item quests (e.g. the sword quests) are the
player-supplied channel, NOT job output; SupplySwordCommand is the
held-run stand-in.
"""
import json, sys, glob, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JOBS = os.path.join(ROOT, 'src/main/resources/data/questown/questown_jobs')
LOOT = os.path.join(ROOT, 'src/main/resources/data/questown/loot_tables/jobs')
TAG_DIRS = ['src/main/resources/data/questown/tags/items',
            'src/main/resources/data/minecraft/tags/items']

def load(p):
    try:
        return json.load(open(p))
    except Exception as e:
        return None

def resolve_tag(tag):
    """#ns:name -> list of item ids."""
    if not tag.startswith('#'):
        return [tag]
    ns, name = tag[1:].split(':', 1)
    p = f'{ROOT}/src/main/resources/data/{ns}/tags/items/{name}.json'
    d = load(p)
    if d is None:
        # fall back: search every installed namespace
        for cand in glob.glob(f'{ROOT}/src/main/resources/data/*/tags/items/{name}.json'):
            d = load(cand)
            if d:
                break
    return [v for v in (d or {}).get('values', [])]

def job_outputs(j):
    """Expand the OUTPUT side of one job dict to a flat set of item tags/ids."""
    out = set()
    req = j.get('initial_request')
    if req:
        out.update(resolve_tag(req))
    res = j.get('result') or {}
    t = res.get('type')
    if t == 'item':
        out.add(res.get('item'))
    elif t == 'biome_loot':
        prefix = res.get('prefix')
        for f in glob.glob(f'{LOOT}/{prefix}/**/*.json', recursive=True) + glob.glob(f'{LOOT}/{prefix}/*.json'):
            d = load(f)
            if d:
                for pool in d.get('pools', []):
                    for e in pool.get('entries', []):
                        if e.get('name'):
                            out.add(e['name'])
    elif t == 'crafting_table':
        if res.get('fallback'):
            out.add(res['fallback'])
    out.discard(None)
    return out

def job_inputs(j):
    ing, tools = [], []
    for s in j.get('work_states', []):
        if s.get('ingredients'):
            ing.append(f"{s['ingredients']} x{s.get('quantity','?')}")
        if s.get('tools'):
            tools.append(s['tools'])
    return ing, tools

def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__); sys.exit(1)
    if args[0] == '--list':
        q, tag, room = '', None, ''
    elif args[0] == '--tag':
        q, tag, room = '', args[1], ''
    elif args[0] == '--room':
        q, tag, room = '', None, args[1]
    else:
        q, tag, room = args[0], None, ''

    jobs = {}
    for f in sorted(glob.glob(f'{JOBS}/*.json')):
        d = load(f)
        if d and d.get('id'):
            jobs[d['id']] = (d, os.path.basename(f))

    if room:
        matched = {jid: d for jid, (d, fn) in jobs.items() if room in (d.get('room') or '')}
        print(f"== jobs in a room containing '{room}' ({len(matched)}) ==")
        for jid, d in sorted(matched.items()):
            print(f"  {jid:35s} room={d.get('room'):45s} result={d.get('result',{}).get('type')}")
        return

    if tag:
        wanted = set(resolve_tag(tag))
        q = None
    else:
        wanted = None

    for jid, (d, fn) in sorted(jobs.items()):
        outputs = job_outputs(d)
        ing, tools = job_inputs(d)
        hit_produces = (wanted is not None and wanted & outputs) or (q and any(q in o for o in outputs))
        hit_consumes = (q and any(q in i for i in ing + tools)) or (
            wanted is not None and (set(ing) | set(tools)) & wanted)
        if hit_produces or hit_consumes:
            side = 'PRODUCES' if hit_produces else 'consumes/needs'
            print(f"{side:15s} {jid:35s} {fn}")
            print(f"{'':15s} request={d.get('initial_request')} result={d.get('result')}")
            if ing:
                print(f"{'':15s} ingredients={ing}")
            if tools:
                print(f"{'':15s} tools={tools} (TOOLS = held, usually durable — NOT the product)")
            print(f"{'':15s} room={d.get('room')} block={d.get('block')} parent={d.get('parent')}")
            print()

    # summary check for a plain query
    if q:
        produces = [jid for jid, (d, _f) in jobs.items() if any(q in o for o in job_outputs(d))]
        print(f"== no job PRODUCES '{q}' ==" if not produces
              else f"== {len(produces)} job(s) produce '{q}' ==")

main() if __name__ == '__main__' else None
