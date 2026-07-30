#!/usr/bin/env python3
"""Generate legacy and adaptive launcher assets for Black Swan: War Impact Map."""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "assets" / "black_swan_icon_source.png"
RES = ROOT / "app" / "src" / "main" / "res"
LEGACY_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def masked_icon(source: Image.Image, size: int, *, circular: bool) -> Image.Image:
    scale = 4
    canvas_size = size * scale
    image = source.resize((canvas_size, canvas_size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (canvas_size, canvas_size), 0)
    draw = ImageDraw.Draw(mask)
    if circular:
        draw.ellipse((0, 0, canvas_size - 1, canvas_size - 1), fill=255)
    else:
        radius = round(canvas_size * 0.21)
        draw.rounded_rectangle((0, 0, canvas_size - 1, canvas_size - 1), radius=radius, fill=255)
    image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    border = round(min(source.size) * 0.035)
    source = source.crop((border, border, source.width - border, source.height - border))

    for density, size in LEGACY_SIZES.items():
        output = RES / density
        output.mkdir(parents=True, exist_ok=True)
        masked_icon(source, size, circular=False).save(output / "ic_launcher.webp", "WEBP", lossless=True)
        masked_icon(source, size, circular=True).save(output / "ic_launcher_round.webp", "WEBP", lossless=True)

    adaptive = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    adaptive_art = masked_icon(source, 300, circular=False)
    adaptive.alpha_composite(adaptive_art, ((432 - 300) // 2, (432 - 300) // 2))
    adaptive_output = RES / "drawable-nodpi" / "ic_launcher_swan_foreground.png"
    adaptive_output.parent.mkdir(parents=True, exist_ok=True)
    adaptive.save(adaptive_output, optimize=True)

    preview = masked_icon(source, 512, circular=False)
    preview.save(ROOT / "tools" / "assets" / "black_swan_launcher_preview.png", optimize=True)
    print("Generated launcher icons for 5 densities plus adaptive foreground")


if __name__ == "__main__":
    main()
