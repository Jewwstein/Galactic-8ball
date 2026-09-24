from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter, ImageChops, ImageOps

ROOT=Path("app/src/main/assets/extracted")

def find(suffix):
    xs=list(ROOT.glob("*"+suffix))
    if not xs:
        raise FileNotFoundError(suffix)
    return xs[0]

def bake_relief(base,bump,strength=0.12):
    bump=bump.convert("L").resize(base.size,Image.Resampling.LANCZOS)
    # Use the original Unity bump map as a subtle baked micro-surface cue.
    relief=bump.filter(ImageFilter.EMBOSS)
    relief=ImageEnhance.Contrast(relief).enhance(1.35)
    neutral=Image.new("L",base.size,128)
    relief=Image.blend(neutral,relief,strength)
    relief_rgb=Image.merge("RGB",(relief,relief,relief))
    # Overlay preserves the original color art while adding local surface definition.
    return ImageChops.overlay(base.convert("RGB"),relief_rgb)

felt_path=find("_Felt.png")
felt_bump=find("_FeltBump.png")
felt=Image.open(felt_path).convert("RGB")
felt=bake_relief(felt,Image.open(felt_bump),0.10)
felt=ImageEnhance.Contrast(felt).enhance(1.035)
felt=felt.filter(ImageFilter.UnsharpMask(radius=1.4,percent=118,threshold=2))
felt.save(felt_path,"PNG",compress_level=3)
print("HD felt",felt_path,felt.size)

wood_path=find("_Wood.png")
wood_bump=find("_WoodBump.png")
wood=Image.open(wood_path).convert("RGB").resize((2048,2048),Image.Resampling.LANCZOS)
wood=bake_relief(wood,Image.open(wood_bump),0.19)
wood=ImageEnhance.Contrast(wood).enhance(1.075)
wood=ImageEnhance.Color(wood).enhance(1.035)
wood=wood.filter(ImageFilter.UnsharpMask(radius=1.8,percent=155,threshold=2))
wood.save(wood_path,"PNG",compress_level=3)
print("HD wood",wood_path,wood.size)

foot_path=find("_Foot.png")
foot=Image.open(foot_path).convert("RGBA").resize((1024,1024),Image.Resampling.LANCZOS)
rgb=foot.convert("RGB")
rgb=rgb.filter(ImageFilter.UnsharpMask(radius=1.3,percent=145,threshold=2))
rgb=ImageEnhance.Contrast(rgb).enhance(1.05)
rgb.putalpha(foot.getchannel("A"))
rgb.save(foot_path,"PNG",compress_level=3)
print("HD foot",foot_path,rgb.size)
