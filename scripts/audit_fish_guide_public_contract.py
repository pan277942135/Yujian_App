#!/usr/bin/env python3
"""Read-only public API/media audit: no CMS, database or GCS writes."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

ROLES = ("HERO", "IDENTIFICATION", "ECO", "GEAR", "SKILL")

def http_get(url, limit=0):
    try:
        with urlopen(Request(url, headers={"Accept": "application/json, image/*"}), timeout=12) as resp:
            content = resp.read(limit or None)
            return resp.status, resp.headers.get("Content-Type", ""), content
    except Exception as exc:
        return getattr(exc, "code", None) or "ERROR", "", str(exc).encode()[:300]

def clean(v):
    return str(v).strip() if v is not None else ""

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip("/") + "/"
    if urlsplit(base).scheme != "https":
        raise SystemExit("HTTPS base URL required")
    status, _, payload = http_get(urljoin(base, "api/v1/fish/species"))
    if status != 200:
        raise SystemExit(f"Catalog HTTP {status}: {payload[:300]!r}")
    catalog = json.loads(payload)
    if not isinstance(catalog, list):
        raise SystemExit("Catalog is not a list")
    rows = []
    for species in catalog:
        sid = clean(species.get("id"))
        if not sid or "/" in sid:
            continue
        st, _, body = http_get(urljoin(base, f"api/v1/fish/species/{sid}/detail"))
        detail = json.loads(body) if st == 200 else {}
        if not isinstance(detail, dict):
            detail = {}
        hero = {
            "role": "COVER_HERO", "species_id": sid,
            "status": clean(species.get("cover_hero_status")),
            "version_id": species.get("cover_hero_version_id"),
            "url": clean(species.get("cover_hero_image")),
            "detail_consistent": (
                detail.get("cover_hero_status") == species.get("cover_hero_status")
                and detail.get("cover_hero_version_id") == species.get("cover_hero_version_id")
                and detail.get("cover_hero_image") == species.get("cover_hero_image")
            ),
            "detail_http": st,
        }
        hero["contract_ok"] = (
            hero["status"].upper() == "ACTIVE"
            and str(hero["version_id"]).isdigit() and int(hero["version_id"]) > 0
            and bool(hero["url"]) and hero["detail_consistent"]
        )
        rows.append(hero)
        assets = detail.get("knowledge_assets", {})
        if not isinstance(assets, dict):
            assets = {}
        for role in ROLES:
            item = assets.get(role) or {}
            if not isinstance(item, dict):
                item = {}
            version = clean(item.get("version_id") or item.get("asset_version_id") or item.get("version"))
            asset_status = clean(item.get("asset_status") or item.get("status")).upper()
            asset_role = clean(item.get("asset_role") or item.get("role")).upper()
            image = clean(item.get("image_url") or item.get("url"))
            row = {
                "species_id": sid, "role": role, "status": asset_status,
                "version_id": version, "url": image, "detail_http": st,
            }
            row["contract_ok"] = (
                st == 200 and asset_status == "ACTIVE" and bool(version)
                and asset_role == role and bool(image)
            )
            rows.append(row)
    def probe(row):
        if not row["contract_ok"]:
            return None
        image_url = urljoin(base, row["url"])
        if urlsplit(image_url).scheme != "https":
            return "INVALID_URL"
        code, kind, data = http_get(image_url, limit=24)
        magic = (
            data.startswith(b"\xff\xd8\xff")
            or data.startswith(b"\x89PNG\r\n\x1a\n")
            or (data.startswith(b"RIFF") and data[8:12] == b"WEBP")
        )
        return "OK" if code == 200 and magic else f"HTTP_{code}_{kind[:30]}"
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(probe, r): r for r in rows}
        for future in as_completed(futures):
            futures[future]["image_probe"] = future.result()
    passed = sum(1 for r in rows if r.get("contract_ok") and r.get("image_probe") == "OK")
    summary = {
        "species_count": len(catalog), "expected_slots": len(catalog) * 6,
        "audited_slots": len(rows), "usable_slots": passed,
        "missing_or_invalid_contract": sum(not r["contract_ok"] for r in rows),
        "image_probe_failures": sum(r["contract_ok"] and r.get("image_probe") != "OK" for r in rows),
        "cover_hero_usable": sum(r["role"] == "COVER_HERO" and r.get("image_probe") == "OK" for r in rows),
        "knowledge_card_usable": sum(r["role"] != "COVER_HERO" and r.get("image_probe") == "OK" for r in rows),
        "base_url": base,
    }
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    Path(args.output).write_text(
        json.dumps({"summary": summary, "slots": rows}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False))
    if summary["species_count"] != 20 or passed != 120:
        raise SystemExit("FAIL_PUBLIC_FISH_GUIDE_PUBLICATION: expected 20 COVER_HERO + 100 ACTIVE/versioned/readable knowledge slots")

if __name__ == "__main__":
    main()
