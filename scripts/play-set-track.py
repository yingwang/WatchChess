#!/usr/bin/env python3
"""把一个已经上传过的 versionCode 放到另一条轨道上，不重新上传包。

Play 的 Wear OS 表单要求「先把包发到一条轨道」之后才肯让人勾选它的审核政策，
而同一个 versionCode 不能再上传第二遍，所以需要这么一个只改轨道不传文件的动作。
play_publish.py 缺这一路：它没有 --aab 时会去读轨道上现有的发布来改文案，
轨道是空的就直接报错。
"""
import argparse, os, sys, urllib.parse
sys.path.insert(0, os.path.expanduser("~/claude/telewise/tools"))
import play_publish as pp
import json

ap = argparse.ArgumentParser()
ap.add_argument("--package", required=True)
ap.add_argument("--track", required=True)
ap.add_argument("--version-code", required=True)
ap.add_argument("--status", default="draft", choices=["draft", "completed"])
ap.add_argument("--name")
ap.add_argument("--notes", help="fastlane metadata 目录")
a = ap.parse_args()

key = json.load(open(pp.KEY_PATH))
token = pp.access_token(key)
edit = pp.call(token, "POST", "%s/%s/edits" % (pp.API, a.package), payload={})
eid = edit["id"]
print("edit", eid)

release = {"versionCodes": [a.version_code], "status": a.status,
           "name": a.name or a.version_code}
notes = pp.release_notes(a.notes, a.version_code) if a.notes else []
if notes:
    release["releaseNotes"] = notes
    print("notes in", ", ".join(n["language"] for n in notes))

pp.call(token, "PUT", "%s/%s/edits/%s/tracks/%s" % (pp.API, a.package, eid, urllib.parse.quote(a.track)),
        payload={"track": a.track, "releases": [release]})
print("track", a.track, "->", a.version_code, a.status)
pp.call(token, "POST", "%s/%s/edits/%s:commit" % (pp.API, a.package, eid))
print("committed")
