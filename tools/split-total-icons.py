#!/usr/bin/env python3
"""Split the generated 5x3 Total-screen icon atlas into Android PNG assets."""

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ATLAS = ROOT / "tools" / "assets" / "total_loss_icons_atlas.png"
OUTPUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
NAMES = [
    "loss_personnel",
    "loss_tanks",
    "loss_armored_vehicles",
    "loss_artillery",
    "loss_mlrs",
    "loss_air_defense",
    "loss_aircraft",
    "loss_helicopters",
    "loss_uav",
    "loss_cruise_missiles",
    "loss_ships",
    "loss_submarines",
    "loss_transport",
    "loss_special_equipment",
    "loss_ground_robots",
]


def main() -> None:
    atlas = Image.open(ATLAS).convert("RGBA")
    cell_width = atlas.width / 5
    cell_height = atlas.height / 3

    for index, name in enumerate(NAMES):
        column = index % 5
        row = index // 5
        cell = atlas.crop((
            round(column * cell_width),
            round(row * cell_height),
            round((column + 1) * cell_width),
            round((row + 1) * cell_height),
        ))
        alpha = cell.getchannel("A")
        bounds = alpha.getbbox()
        if not bounds:
            raise RuntimeError(f"No icon pixels found for {name}")
        icon = cell.crop(bounds)
        icon.thumbnail((216, 216), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        canvas.alpha_composite(icon, ((256 - icon.width) // 2, (256 - icon.height) // 2))
        canvas.save(OUTPUT / f"{name}.png", optimize=True)

    print(f"Wrote {len(NAMES)} Total-screen icons to {OUTPUT}")


if __name__ == "__main__":
    main()
