#!/usr/bin/env bash
# Builds a report PDF in the course Word template from its Markdown source, for the iteration tags
# (CLAUDE.md, Git and Jira). Needs python3, pandoc and LibreOffice (soffice). Usage, from the
# repository root:
#   docs/reports/build-report.sh docs/reports/qa-manual/qa-manual.md \
#     docs/reports/templates/quality-manual-reference.docx docs/qa-manual-iter2.pdf
# The .docx is kept next to the PDF's build directory, so it can be checked in Word.
set -euo pipefail

source_md=$1
reference_docx=$2
output_pdf=$3
here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

python3 "$here/prepare-markdown.py" "$source_md" "$work/report.md"
pandoc "$work/report.md" --from markdown --to docx \
  --lua-filter "$here/docx-tables.lua" \
  --reference-doc "$reference_docx" \
  -o "$work/report.docx"
soffice --headless --convert-to pdf --outdir "$work" "$work/report.docx" > /dev/null
mv "$work/report.pdf" "$output_pdf"
echo "Wrote $output_pdf"
