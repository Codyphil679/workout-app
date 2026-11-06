"""Builds docs/LiftLog-Manual.pdf from docs/MANUAL.md.

Usage (from the repo root; needs `pip install markdown` and Node with Playwright):
    python3 docs/tools/build_manual.py docs/MANUAL.md /tmp/manual.html
    node docs/tools/render_pdf.js /tmp/manual.html docs/LiftLog-Manual.pdf
"""
import markdown, os, re, sys
src, out = sys.argv[1], sys.argv[2]
base = 'file://' + os.path.dirname(os.path.abspath(src)) + '/' 
md = open(src).read()
# Python-Markdown needs a blank line before a list (GitHub doesn't); add one where missing.
ITEM = re.compile(r'^\s*(?:[-*] |\d+\. )')
lines = md.split('\n'); fixed = []
for line in lines:
    prev = fixed[-1] if fixed else ''
    if ITEM.match(line) and prev.strip() and not ITEM.match(prev) and not prev.startswith((' ', '|')):
        fixed.append('')
    fixed.append(line)
md = '\n'.join(fixed)
# Emoji may not exist in the PDF renderer's fonts: swap for coloured dots / text.
md = md.replace('🟠 Orange', '<span class="dot" style="background:#FF6B35"></span> Orange')
md = md.replace('🟢 Lime', '<span class="dot" style="background:#9CCB1F"></span> Lime')
md = md.replace('🔵 Blue', '<span class="dot" style="background:#3DD9FF"></span> Blue')
md = md.replace('🏆', 'a trophy')
body = markdown.markdown(md, extensions=['tables', 'toc', 'md_in_html'])
# First H1 becomes the cover.
body = re.sub(r'<h1[^>]*>(.*?)</h1>', '', body, count=1)
html = f'''<!DOCTYPE html><html><head><meta charset="utf-8"><base href="{base}">
<title>LiftLog user manual</title><style>
@page {{ size: 5.5in 8.5in; margin: 0.45in 0.42in 0.5in; @bottom-center {{ content: counter(page); }} }}
body {{ font-family: "Roboto", "Helvetica Neue", Arial, sans-serif; color:#15181d; font-size:9.6pt; line-height:1.45; }}
.cover {{ background:#0a0c0f; color:#f2f4f7; border-radius:16px; padding:28px 24px; margin-bottom:18px; }}
.cover .eyebrow {{ color:#c6ff3d; font-size:8pt; font-weight:800; letter-spacing:1.6px; text-transform:uppercase; }}
.cover h1 {{ font-size:28pt; font-weight:900; margin:6px 0 10px; letter-spacing:-0.5px; }}
.cover p {{ color:#a3abb8; margin:0; }}
h2 {{ font-size:15pt; font-weight:900; margin:22px 0 8px; padding-bottom:4px; border-bottom:3px solid #c6ff3d; break-after:avoid; }}
h3 {{ font-size:11.5pt; font-weight:800; margin:14px 0 4px; break-after:avoid; }}
p, li {{ margin:4px 0; }} ul, ol {{ padding-left:18px; margin:4px 0; }}
hr {{ display:none; }}

table {{ width:100%; border-collapse:collapse; font-size:8.4pt; margin:8px 0; break-inside:avoid; }}
th {{ text-align:left; font-size:7.4pt; text-transform:uppercase; letter-spacing:.8px; color:#6b7380; border-bottom:2px solid #e5e7eb; padding:5px; }}
td {{ border-bottom:1px solid #eef0f3; padding:5px; vertical-align:top; }}
code {{ background:#eef1f4; border-radius:4px; padding:0 4px; font-size:8.6pt; }}
strong {{ font-weight:800; }}
a {{ color:#3f6212; text-decoration:none; }}
p:has(> img) {{ text-align:center; break-inside:avoid; margin:10px 0; }}
img {{ width:40%; border-radius:14px; border:3px solid #0a0c0f; }}
.dot {{ display:inline-block; width:9px; height:9px; border-radius:50%; vertical-align:middle; }}
</style></head><body>
<div class="cover"><div class="eyebrow">User manual</div><h1>LiftLog</h1>
<p>Build programs, log every set, rest on time, and see your progress. Everything on this phone, nothing in the cloud.</p></div>
{body}
</body></html>'''
open(out, 'w').write(html)
