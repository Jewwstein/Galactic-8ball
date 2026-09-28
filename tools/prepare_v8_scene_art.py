from pathlib import Path
import base64, hashlib, io, zipfile

ROOT=Path(__file__).resolve().parents[1]
CHUNKS=ROOT/"tools"/"v8_art_chunks"
OUT=ROOT/"soulapp"/"src"/"main"/"res"/"drawable-nodpi"
OUT.mkdir(parents=True,exist_ok=True)

parts=sorted(CHUNKS.glob("*.b64"))
if len(parts)!=10:
    raise SystemExit(f"Expected 10 v8 chunks, found {len(parts)}")

payload="".join(p.read_text().strip() for p in parts)
raw=base64.b64decode(payload)
expected="cd3d68f54601cd0c1a59c4ce84115f05d99233287635ff95b0d9a1d9f708c19f"
actual=hashlib.sha256(raw).hexdigest()
if actual!=expected:
    raise SystemExit(f"v8 art payload checksum mismatch: {actual}")
print("v8 painted-art payload verified",actual)

required={
    "room_start.webp","room_corridor.webp","room_junction.webp","room_spider.webp",
    "mite_master.webp","spider_master.webp","slime_master.webp"
}
with zipfile.ZipFile(io.BytesIO(raw)) as z:
    names=set(z.namelist())
    missing=required-names
    if missing:
        raise SystemExit(f"Missing v8 art assets: {sorted(missing)}")
    for name in required:
        data=z.read(name)
        (OUT/name).write_bytes(data)
        print(name,len(data))
