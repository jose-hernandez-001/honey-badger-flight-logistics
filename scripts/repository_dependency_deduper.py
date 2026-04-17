#!/usr/bin/env python3
"""Scan a local Maven-style repository for duplicate artifact versions and update pom.xml files.

This script helps with two tasks:
1) Identify dependencies/plugins downloaded in multiple versions under a local repository.
2) Update pom.xml files so duplicated artifacts use the latest discovered version (where possible).

Usage examples:
    python scripts/repository_dependency_deduper.py scan
    python scripts/repository_dependency_deduper.py scan --repo .repository
    python scripts/repository_dependency_deduper.py update --root . --write
    python scripts/repository_dependency_deduper.py prune --root . --write
    python scripts/repository_dependency_deduper.py prune --repo-only --write
    python scripts/repository_dependency_deduper.py remove-old-dependencies --root . --write
    python scripts/repository_dependency_deduper.py remove-old-dependencies --repo-only --write
"""

from __future__ import annotations

import argparse
import dataclasses
import os
import re
import shutil
import subprocess
import tempfile
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import DefaultDict, Dict, Iterable, List, Optional, Sequence, Set, Tuple


QUALIFIER_ORDER = {
    "snapshot": -5,
    "alpha": -4,
    "a": -4,
    "beta": -3,
    "b": -3,
    "milestone": -2,
    "m": -2,
    "rc": -1,
    "cr": -1,
    "": 0,
    "ga": 0,
    "final": 0,
    "release": 0,
    "sp": 1,
}

PROPERTY_REF_RE = re.compile(r"^\$\{([^}]+)\}$")


@dataclasses.dataclass(frozen=True)
class ArtifactRef:
    group_id: str
    artifact_id: str

    @property
    def ga(self) -> str:
        return f"{self.group_id}:{self.artifact_id}"


@dataclasses.dataclass
class DuplicateReport:
    artifact: ArtifactRef
    versions: List[str]
    latest: str
    is_plugin_like: bool


@dataclasses.dataclass
class PomUpdate:
    pom_path: Path
    artifact: ArtifactRef
    old_version: str
    new_version: str
    via_property: Optional[str]
    section: str


@dataclasses.dataclass
class PruneAction:
    artifact: ArtifactRef
    keep_versions: List[str]
    remove_versions: List[str]
    reason: str


# ----------------------------- Version comparison ----------------------------

def tokenize_version(version: str) -> List[str]:
    normalized = re.sub(r"[._-]+", ".", version.strip())
    return [t for t in re.findall(r"\d+|[A-Za-z]+", normalized)]


def maven_version_key(version: str) -> List[Tuple[int, object]]:
    """Best-effort comparable key for Maven-like versions.

    This is intentionally pragmatic (no external dependencies) and good enough
    for selecting "latest where possible" in local repositories.
    """

    tokens = tokenize_version(version)
    key: List[Tuple[int, object]] = []

    for token in tokens:
        if token.isdigit():
            key.append((2, int(token)))
            continue

        lowered = token.lower()
        if lowered in QUALIFIER_ORDER:
            key.append((0, QUALIFIER_ORDER[lowered]))
        else:
            key.append((1, lowered))

    return key


def latest_version(versions: Iterable[str]) -> str:
    unique = sorted(set(versions), key=maven_version_key)
    return unique[-1]


def resolve_repo_dir(repo_arg: Optional[str], root_dir: Path) -> Path:
    """Resolve repository directory from CLI arg or Maven settings.

    Resolution order:
    1) Explicit --repo value.
    2) settings.localRepository from Maven.
    3) ~/.m2/repository fallback.
    """

    if repo_arg:
        return Path(repo_arg).expanduser().resolve()

    mvn_cmd = "./mvnw" if (root_dir / "mvnw").exists() else "mvn"
    try:
        proc = subprocess.run(
            [mvn_cmd, "-q", "help:evaluate", "-Dexpression=settings.localRepository", "-DforceStdout"],
            cwd=root_dir,
            check=True,
            capture_output=True,
            text=True,
        )
        value = (proc.stdout or "").strip().splitlines()[-1].strip()
        if value and "${" not in value:
            return Path(value).expanduser().resolve()
    except (subprocess.CalledProcessError, FileNotFoundError, IndexError):
        pass

    return Path.home().joinpath(".m2", "repository").resolve()


# ------------------------------- Repo scanning -------------------------------

def looks_like_plugin(group_id: str, artifact_id: str) -> bool:
    if artifact_id.endswith("-plugin"):
        return True
    if group_id in {"org.apache.maven.plugins", "org.codehaus.mojo", "com.mycila"}:
        return True
    return False


def iter_repository_artifacts(repo_dir: Path) -> Iterable[Tuple[ArtifactRef, str]]:
    """Yield (groupId:artifactId, version) from Maven repository paths.

    Expected path structure: group/path/artifact/version/file
    """

    for root, _, files in os.walk(repo_dir):
        if not files:
            continue

        root_path = Path(root)
        rel_root = root_path.relative_to(repo_dir)
        parts = rel_root.parts
        if len(parts) < 3:
            continue

        group_parts = parts[:-2]
        artifact_id = parts[-2]
        version = parts[-1]

        if not group_parts or not artifact_id or not version:
            continue

        # Ensure there is at least one likely artifact file in this version dir.
        expected_prefix = f"{artifact_id}-{version}"
        if not any(
            f.startswith(expected_prefix) and (f.endswith(".jar") or f.endswith(".pom"))
            for f in files
        ):
            continue

        group_id = ".".join(group_parts)
        yield ArtifactRef(group_id=group_id, artifact_id=artifact_id), version


def collect_repo_version_paths(repo_dir: Path) -> Dict[ArtifactRef, Dict[str, Path]]:
    """Map artifact -> version -> version directory path in local repository."""

    result: DefaultDict[ArtifactRef, Dict[str, Path]] = defaultdict(dict)

    for root, _, files in os.walk(repo_dir):
        if not files:
            continue

        root_path = Path(root)
        rel_root = root_path.relative_to(repo_dir)
        parts = rel_root.parts
        if len(parts) < 3:
            continue

        group_parts = parts[:-2]
        artifact_id = parts[-2]
        version = parts[-1]
        expected_prefix = f"{artifact_id}-{version}"

        if not any(
            f.startswith(expected_prefix) and (f.endswith(".jar") or f.endswith(".pom"))
            for f in files
        ):
            continue

        group_id = ".".join(group_parts)
        artifact = ArtifactRef(group_id=group_id, artifact_id=artifact_id)
        result[artifact][version] = root_path

    return dict(result)


def collect_duplicates(repo_dir: Path) -> List[DuplicateReport]:
    versions_by_ga: Dict[ArtifactRef, Set[str]] = defaultdict(set)

    for artifact, version in iter_repository_artifacts(repo_dir):
        versions_by_ga[artifact].add(version)

    reports: List[DuplicateReport] = []
    for artifact, versions in versions_by_ga.items():
        if len(versions) <= 1:
            continue

        sorted_versions = sorted(versions, key=maven_version_key)
        reports.append(
            DuplicateReport(
                artifact=artifact,
                versions=sorted_versions,
                latest=sorted_versions[-1],
                is_plugin_like=looks_like_plugin(artifact.group_id, artifact.artifact_id),
            )
        )

    reports.sort(key=lambda r: (r.artifact.group_id, r.artifact.artifact_id))
    return reports


# ------------------------------- POM updating --------------------------------

def local_name(tag: str) -> str:
    if "}" in tag:
        return tag.split("}", 1)[1]
    return tag


def nsmap_from_root(root: ET.Element) -> Dict[str, str]:
    if root.tag.startswith("{"):
        uri = root.tag[1:].split("}", 1)[0]
        return {"m": uri}
    return {}


def qname(ns: Dict[str, str], name: str) -> str:
    if "m" not in ns:
        return name
    return f"{{{ns['m']}}}{name}"


def child_text(elem: ET.Element, ns: Dict[str, str], name: str) -> Optional[str]:
    child = elem.find(f"m:{name}", ns) if ns else elem.find(name)
    return child.text.strip() if child is not None and child.text else None


def child_elem(elem: ET.Element, ns: Dict[str, str], name: str) -> Optional[ET.Element]:
    return elem.find(f"m:{name}", ns) if ns else elem.find(name)


def iter_dependency_like_nodes(root: ET.Element, ns: Dict[str, str]) -> Iterable[Tuple[str, ET.Element]]:
    """Yield dependency nodes from both dependencies and dependencyManagement sections."""

    dep_paths = [
        ".//m:dependencyManagement/m:dependencies/m:dependency",
        ".//m:dependencies/m:dependency",
    ] if ns else [
        ".//dependencyManagement/dependencies/dependency",
        ".//dependencies/dependency",
    ]

    seen: Set[int] = set()
    for path in dep_paths:
        for dep in root.findall(path, ns):
            marker = id(dep)
            if marker in seen:
                continue
            seen.add(marker)
            yield "dependency", dep


def iter_plugin_nodes(root: ET.Element, ns: Dict[str, str]) -> Iterable[Tuple[str, ET.Element]]:
    plugin_paths = [
        ".//m:build/m:plugins/m:plugin",
        ".//m:build/m:pluginManagement/m:plugins/m:plugin",
    ] if ns else [
        ".//build/plugins/plugin",
        ".//build/pluginManagement/plugins/plugin",
    ]

    seen: Set[int] = set()
    for path in plugin_paths:
        for plugin in root.findall(path, ns):
            marker = id(plugin)
            if marker in seen:
                continue
            seen.add(marker)
            yield "plugin", plugin


def parse_property_map(root: ET.Element, ns: Dict[str, str]) -> Dict[str, ET.Element]:
    props = child_elem(root, ns, "properties")
    if props is None:
        return {}

    result: Dict[str, ET.Element] = {}
    for child in list(props):
        result[local_name(child.tag)] = child
    return result


def update_version_text(
    version_elem: ET.Element,
    property_map: Dict[str, ET.Element],
    new_version: str,
) -> Tuple[bool, str, Optional[str]]:
    """Update a <version> element or referenced property.

    Returns: (changed, old_version, property_name_or_none)
    """

    current = (version_elem.text or "").strip()
    if not current:
        return False, "", None

    m = PROPERTY_REF_RE.match(current)
    if m:
        prop_name = m.group(1)
        prop_elem = property_map.get(prop_name)
        if prop_elem is None or (prop_elem.text or "").strip() == new_version:
            return False, current, prop_name
        old = (prop_elem.text or "").strip()
        prop_elem.text = new_version
        return True, old, prop_name

    if current == new_version:
        return False, current, None

    version_elem.text = new_version
    return True, current, None


def update_pom_file(
    pom_path: Path,
    latest_by_ga: Dict[ArtifactRef, str],
    dry_run: bool,
) -> List[PomUpdate]:
    tree = ET.parse(pom_path)
    root = tree.getroot()
    ns = nsmap_from_root(root)
    property_map = parse_property_map(root, ns)

    updates: List[PomUpdate] = []

    # Dependencies
    for section, node in iter_dependency_like_nodes(root, ns):
        group_id = child_text(node, ns, "groupId")
        artifact_id = child_text(node, ns, "artifactId")
        if not group_id or not artifact_id:
            continue

        artifact = ArtifactRef(group_id=group_id, artifact_id=artifact_id)
        new_version = latest_by_ga.get(artifact)
        if not new_version:
            continue

        version_elem = child_elem(node, ns, "version")
        if version_elem is None:
            # No explicit version in this POM: inherited/managed, skip.
            continue

        changed, old_version, prop_name = update_version_text(version_elem, property_map, new_version)
        if not changed:
            continue

        updates.append(
            PomUpdate(
                pom_path=pom_path,
                artifact=artifact,
                old_version=old_version,
                new_version=new_version,
                via_property=prop_name,
                section=section,
            )
        )

    # Build plugins
    for section, node in iter_plugin_nodes(root, ns):
        group_id = child_text(node, ns, "groupId") or "org.apache.maven.plugins"
        artifact_id = child_text(node, ns, "artifactId")
        if not artifact_id:
            continue

        artifact = ArtifactRef(group_id=group_id, artifact_id=artifact_id)
        new_version = latest_by_ga.get(artifact)
        if not new_version:
            continue

        version_elem = child_elem(node, ns, "version")
        if version_elem is None:
            continue

        changed, old_version, prop_name = update_version_text(version_elem, property_map, new_version)
        if not changed:
            continue

        updates.append(
            PomUpdate(
                pom_path=pom_path,
                artifact=artifact,
                old_version=old_version,
                new_version=new_version,
                via_property=prop_name,
                section=section,
            )
        )

    if updates and not dry_run:
        tree.write(pom_path, encoding="UTF-8", xml_declaration=True)

    return updates


def discover_poms(root_dir: Path) -> List[Path]:
    poms = [p for p in root_dir.rglob("pom.xml") if ".repository" not in p.parts]
    poms.sort()
    return poms


def resolve_property_ref(raw: str, property_map: Dict[str, ET.Element]) -> Optional[str]:
    m = PROPERTY_REF_RE.match((raw or "").strip())
    if not m:
        return raw.strip() if raw else None
    prop_name = m.group(1)
    prop_elem = property_map.get(prop_name)
    if prop_elem is None or not prop_elem.text:
        return None
    return prop_elem.text.strip()


def collect_declared_versions_from_poms(root_dir: Path) -> Dict[ArtifactRef, Set[str]]:
    """Collect explicitly declared dependency/plugin versions from pom.xml files."""

    required: DefaultDict[ArtifactRef, Set[str]] = defaultdict(set)

    for pom in discover_poms(root_dir):
        try:
            tree = ET.parse(pom)
        except ET.ParseError:
            continue

        root = tree.getroot()
        ns = nsmap_from_root(root)
        property_map = parse_property_map(root, ns)

        for _, node in iter_dependency_like_nodes(root, ns):
            group_id = child_text(node, ns, "groupId")
            artifact_id = child_text(node, ns, "artifactId")
            version_elem = child_elem(node, ns, "version")
            if not group_id or not artifact_id or version_elem is None:
                continue
            resolved = resolve_property_ref((version_elem.text or "").strip(), property_map)
            if not resolved:
                continue
            required[ArtifactRef(group_id, artifact_id)].add(resolved)

        for _, node in iter_plugin_nodes(root, ns):
            group_id = child_text(node, ns, "groupId") or "org.apache.maven.plugins"
            artifact_id = child_text(node, ns, "artifactId")
            version_elem = child_elem(node, ns, "version")
            if not artifact_id or version_elem is None:
                continue
            resolved = resolve_property_ref((version_elem.text or "").strip(), property_map)
            if not resolved:
                continue
            required[ArtifactRef(group_id, artifact_id)].add(resolved)

    return dict(required)


def parse_dependency_list_output(output_file: Path) -> Dict[ArtifactRef, Set[str]]:
    """Parse mvn dependency:list output and return required GA:version set."""

    required: DefaultDict[ArtifactRef, Set[str]] = defaultdict(set)
    if not output_file.exists():
        return {}

    line_re = re.compile(
        r"^\s*([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):[A-Za-z0-9_.-]+:([A-Za-z0-9_.+\-]+):[A-Za-z]+"
    )

    for line in output_file.read_text(encoding="utf-8", errors="ignore").splitlines():
        m = line_re.match(line)
        if not m:
            continue
        group_id, artifact_id, version = m.groups()
        required[ArtifactRef(group_id, artifact_id)].add(version)

    return dict(required)


def collect_required_versions(root_dir: Path) -> Dict[ArtifactRef, Set[str]]:
    """Collect required versions from current project dependencies and explicit POM declarations."""

    required: DefaultDict[ArtifactRef, Set[str]] = defaultdict(set)

    # 1) Project dependency graph (runtime/test deps)
    with tempfile.NamedTemporaryFile(prefix="mvn-deps-", suffix=".txt", delete=False) as tmp:
        output_file = Path(tmp.name)

    try:
        mvn_cmd = [
            "./mvnw" if (root_dir / "mvnw").exists() else "mvn",
            "-q",
            "dependency:list",
            "-DincludeScope=test",
            "-DexcludeTransitive=false",
            f"-DoutputFile={output_file}",
            "-DoutputAbsoluteArtifactFilename=false",
        ]
        subprocess.run(mvn_cmd, cwd=root_dir, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for art, versions in parse_dependency_list_output(output_file).items():
            required[art].update(versions)
    except (subprocess.CalledProcessError, FileNotFoundError):
        # Fall through to POM-declared versions only.
        pass
    finally:
        try:
            output_file.unlink(missing_ok=True)
        except OSError:
            pass

    # 2) Explicit versions declared in pom.xml files (dependencies + plugins)
    for art, versions in collect_declared_versions_from_poms(root_dir).items():
        required[art].update(versions)

    return dict(required)


def plan_prune_actions(
    repo_versions: Dict[ArtifactRef, Dict[str, Path]],
    required_versions: Dict[ArtifactRef, Set[str]],
    force_latest: bool,
    include_plugins: bool,
) -> List[PruneAction]:
    actions: List[PruneAction] = []

    for artifact, version_map in sorted(repo_versions.items(), key=lambda x: (x[0].group_id, x[0].artifact_id)):
        if not include_plugins and looks_like_plugin(artifact.group_id, artifact.artifact_id):
            continue

        versions = sorted(version_map.keys(), key=maven_version_key)
        if len(versions) <= 1:
            continue

        latest = versions[-1]
        req = set(required_versions.get(artifact, set()))

        if force_latest:
            keep = {latest}
            reason = "force-latest"
        else:
            # Safe mode: keep latest plus any explicitly required older versions.
            keep = {latest}
            keep.update(v for v in req if v in version_map)
            reason = "keep-required-and-latest"

        remove = [v for v in versions if v not in keep]
        if not remove:
            continue

        actions.append(
            PruneAction(
                artifact=artifact,
                keep_versions=sorted(keep, key=maven_version_key),
                remove_versions=remove,
                reason=reason,
            )
        )

    return actions


def apply_prune_actions(
    repo_versions: Dict[ArtifactRef, Dict[str, Path]],
    actions: Sequence[PruneAction],
    write: bool,
) -> Tuple[int, int]:
    removed_dirs = 0
    removed_artifacts = 0

    for action in actions:
        removed_artifacts += 1
        version_map = repo_versions[action.artifact]
        for version in action.remove_versions:
            path = version_map.get(version)
            if path is None:
                continue
            if write:
                shutil.rmtree(path, ignore_errors=True)
            removed_dirs += 1

    return removed_artifacts, removed_dirs


# --------------------------------- Output ------------------------------------

def print_duplicate_report(reports: Sequence[DuplicateReport]) -> None:
    if not reports:
        print("No duplicate artifact versions found.")
        return

    dep_count = sum(1 for r in reports if not r.is_plugin_like)
    plugin_count = sum(1 for r in reports if r.is_plugin_like)

    print(f"Found {len(reports)} duplicate artifacts ({dep_count} dependency-like, {plugin_count} plugin-like).")
    print()

    for rep in reports:
        kind = "plugin" if rep.is_plugin_like else "dependency"
        print(f"- {rep.artifact.ga} [{kind}]")
        print(f"  versions: {', '.join(rep.versions)}")
        print(f"  latest:   {rep.latest}")


def print_updates(updates: Sequence[PomUpdate], dry_run: bool) -> None:
    if not updates:
        print("No POM updates were necessary.")
        return

    mode = "DRY RUN" if dry_run else "UPDATED"
    print(f"{mode}: {len(updates)} version reference(s).")
    for upd in updates:
        path = str(upd.pom_path)
        via = f" via property {upd.via_property}" if upd.via_property else ""
        print(
            f"- {path}: {upd.artifact.ga} {upd.old_version} -> {upd.new_version} "
            f"({upd.section}{via})"
        )


def print_prune_actions(actions: Sequence[PruneAction], write: bool) -> None:
    if not actions:
        print("No prune actions are required.")
        return

    mode = "PRUNE" if write else "DRY RUN PRUNE"
    print(f"{mode}: {len(actions)} artifact(s) have removable duplicate versions.")
    for action in actions:
        print(f"- {action.artifact.ga}")
        print(f"  keep:   {', '.join(action.keep_versions)}")
        print(f"  remove: {', '.join(action.remove_versions)}")
        print(f"  reason: {action.reason}")


# ---------------------------------- CLI --------------------------------------

def cmd_scan(args: argparse.Namespace) -> int:
    root_dir = Path(args.root).resolve()
    repo_dir = resolve_repo_dir(args.repo, root_dir)
    if not repo_dir.exists():
        print(f"Repository path does not exist: {repo_dir}", file=sys.stderr)
        return 2

    reports = collect_duplicates(repo_dir)
    print_duplicate_report(reports)
    return 0


def cmd_update(args: argparse.Namespace) -> int:
    root_dir = Path(args.root).resolve()
    repo_dir = resolve_repo_dir(args.repo, root_dir)

    if not repo_dir.exists():
        print(f"Repository path does not exist: {repo_dir}", file=sys.stderr)
        return 2
    if not root_dir.exists():
        print(f"Root path does not exist: {root_dir}", file=sys.stderr)
        return 2

    reports = collect_duplicates(repo_dir)
    latest_by_ga = {r.artifact: r.latest for r in reports}

    poms = discover_poms(root_dir)
    if not poms:
        print(f"No pom.xml files found under: {root_dir}")
        return 0

    all_updates: List[PomUpdate] = []
    for pom in poms:
        all_updates.extend(update_pom_file(pom, latest_by_ga, dry_run=not args.write))

    print_duplicate_report(reports)
    print()
    print_updates(all_updates, dry_run=not args.write)

    if not args.write:
        print()
        print("No files were changed (dry run). Re-run with --write to apply updates.")

    return 0


def cmd_prune(args: argparse.Namespace) -> int:
    root_dir = Path(args.root).resolve()
    repo_dir = resolve_repo_dir(args.repo, root_dir)

    if not repo_dir.exists():
        print(f"Repository path does not exist: {repo_dir}", file=sys.stderr)
        return 2
    if not root_dir.exists():
        print(f"Root path does not exist: {root_dir}", file=sys.stderr)
        return 2

    repo_versions = collect_repo_version_paths(repo_dir)
    required_versions = {} if args.repo_only else collect_required_versions(root_dir)
    actions = plan_prune_actions(
        repo_versions,
        required_versions,
        force_latest=(args.force_latest or args.repo_only),
        include_plugins=args.include_plugins,
    )

    print_prune_actions(actions, write=args.write)
    removed_artifacts, removed_dirs = apply_prune_actions(repo_versions, actions, write=args.write)

    print()
    if args.write:
        print(f"Removed {removed_dirs} version directory(ies) across {removed_artifacts} artifact(s).")
    else:
        print("No files were removed (dry run). Re-run with --write to apply pruning.")

    return 0


def cmd_remove_old_dependencies(args: argparse.Namespace) -> int:
    """Remove old dependency versions from the selected repository.

    This is a dependency-focused alias for prune:
    - plugins are excluded by default
    - latest is always kept
    - optionally force latest-only keep behavior
    """

    root_dir = Path(args.root).resolve()
    repo_dir = resolve_repo_dir(args.repo, root_dir)

    if not repo_dir.exists():
        print(f"Repository path does not exist: {repo_dir}", file=sys.stderr)
        return 2
    if not root_dir.exists():
        print(f"Root path does not exist: {root_dir}", file=sys.stderr)
        return 2

    repo_versions = collect_repo_version_paths(repo_dir)
    required_versions = {} if args.repo_only else collect_required_versions(root_dir)
    actions = plan_prune_actions(
        repo_versions,
        required_versions,
        force_latest=(args.force_latest or args.repo_only),
        include_plugins=False,
    )

    print_prune_actions(actions, write=args.write)
    removed_artifacts, removed_dirs = apply_prune_actions(repo_versions, actions, write=args.write)

    print()
    if args.write:
        print(f"Removed {removed_dirs} old dependency version directory(ies) across {removed_artifacts} artifact(s).")
    else:
        print("No files were removed (dry run). Re-run with --write to remove old dependency versions.")

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Detect duplicate Maven artifact versions in .repository and update pom.xml files."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    scan = sub.add_parser("scan", help="Report duplicated artifacts in the local repository")
    scan.add_argument("--repo", default=None, help="Path to local Maven-style repository (defaults to Maven local repo)")
    scan.add_argument("--root", default=".", help="Project root used to resolve default Maven local repository")
    scan.set_defaults(func=cmd_scan)

    update = sub.add_parser("update", help="Update pom.xml files to latest duplicated versions")
    update.add_argument("--repo", default=None, help="Path to local Maven-style repository (defaults to Maven local repo)")
    update.add_argument("--root", default=".", help="Root directory to search for pom.xml files")
    update.add_argument("--write", action="store_true", help="Apply changes (default is dry-run)")
    update.set_defaults(func=cmd_update)

    prune = sub.add_parser(
        "prune",
        help="Remove unneeded duplicate versions from local repository, keeping latest/required versions",
    )
    prune.add_argument("--repo", default=None, help="Path to local Maven-style repository (defaults to Maven local repo)")
    prune.add_argument("--root", default=".", help="Project root used to detect required versions")
    prune.add_argument("--write", action="store_true", help="Apply deletion (default is dry-run)")
    prune.add_argument(
        "--include-plugins",
        action="store_true",
        help="Also prune old plugin versions (default only prunes dependencies)",
    )
    prune.add_argument(
        "--force-latest",
        action="store_true",
        help="Keep only the latest version per artifact, even if an older version appears required",
    )
    prune.add_argument(
        "--repo-only",
        action="store_true",
        help="Do not scan project dependencies/poms; keep only latest versions from the local repository",
    )
    prune.set_defaults(func=cmd_prune)

    remove_old = sub.add_parser(
        "remove-old-dependencies",
        help="Remove old dependency versions from local repository (plugins excluded)",
    )
    remove_old.add_argument("--repo", default=None, help="Path to local Maven-style repository (defaults to Maven local repo)")
    remove_old.add_argument("--root", default=".", help="Project root used to detect required versions")
    remove_old.add_argument("--write", action="store_true", help="Apply deletion (default is dry-run)")
    remove_old.add_argument(
        "--force-latest",
        action="store_true",
        help="Keep only latest dependency versions, even if older versions appear required",
    )
    remove_old.add_argument(
        "--repo-only",
        action="store_true",
        help="Do not scan project dependencies/poms; keep only latest dependency versions from the local repository",
    )
    remove_old.set_defaults(func=cmd_remove_old_dependencies)

    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except BrokenPipeError:
        # Allow piping output to head/tail without noisy tracebacks.
        try:
            sys.stdout.close()
        except OSError:
            pass
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
