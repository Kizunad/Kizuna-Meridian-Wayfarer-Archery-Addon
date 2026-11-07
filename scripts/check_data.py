"""检查行矢数据包、硬依赖和唯一目录资源。"""

import json
from pathlib import Path
import struct
import sys

NAMESPACE = "meridian_wayfarer"
DAMAGE_TYPES = {"bow_strike_main", "bow_strike_off"}
ARROWS = {"minecraft:arrow", "minecraft:tipped_arrow", "minecraft:spectral_arrow"}
TECHNIQUES = (
    "full_draw", "pearls", "bow_strike", "dry_draw", "repeater",
    "gale", "kneeling", "parthian", "arrow_rain", "convergence",
)
VOLUMES = ("bow_upper", "crossbow", "bow_lower")


def read_json(path: Path, failures: list[str]):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        failures.append(f"{path}: JSON 无效：{error}")
        return {}


def require_tag(resources: Path, path: str, expected: set[str], failures: list[str]) -> None:
    tag = read_json(resources / path, failures)
    values = tag.get("values", [])
    if tag.get("replace") is not False or not isinstance(values, list):
        failures.append(f"{path}: 标签必须追加，且 values 必须为数组")
    elif any(not isinstance(value, str) for value in values):
        failures.append(f"{path}: 标签成员必须为资源 ID")
    elif set(values) != expected or len(values) != len(set(values)):
        failures.append(f"{path}: 标签成员错误或重复：{values}")


def check_metadata(root: Path, resources: Path, failures: list[str]) -> None:
    metadata = read_json(resources / "fabric.mod.json", failures)
    if metadata.get("id") != NAMESPACE or metadata.get("environment") != "*":
        failures.append("正式数据包必须在两端提供同一卷轴目录")
    if metadata.get("entrypoints") or metadata.get("mixins"):
        failures.append("正式数据包不得包含入口或 mixin")
    for dependency in ("meridian_technique", "meridian_vanilla_damage", "meridian_human_host"):
        if dependency not in metadata.get("depends", {}):
            failures.append(f"缺少硬依赖：{dependency}")
    catalog_path = f"data/{NAMESPACE}/technique_catalog.txtpb"
    if metadata.get("custom", {}).get("meridian_technique:catalogs") != catalog_path:
        failures.append("正式元数据必须声明行矢唯一目录")
    catalog = root / "shared/src/main/resources" / catalog_path
    sources = list((root / "shared/src/main/resources").rglob("*.txtpb"))
    if sources != [catalog]:
        failures.append("shared 必须只有一份行矢 txtpb 目录")
    if list((root / "fabric/src").rglob("*.java")) or list((root / "shared/src").rglob("*.java")):
        failures.append("正式模块不得携带 Java 源码")


def check_artwork(resources: Path, failures: list[str]) -> None:
    textures = resources / f"assets/{NAMESPACE}/textures"
    expected = {}
    for technique in TECHNIQUES:
        expected[f"gui/technique/{technique}.png"] = (32, 32)
        expected[f"gui/technique/{technique}_64.png"] = (64, 64)
    for volume in VOLUMES:
        expected[f"item/{volume}_scroll.png"] = (32, 32)
        expected[f"gui/scroll/{volume}.png"] = (1536, 1024)
    expected_paths = set(expected)
    actual = {path.relative_to(textures).as_posix() for path in textures.rglob("*") if path.is_file()}
    if actual != expected_paths:
        missing = sorted(expected_paths - actual)
        extra = sorted(actual - expected_paths)
        failures.append(f"美术资源清单不符：缺少 {missing}，多余 {extra}")
    for relative, size in expected.items():
        path = textures / relative
        if not path.is_file():
            continue
        with path.open("rb") as image:
            header = image.read(24)
        if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
            failures.append(f"{relative}: PNG 文件头无效")
        elif struct.unpack(">II", header[16:24]) != size:
            failures.append(f"{relative}: 图片尺寸必须为 {size[0]} × {size[1]}")


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    resources = root / "fabric/src/main/resources"
    failures: list[str] = []
    check_metadata(root, resources, failures)
    check_artwork(resources, failures)
    damage_dir = resources / f"data/{NAMESPACE}/damage_type"
    if {path.stem for path in damage_dir.glob("*.json")} != DAMAGE_TYPES:
        failures.append("弓梢必须恰有主手、副手两种伤害类型")
    for name in DAMAGE_TYPES:
        damage = read_json(damage_dir / f"{name}.json", failures)
        if damage.get("message_id") != f"{NAMESPACE}.bow_strike":
            failures.append(f"{name}: 伤害显示名错误")
        if damage.get("scaling") != "when_caused_by_living_non_player":
            failures.append(f"{name}: 伤害难度缩放声明错误")
    all_damage = {f"{NAMESPACE}:{name}" for name in DAMAGE_TYPES}
    require_tag(resources, "data/meridian/tags/damage_type/melee.json", all_damage, failures)
    require_tag(resources, "data/meridian/tags/damage_type/kind/blunt.json", all_damage, failures)
    require_tag(resources, "data/meridian/tags/damage_type/off_hand.json",
                {f"{NAMESPACE}:bow_strike_off"}, failures)
    require_tag(resources, f"data/{NAMESPACE}/tags/item/bows.json", {"minecraft:bow"}, failures)
    require_tag(resources, f"data/{NAMESPACE}/tags/item/crossbows.json", {"minecraft:crossbow"}, failures)
    require_tag(resources, f"data/{NAMESPACE}/tags/item/ammo.json", ARROWS, failures)
    require_tag(resources, f"data/{NAMESPACE}/tags/item/crossbow_ammo.json",
                {f"#{NAMESPACE}:ammo", "minecraft:firework_rocket"}, failures)
    for language in ("zh_cn", "en_us"):
        read_json(resources / f"assets/{NAMESPACE}/lang/{language}.json", failures)
    if failures:
        print("数据检查失败：\n" + "\n".join(failures), file=sys.stderr)
        return 1
    print("数据检查通过：行矢目录、26 张美术资源、两种弓梢伤害、追加标签和无入口元数据。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
