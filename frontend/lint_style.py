#!/usr/bin/env python3
"""Lint HTML/CSS/JSX/TSX against the Literary Editorial UI rules.

Usage:
  python3 lint_style.py page.html [more files...]      # exit 1 if any ERROR
  python3 lint_style.py src/ --ext .tsx .css            # walk a directory

Heuristic, not a parser: it catches the mistakes that most often break the
style (pure black text, off-palette colours, filled blue buttons, shadowed
buttons, bold serif, oversized radii, pure-white canvas). Read each finding;
a WARN may be intentional, an ERROR almost never is.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

PALETTE = {
    "#fefffc", "#ffffff", "#f9faf7", "#171717", "#2c2c2c", "#444141", "#646464",
    "#b4b8b4", "#dee2de", "#282834", "#1f1f29", "#41a1cf", "#0081c0",
    # extensions documented in references/tokens.md
    "#0077b3", "#a5462b", "#3f6b3b", "#f4f6f1", "#f1f3ee", "#d6e9f3",
}
# Scene / illustration files are allowed their own painterly palette.
SCENE_HINTS = ("scene", "illustration", "<title>moonlit", "<title>wildflower", "<title>cerulean sky", "<title>pixel flower")

HEX_RE = re.compile(r"#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b")
RULE_RE = re.compile(r"([^{}]+)\{([^{}]*)\}", re.S)
RADIUS_RE = re.compile(r"border-radius\s*:\s*([^;]+);?", re.I)
CLASS_ATTR_RE = re.compile(r'class(?:Name)?\s*=\s*["\'{`]([^"\'`}]*)', re.I)
INLINE_STYLE_RE = re.compile(r'<(\w+)([^>]*?)style\s*=\s*"([^"]*)"', re.I)

BUTTONISH = re.compile(r"\b(btn|button|cta)\b|<button", re.I)
SERIFISH = re.compile(r"font-serif|fraunces|ppmondwest|mondwest|t-display|t-heading|--font-serif", re.I)


def expand(h: str) -> str:
    h = h.lower()
    return "#" + "".join(c * 2 for c in h[1:]) if len(h) == 4 else h


def line_of(text: str, idx: int) -> int:
    return text.count("\n", 0, idx) + 1


def lint(path: Path) -> list[tuple[str, int, str]]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as e:
        return [("ERROR", 0, f"cannot read file: {e}")]
    low = text.lower()
    out: list[tuple[str, int, str]] = []
    is_scene = path.suffix == ".svg" and any(k in low for k in SCENE_HINTS)

    # 1. Pure black
    for m in re.finditer(r"(?<![\w-])color\s*:\s*(#000000|#000|black|rgb\(\s*0\s*,\s*0\s*,\s*0\s*\))", text, re.I):
        out.append(("ERROR", line_of(text, m.start()), f"pure black text `{m.group(1)}` — use #171717 Ink Black or #2c2c2c Graphite"))
    for m in re.finditer(r"\btext-black\b", text):
        out.append(("ERROR", line_of(text, m.start()), "Tailwind `text-black` — use text-ink-black / text-graphite"))

    # 2. Off-palette hex (skip scenes, skip SVG data URIs which use %23)
    if not is_scene:
        seen: set[str] = set()
        for m in HEX_RE.finditer(text):
            h = expand(m.group(0))
            if h not in PALETTE and h != "#000000" and h not in seen:
                ctx = text[max(0, m.start() - 160):m.start()].lower()
                if "<svg" in ctx and "scene" in ctx:
                    continue
                seen.add(h)
                out.append(("WARN", line_of(text, m.start()), f"off-palette colour {h} — the system is 95% neutral + one blue; map it to a token or justify it"))

    # 3. CSS rule checks (comments blanked out, newlines kept for line numbers)
    css = re.sub(r"/\*.*?\*/", lambda c: "\n" * c.group(0).count("\n"), text, flags=re.S)
    for m in RULE_RE.finditer(css):
        sel, body = m.group(1).strip(), m.group(2)
        ln = line_of(css, m.start())
        bl = body.lower()
        sel_l = sel.lower()
        if BUTTONISH.search(sel_l):
            if re.search(r"box-shadow\s*:(?!\s*(?:none|var\(--focus-ring\)))[^;]+", bl) and ":focus" not in sel_l:
                out.append(("ERROR", ln, f"`{sel[:60]}` has a box-shadow — buttons use borders, not elevation"))
            if re.search(r"background(?:-color)?\s*:\s*[^;]*(#41a1cf|#0081c0|--color-signal-blue|--color-cerulean)", bl):
                out.append(("ERROR", ln, f"`{sel[:60]}` fills a button with Signal Blue/Cerulean — blue is border/surface only"))
        if SERIFISH.search(sel_l + " " + bl) and re.search(r"font-weight\s*:\s*(600|700|800|900|bold)", bl):
            out.append(("ERROR", ln, f"`{sel[:60]}` sets serif weight ≥600 — the serif lives at 400 (500 max)"))
        if re.search(r"(?:^|[^\w-])(?:html|body|:root)$", sel_l) and re.search(r"background(?:-color)?\s*:\s*(#fff\b|#ffffff|white)", bl):
            out.append(("WARN", ln, f"`{sel[:40]}` canvas is pure white — use #fefffc Parchment"))
        if re.search(r"(?<![-\w])color\s*:\s*rgba\(", bl) and not re.search(r"nav|placeholder|on-dark|hero|frost", sel_l):
            out.append(("WARN", ln, f"`{sel[:60]}` uses transparent text colour — text colours are solid hex"))
        for rm in RADIUS_RE.finditer(body):
            for num in re.findall(r"([\d.]+)px", rm.group(1)):
                if float(num) > 24 and not re.search(r"nav|pill|avatar|dot|circle|radius-pill", sel_l + rm.group(1).lower()):
                    out.append(("WARN", ln, f"`{sel[:60]}` radius {num}px > 24px — only the nav pill goes rounder"))

    # 4. Utility-class combos (Tailwind / className)
    for m in CLASS_ATTR_RE.finditer(text):
        cls = m.group(1)
        ln = line_of(text, m.start())
        tokens = set(cls.split())
        if "font-serif" in tokens and tokens & {"font-semibold", "font-bold", "font-extrabold", "font-black"}:
            out.append(("ERROR", ln, "font-serif with semibold/bold — serif stays 400/500"))
        if any(t.startswith("bg-signal-blue") or t.startswith("bg-cerulean") for t in tokens) and re.search(r"btn|button", cls + text[max(0, m.start() - 40):m.start()], re.I):
            out.append(("ERROR", ln, "button filled with bg-signal-blue/bg-cerulean — use border-signal-blue"))
        if any(re.fullmatch(r"shadow(-\w+)?", t) for t in tokens) and re.search(r"<button|btn", text[max(0, m.start() - 60):m.start()] + cls, re.I):
            out.append(("ERROR", ln, "shadow utility on a button — remove it"))
        if tokens & {"rounded-full", "rounded-3xl"} and not re.search(r"nav|avatar|dot|pill", cls, re.I):
            out.append(("WARN", ln, f"`{' '.join(t for t in tokens if t.startswith('rounded'))}` — radius >24px is reserved for the nav pill"))

    # 5. Font sanity
    if re.search(r"font-family\s*:[^;]*\b(arial|helvetica)\b(?![^;]*inter)", low):
        out.append(("WARN", 0, "Arial/Helvetica as primary family — use Inter (af substitute)"))
    if ("<h1" in low or "t-display" in low) and "fraunces" not in low and "font-serif" not in low and "--font-serif" not in low and path.suffix in (".html", ".htm"):
        out.append(("WARN", 0, "no serif display face found — headlines ≥27px must be the serif"))

    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--ext", nargs="*", default=[".html", ".htm", ".css", ".jsx", ".tsx", ".svg"])
    a = ap.parse_args()

    files: list[Path] = []
    for p in map(Path, a.paths):
        if p.is_dir():
            files += [q for q in p.rglob("*") if q.suffix in a.ext and "node_modules" not in q.parts]
        elif p.exists():
            files.append(p)
        else:
            print(f"ERROR  {p}: not found", file=sys.stderr)
            return 2

    errors = warns = 0
    for fpath in files:
        for level, ln, msg in lint(fpath):
            print(f"{level:<5}  {fpath}:{ln}  {msg}")
            errors += level == "ERROR"
            warns += level == "WARN"
    print(f"\n{len(files)} file(s) · {errors} error(s) · {warns} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
