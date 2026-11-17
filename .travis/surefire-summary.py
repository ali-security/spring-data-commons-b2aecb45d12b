"""Print per-test skip reasons and the overall totals from target/surefire-reports.

JUnit 4 assumptions such as assumeTrue(...) record a skip without a message, so
for those the matching Assume/@Ignore line from the test source is printed.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET


def source_reason(classname, method):
    path = os.path.join("src/test/java", classname.split("$")[0].replace(".", "/") + ".java")
    name = re.sub(r"\[.*$", "", method)
    try:
        with open(path, encoding="utf-8") as f:
            lines = f.readlines()
    except OSError:
        return None
    for i, line in enumerate(lines):
        if re.search(r"\bvoid\s+%s\s*\(" % re.escape(name), line):
            for prev in lines[max(0, i - 4):i]:
                if "@Ignore" in prev:
                    return prev.strip()
            for body in lines[i + 1:i + 40]:
                if re.search(r"assume", body, re.IGNORECASE):
                    return body.strip()
    return None


tests = failures = errors = skipped = 0
skips = []
for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        sk = case.find("skipped")
        if sk is None:
            continue
        message = (sk.get("message") or "").strip()
        if not message:
            src = source_reason(case.get("classname", ""), case.get("name", ""))
            message = "assumption not met: %s" % src if src else "(no message)"
        skips.append("%s.%s: %s" % (case.get("classname"), case.get("name"), message))

print("Skipped tests (%d):" % len(skips))
for line in skips:
    print("  SKIPPED " + line)
print("Surefire totals: tests=%d passed=%d failures=%d errors=%d skipped=%d"
      % (tests, tests - failures - errors - skipped, failures, errors, skipped))
sys.exit(0 if tests > 0 and failures == 0 and errors == 0 else 1)
