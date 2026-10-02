#!/usr/bin/env python3
"""Builds doc/requirements.pdf from doc/requirements.md.

Needs pandoc and Google Chrome (or Chromium; set CHROME to its executable). Run from anywhere:
    python3 doc/requirements-pdf/build.py
The mermaid diagram is replaced by pipeline.png, since neither tool renders mermaid.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
DOC = os.path.dirname(HERE)
TITLE = "ECCO - Recovered Requirements"


def chrome():
    candidates = [os.environ.get("CHROME"),
                  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                  shutil.which("google-chrome"), shutil.which("chromium"), shutil.which("chromium-browser")]
    for c in candidates:
        if c and os.path.exists(c):
            return c
    sys.exit("Chrome not found; set CHROME to its executable")


def main():
    src = open(os.path.join(DOC, "requirements.md"), encoding="utf-8").read()
    as_of = re.search(r"^As of (.*)\.$", src, re.M).group(1)
    sentence = re.search(r"```mermaid.*?```\n\n(.*?)\n", src, re.S).group(1)
    # the sentence goes above the picture, so it never lands alone on the next page
    src = re.sub(r"```mermaid.*?```\n\n.*?\n", sentence + "\n\n![Commit and checkout pipelines](pipeline.png)\n", src, count=1, flags=re.S)
    src = re.sub(r"## Contents\n\n(\* .*\n)+\n", "", src)  # pandoc builds its own
    src = re.sub(r"^# .*\n\nAs of .*\n", "", src)  # the title goes above the contents

    with tempfile.TemporaryDirectory() as tmp:
        shutil.copy(os.path.join(HERE, "pipeline.png"), tmp)
        shutil.copy(os.path.join(HERE, "style.css"), tmp)
        md, html = os.path.join(tmp, "req.md"), os.path.join(tmp, "req.html")
        open(md, "w", encoding="utf-8").write(src)
        subprocess.run(["pandoc", md, "-s", "--toc", "--toc-depth=2", "--metadata", "pagetitle=" + TITLE,
                        "-c", "style.css", "-o", html], check=True)
        page = open(html, encoding="utf-8").read()
        page = page.replace('<nav id="TOC" role="doc-toc">',
                            f'<h1>{TITLE}</h1><p>As of {as_of}.</p><nav id="TOC" role="doc-toc"><b>Contents</b>', 1)
        page = re.sub(r"<colgroup>.*?</colgroup>", "", page, flags=re.S)  # let columns fit their content
        open(html, "w", encoding="utf-8").write(page)
        out = os.path.join(DOC, "requirements.pdf")
        subprocess.run([chrome(), "--headless", "--disable-gpu", "--no-pdf-header-footer",
                        "--print-to-pdf=" + out, html], check=True, stderr=subprocess.DEVNULL)
        print("wrote", out)


if __name__ == "__main__":
    main()
