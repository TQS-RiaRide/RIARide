"""Prepares a report's Markdown for the course Word template (used by build-report.sh).

The template numbers its headings itself, so the manual numbers ("### 3.2 Title") are removed and
each level moves up one. The first four lines (title, blank, authors, version) become the
document's title, author and date; relative links become links to the repository on GitHub; and
a paragraph that is only bold text uses the template's inner heading style.

Usage, from the repository root: python3 docs/reports/prepare-markdown.py SOURCE.md OUTPUT.md
"""

import posixpath
import re
import sys
from pathlib import Path

REPOSITORY = "https://github.com/TQS-RiaRide/RIARide/blob/dev/"

source, target = Path(sys.argv[1]), Path(sys.argv[2])
source_dir = posixpath.dirname(source.as_posix())
lines = source.read_text(encoding="utf-8").split("\n")
title, author, version = lines[0].lstrip("# ").strip(), lines[2].rstrip(), lines[3].strip()
body = "\n".join(lines[4:])

body = re.sub(r"^(#{2,4}) [0-9]+(\.[0-9]+)*\.? ", r"\1 ", body, flags=re.M)
body = re.sub(r"^#(#+) ", r"\1 ", body, flags=re.M)
body = re.sub(
    r"\]\((\.\./[^)#]*)([^)]*)\)",
    lambda m: "](" + REPOSITORY + posixpath.normpath(posixpath.join(source_dir, m.group(1))) + m.group(2) + ")",
    body,
)
body = re.sub(
    r"^\*\*([^*]+)\*\*\s*$",
    lambda m: '::: {custom-style="heading_inner"}\n' + m.group(1) + "\n:::",
    body,
    flags=re.M,
)
header = f'---\ntitle: "{title}"\nauthor: "{author}"\ndate: "{version}"\n---\n'
target.write_text(header + body, encoding="utf-8")
