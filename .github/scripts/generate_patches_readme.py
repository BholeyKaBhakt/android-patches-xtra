#!/usr/bin/env python3
"""
Generates the patches section of README.md from patches-list.json
and injects it between <!-- PATCHES_START --> / <!-- PATCHES_END --> markers.

The README section is a minimal list with one line per app: its name, linking to
its section in PATCHES.md, and its supported versions. The full per-patch details
are written to PATCHES.md, where each app gets its own `## App (package)` heading
so anchors are unique and deep links work.

python3 generate_patches_readme.py <owner/repo> <branch> [patches-list.json] [README.md] [PATCHES.md]
"""

import json
import re
import sys
from html import escape
from pathlib import Path

if len(sys.argv) < 3:
    print("Usage: generate_patches_readme.py <owner/repo> <branch> [json] [README] [PATCHES]")
    sys.exit(1)

repo_full = sys.argv[1]
branch = sys.argv[2]
json_path = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("patches-list.json")
readme_path = Path(sys.argv[4]) if len(sys.argv) > 4 else Path("README.md")
patches_path = Path(sys.argv[5]) if len(sys.argv) > 5 else Path("PATCHES.md")

if "/" not in repo_full:
    raise ValueError(f"Invalid repo format: {repo_full} (expected owner/repo)")

owner, repo = repo_full.split("/", 1)

with open(json_path, encoding="utf-8") as f:
    data = json.load(f)


# Group patches by package; patches with no compatiblePackages are universal.
# JSON structure: compatiblePackages is a list of objects with
# { packageName, name, targets: [{ version, isExperimental, description }] }
by_pkg = {}  # packageName -> { name, patches, targets }
universal = {}

for patch in data["patches"]:
    compatible_packages = patch.get("compatiblePackages")
    if not compatible_packages:
        # Deduplicate universal patches by name.
        if patch["name"] not in universal:
            universal[patch["name"]] = patch
        continue

    for pkg_entry in compatible_packages:
        package_name = pkg_entry["packageName"]
        app_name = pkg_entry.get("name") or package_name
        if package_name not in by_pkg:
            by_pkg[package_name] = {
                "name": app_name,
                "patches": {},
                "targets": pkg_entry.get("targets", []),
            }

        # Deduplicate patches that appear across multiple packages.
        if patch["name"] not in by_pkg[package_name]["patches"]:
            by_pkg[package_name]["patches"][patch["name"]] = patch


def anchor(name):
    """Convert a heading to a GitHub-compatible anchor slug (github-slugger rules).

    Lowercase, drop everything except word characters, hyphens and spaces, then turn
    each space into a hyphen. Runs of hyphens are kept and nothing is trimmed, like
    GitHub does: `## PROTO - circuit simulator (com.proto.x)` anchors as
    `#proto---circuit-simulator-comprotox`.
    """
    slug = re.sub(r"[^\w\- ]", "", name.lower())
    return slug.replace(" ", "-")


def app_anchor(app_name, package_name):
    """Anchor of an app section heading: `## Name (package)`, or `## Name` without a package."""
    return anchor(f"{app_name} ({package_name})" if package_name else app_name)


def version_labels(targets):
    """Supported version labels for a package, experimental ones flagged."""
    labels = []
    for target in targets:
        version = target.get("version")
        if version is None:
            continue
        label = f"experimental {version}" if target.get("isExperimental") else version
        labels.append(label)
    return labels


def clean_description(text):
    """Collapse all whitespace in a description into single spaces."""
    return re.sub(r"\s+", " ", (text or "")).strip()


def markdown_text(text):
    """Escape HTML and the markdown characters that would change inline text, such as link text."""
    return re.sub(r"([\\`*_\[\]])", r"\\\1", escape(text, quote=False))


def markdown_cell(text):
    """Escape HTML and markdown table syntax for use inside a table cell."""
    text = escape(text, quote=False)
    return text.replace("|", "\\|").replace("\n", "<br>")


def options_summary(options, visible=3):
    """Render a compact options summary for a table details cell."""
    titles = [opt.get("title") or opt.get("key") for opt in options or []]
    titles = [title for title in titles if title]
    if not titles:
        return ""

    shown = titles[:visible]
    remaining = len(titles) - len(shown)
    summary = ", ".join(escape(title, quote=False) for title in shown)
    if remaining > 0:
        summary += f", +{remaining} more"
    return f"<br><sub>Options: {summary}</sub>"


def patches_table(patches):
    """Render a sorted markdown table of patches with full descriptions."""
    rows = [
        "| Patch | Details |",
        "|---|---|",
    ]

    for patch in sorted(patches, key=lambda p: p["name"]):
        description = clean_description(patch.get("description") or "")
        details = markdown_cell(description) + options_summary(patch.get("options") or [])
        rows.append(f"| **{escape(patch['name'], quote=False)}** | {details} |")

    return "\n".join(rows)


def versions_line(targets):
    """Render supported versions as compact inline code badges."""
    labels = version_labels(targets)
    if not labels:
        return ""
    return "**Supported versions:** " + " ".join(f"`{escape(label)}`" for label in labels)


def readme_entry(app_name, package_name, labels):
    """One line of the README list: the app name linking to PATCHES.md, then its versions."""
    link = f"[{markdown_text(app_name)}](PATCHES.md#{app_anchor(app_name, package_name)})"
    versions = " ".join(f"<kbd>{escape(label, quote=False)}</kbd>" for label in labels)
    return f"- {link} {versions}".rstrip()


def build_readme_section():
    """Build the minimal app list for the README."""
    lines = [
        f"> **[v{ver}](https://github.com/{owner}/{repo}/releases/tag/v{ver})**"
        f"&nbsp;&nbsp;&middot;&nbsp;&nbsp;`{branch}`&nbsp;&nbsp;&middot;&nbsp;&nbsp;"
        f"**{total} patches** across **{len(sorted_packages)} apps**"
        f"&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)",
        "",
    ]

    for package_name, entry in sorted_packages:
        lines.append(readme_entry(entry["name"], package_name, version_labels(entry["targets"])))

    if universal:
        lines.append(readme_entry("Universal", "", []))

    return "\n".join(lines)


def build_patches_doc():
    """Build the full PATCHES.md reference with one section per app."""
    sections = [
        "# Patches",
        "",
        f"> Generated from `patches-list.json` — **v{ver}** (`{branch}`) · "
        f"**{total} patches** across **{len(sorted_packages)} apps** · "
        f"back to [README](README.md)",
        "",
        "---",
        "",
    ]

    for package_name, entry in sorted_packages:
        patches = list(entry["patches"].values())
        sections.append(f"## {entry['name']} ({package_name})")
        sections.append("")

        versions = versions_line(entry["targets"])
        if versions:
            sections.append(versions)
            sections.append("")

        sections.append(patches_table(patches))
        sections.append("")
        sections.append("---")
        sections.append("")

    if universal:
        sections.append("## Universal")
        sections.append("")
        sections.append(patches_table(list(universal.values())))
        sections.append("")

    return "\n".join(sections).rstrip() + "\n"


# Build and inject.
raw_ver = data["version"]
ver = raw_ver.lstrip("v")
total = sum(len(entry["patches"]) for entry in by_pkg.values()) + len(universal)
sorted_packages = sorted(by_pkg.items(), key=lambda item: item[1]["name"].lower())

readme = readme_path.read_text(encoding="utf-8")

# Marker pattern matches both <!-- PATCHES_START --> and <!-- PATCHES_START EXPANDED -->.
START_PATTERN = r"<!-- PATCHES_START(?:\s+EXPANDED)?\s*-->"
END_MARKER = "<!-- PATCHES_END -->"

marker_match = re.search(START_PATTERN, readme)

if not marker_match or END_MARKER not in readme:
    # Fallback: print to stdout so CI can catch the issue.
    print(build_readme_section())
    sys.stderr.write(
        f"Markers <!-- PATCHES_START [EXPANDED] --> / {END_MARKER} not found in {readme_path}. "
        "Printed to stdout instead.\n"
    )
    sys.exit(1)

actual_start = marker_match.group(0)

generated = build_readme_section()
# A function replacement inserts the section as is: a string replacement would turn
# backslashes in it, such as markdown escapes, into regex escapes.
new_readme = re.sub(
    rf"{START_PATTERN}.*?{re.escape(END_MARKER)}",
    lambda _: f"{actual_start}\n{generated}\n{END_MARKER}",
    readme,
    count=1,
    flags=re.DOTALL,
)
readme_path.write_text(new_readme, encoding="utf-8")
patches_path.write_text(build_patches_doc(), encoding="utf-8")
print(
    f"Injected patches section into {readme_path} and wrote {patches_path} "
    f"(v{ver}, branch={branch}, {total} patches, {len(sorted_packages)} apps)"
)
