#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Builds the Play Store graphics into fastlane/metadata/android/<locale>/images/.

1. Runs StoreScreenshotTest (Robolectric) to render the raw app screens, the 512 px icon and
   the bare waves, with the static Shoreline theme and sample data.
2. Frames each screen with a caption, and draws the 1024 x 500 feature graphic, as HTML pages
   rendered by a headless Chrome.

Usage: scripts/build-store-graphics.py [--skip-gradle]

Needs JDK 17 and the Android SDK, like the build, plus Chrome or Chromium. Set CHROME to the
browser binary if it isn't found on PATH.
"""

import argparse
import html
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "build" / "store-graphics"
FONT = ROOT / "app" / "src" / "main" / "res" / "font" / "manrope.ttf"
METADATA = ROOT / "fastlane" / "metadata" / "android"

# Shoreline palette (app/src/main/java/io/reddevs/tidemark/ui/theme/Theme.kt).
SAND = "#F5F3EE"
DEEP_TEAL = "#123F45"
TEAL = "#1F6670"
FOAM = "#F5EBD8"

LOCALES = {
    "en-US": {
        "raw": "en",
        "tagline": "Your Health Connect data. Offline and read-only.",
        "captions": [
            "Your day at a glance",
            "Every stage of last night's sleep",
            "Trends by day, week, month and year",
            "Resting heart rate, with each day's range",
            "Browse every record, by app",
            "Every field, formatted and raw",
            "Light or dark, always offline",
        ],
    },
    "fr-FR": {
        "raw": "fr",
        "tagline": "Vos données Connexion Santé. Hors ligne, en lecture seule.",
        "captions": [
            "Votre journée en un coup d’œil",
            "Chaque phase de votre nuit",
            "Tendances par jour, semaine, mois et année",
            "Fréquence cardiaque au repos et écarts du jour",
            "Tous vos enregistrements, par application",
            "Chaque champ, formaté et brut",
            "Clair ou sombre, toujours hors ligne",
        ],
    },
}

SCREENS = [
    "1-dashboard",
    "2-sleep-day",
    "3-steps-month",
    "4-resting-heart-rate-month",
    "5-exercise-list",
    "6-exercise-detail",
    "7-dashboard-dark",
]

STYLE = f"""
@font-face {{ font-family: Manrope; src: url("{FONT.as_uri()}"); font-weight: 200 800; }}
* {{ margin: 0; box-sizing: border-box; }}
html, body {{ overflow: hidden; }}
body {{ font-family: Manrope, sans-serif; -webkit-font-smoothing: antialiased; }}
"""

SCREENSHOT = """<!doctype html>
<html><head><meta charset="utf-8"><style>{style}
body {{ width: 1080px; height: 1920px; background: {sand}; position: relative; }}
h1 {{ position: absolute; top: 96px; left: 90px; right: 90px; text-align: center;
     font-size: 64px; line-height: 1.2; font-weight: 700; color: {deep}; letter-spacing: -0.5px;
     text-wrap: balance; }}
.rule {{ position: absolute; top: 300px; left: 390px; width: 300px; height: 14px;
        border-top: 3px solid {teal}; border-bottom: 3px solid {teal}; opacity: .35; }}
.phone {{ position: absolute; top: 360px; left: 120px; width: 840px; height: 1700px;
         padding: 14px; border-radius: 64px; background: #1B2B2D;
         box-shadow: 0 24px 60px rgba(18, 63, 69, .18); }}
.phone img {{ display: block; width: 812px; border-radius: 52px; }}
</style></head><body>
<h1>{caption}</h1>
<div class="rule"></div>
<div class="phone"><img src="{screen}"></div>
</body></html>
"""

FEATURE = """<!doctype html>
<html><head><meta charset="utf-8"><style>{style}
body {{ width: 1024px; height: 500px; background: {teal}; position: relative; overflow: hidden; }}
img {{ position: absolute; top: -60px; right: -40px; width: 620px; opacity: .95; }}
.text {{ position: absolute; left: 72px; top: 0; bottom: 0; width: 470px;
        display: flex; flex-direction: column; justify-content: center; }}
h1 {{ font-size: 96px; font-weight: 800; color: {foam}; letter-spacing: -2px; }}
p {{ margin-top: 18px; font-size: 30px; line-height: 1.3; font-weight: 500; color: #CFE6E8; }}
</style></head><body>
<img src="{waves}">
<div class="text"><h1>Tidemark</h1><p>{tagline}</p></div>
</body></html>
"""


def find_chrome() -> str:
    candidates = [os.environ.get("CHROME")]
    candidates += [shutil.which(name) for name in ("chromium", "chromium-browser", "google-chrome", "chrome-headless-shell")]
    candidates += [str(p) for p in sorted(Path.home().glob(".cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell"))]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    sys.exit("build-store-graphics: no Chrome found; set CHROME to its binary")


def render(chrome: str, page: str, size: tuple[int, int], target: Path, workdir: Path) -> None:
    source = workdir / (target.stem + ".html")
    source.write_text(page, encoding="utf-8")
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [chrome, "--headless", "--no-sandbox", "--hide-scrollbars", "--allow-file-access-from-files",
         "--force-device-scale-factor=1", f"--window-size={size[0]},{size[1]}",
         "--virtual-time-budget=5000", f"--screenshot={target}", source.as_uri()],
        check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    if not target.is_file():
        sys.exit(f"build-store-graphics: Chrome wrote no {target}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--skip-gradle", action="store_true", help="reuse the screens already in build/store-graphics")
    args = parser.parse_args()

    if not args.skip_gradle:
        if RAW.exists():
            shutil.rmtree(RAW)
        subprocess.run(
            ["./gradlew", ":app:testDebugUnitTest", "--tests", "io.reddevs.tidemark.store.*", "--rerun"],
            cwd=ROOT, check=True, env={**os.environ, "TIDEMARK_STORE_OUT": str(RAW)},
        )

    chrome = find_chrome()
    icon = RAW / "icons" / "icon-512.png"
    waves = RAW / "icons" / "waves-1080.png"
    with tempfile.TemporaryDirectory() as tmp:
        workdir = Path(tmp)
        for locale, text in LOCALES.items():
            images = METADATA / locale / "images"
            shots = images / "phoneScreenshots"
            if shots.exists():
                shutil.rmtree(shots)
            for index, (screen, caption) in enumerate(zip(SCREENS, text["captions"]), start=1):
                raw = RAW / "screens" / text["raw"] / f"{screen}.png"
                if not raw.is_file():
                    sys.exit(f"build-store-graphics: missing {raw}")
                page = SCREENSHOT.format(style=STYLE, sand=SAND, deep=DEEP_TEAL, teal=TEAL,
                                         caption=html.escape(caption), screen=raw.as_uri())
                render(chrome, page, (1080, 1920), shots / f"{index}_{locale}.png", workdir)
            page = FEATURE.format(style=STYLE, teal=TEAL, foam=FOAM, waves=waves.as_uri(),
                                  tagline=html.escape(text["tagline"]))
            render(chrome, page, (1024, 500), images / "featureGraphic.png", workdir)
            shutil.copyfile(icon, images / "icon.png")
            print(f"{locale}: {len(SCREENS)} screenshots, feature graphic, icon -> {images.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
