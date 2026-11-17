"""Remove the com.springsource.bundlor.maven plugin blocks from a pom.

Usage: strip-bundlor.py <input pom> <output pom>
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
with open(src, encoding="utf-8") as f:
    pom = f.read()

pattern = re.compile(
    r"[ \t]*<plugin>\s*<groupId>com\.springsource\.bundlor</groupId>.*?</plugin>[ \t]*\n",
    re.DOTALL,
)
pom, removed = pattern.subn("", pom)
if removed == 0 or "com.springsource.bundlor" in pom:
    sys.exit("bundlor plugin blocks not found or not fully removed (removed=%d)" % removed)

# maven-jar-plugin keeps <manifestFile>${manifest.file}</manifestFile>
# (target/classes/META-INF/MANIFEST.MF). Bundlor used to generate that file; it is
# now copied from src/main/resources/META-INF/MANIFEST.MF, the OSGi manifest of the
# published 1.13.23.RELEASE jar, so the jar keeps its bundle headers.
if "<manifestFile>${manifest.file}</manifestFile>" not in pom:
    sys.exit("expected <manifestFile>${manifest.file}</manifestFile> in the parent pom")

with open(dst, "w", encoding="utf-8") as f:
    f.write(pom)
print("removed %d bundlor plugin block(s) -> %s" % (removed, dst))
