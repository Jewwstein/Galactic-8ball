from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

PATH = Path("app/src/main/assets/falcon/falcon_diffuse.png")

def main():
    if not PATH.exists():
        raise SystemExit(f"Missing Falcon texture: {PATH}")

    im = Image.open(PATH).convert("RGBA")

    # Keep this compatibility-friendly at 2K. 4096 textures cost 4x the GPU memory
    # and can create new device-specific issues on lower-end Android hardware.
    target = (2048, 2048)
    if im.size != target:
        im = im.resize(target, Image.Resampling.LANCZOS)

    rgba = np.asarray(im, dtype=np.float32) / 255.0
    rgb = rgba[..., :3]
    alpha = rgba[..., 3:4]

    # Neutralize the warm/beige cast while retaining a little of the original
    # paint/decal color. The Falcon should read as weathered cool gray.
    lum = (
        rgb[..., 0:1] * 0.2126
        + rgb[..., 1:2] * 0.7152
        + rgb[..., 2:3] * 0.0722
    )
    neutral = np.repeat(lum, 3, axis=2)
    rgb = neutral * 0.80 + rgb * 0.20

    # Slightly cool/neutral gray balance and darker hull tone.
    rgb[..., 0] *= 0.90
    rgb[..., 1] *= 0.94
    rgb[..., 2] *= 0.98

    # Increase separation in panel lines without crushing surface detail.
    rgb = (rgb - 0.5) * 1.14 + 0.5
    rgb = np.clip(rgb, 0.0, 1.0)
    rgb = np.power(rgb, 1.07)
    rgb *= 0.90
    rgb = np.clip(rgb, 0.0, 1.0)

    out = np.concatenate([rgb, alpha], axis=2)
    out = Image.fromarray(np.uint8(np.clip(out * 255.0 + 0.5, 0, 255)), "RGBA")

    # Restore fine panel/edge detail lost in the source while keeping compression noise controlled.
    out = out.filter(ImageFilter.UnsharpMask(radius=1.35, percent=155, threshold=3))
    out = ImageEnhance.Contrast(out).enhance(1.06)
    out = ImageEnhance.Sharpness(out).enhance(1.10)

    out.save(PATH, "PNG", optimize=True)
    print(f"Enhanced Falcon texture: {PATH} {out.size[0]}x{out.size[1]}")

if __name__ == "__main__":
    main()
