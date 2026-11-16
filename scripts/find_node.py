"""Print "x y" for the centre of a UI node from a uiautomator dump.

usage: find_node.py ui.xml id|text|desc VALUE [INDEX]
"""
import re
import sys
import xml.etree.ElementTree as ET

path, kind, value = sys.argv[1], sys.argv[2], sys.argv[3]
index = int(sys.argv[4]) if len(sys.argv) > 4 else 0
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
hits = []
for node in root.iter("node"):
    if kind == "id":
        ok = node.get("resource-id", "").endswith(":id/" + value)
    elif kind == "text":
        ok = node.get("text", "") == value
    else:
        ok = node.get("content-desc", "") == value
    if ok:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            if x2 > x1 and y2 > y1:
                hits.append(((x1 + x2) // 2, (y1 + y2) // 2))
if len(hits) > index:
    print(hits[index][0], hits[index][1])
