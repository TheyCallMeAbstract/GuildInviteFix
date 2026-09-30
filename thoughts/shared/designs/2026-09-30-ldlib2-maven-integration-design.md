---
date: 2026-09-30
topic: "LDLib2-Architectury Maven Integration"
status: validated
---

# LDLib2-Architectury Maven Integration — Design

## Problem Statement

GuildInviteFix v2 needs a responsive, modern in-game UI. We are adopting **LDLib2** (ModularUI, LSS stylesheets, Taffy-powered layout, XML definitions) as the UI framework, following the official LDLib2 documentation and standards.

Upstream LDLib2 (`Low-Drag-MC/LDLib2`) publishes **NeoForge-only** artifacts. The Fabric port we will consume is **`TheyCallMeAbstract/ldlib2-Architectury`** (branch `26.1`), a multi-module (common/fabric/neoforge) fork targeting Minecraft 26.1.2.

This task is **build-integration only**: add the Maven repository, declare the required dependencies, align versions/metadata, and verify the toolchain. Building the actual UI screens is a follow-up task.

## Constraints

- **GitHub Packages requires authentication** for all downloads, even for public packages. Unauthenticated requests return 404 (not 401), which is misleading. Every developer and CI job needs a token with `read:packages`.
- The fork's `fabric.mod.json` declares **hard `depends`** on:
  - `fabricloader >= 0.19.5` (our project currently pins `0.19.3` — too old)
  - `minecraft ~26.1.2`, `java >= 25`
  - `architectury >= 20.0.12` (Architectury API must be a runtime mod)
  - `yet_another_config_lib_v3 >= 3.9.1` (YACL must be a runtime mod)
  - `fabric-api *`
  - (`jei` is only `recommends` — optional, out of scope)
- The fork **bundles** (shadows) `taffy`, `kotlin-stdlib`, and the `common` module into its published jar. Consumers must **not** redeclare these; taffy's transitive fastutil is deliberately excluded to avoid shadowing Minecraft's copy.
- The fork is built with `dev.architectury.loom-no-remap` (Minecraft 26.x is unobfuscated); our project uses `net.fabricmc.fabric-loom` 1.17-SNAPSHOT. The published artifact is the shaded jar, exposed via cleared/re-pointed `apiElements`/`runtimeElements`.
- Our current `build.gradle` has **no `repositories` block** — third-party mavens must be added explicitly.
- Local environment currently has JDK 21; builds require **JDK 25** (CI already uses JDK 25).
- A GitHub Actions workflow's default `GITHUB_TOKEN` is scoped to **its own repository** and cannot read packages from a sibling repository, even under the same owner. Cross-repo package reads require a PAT.

## Approach

**Chosen: GitHub Packages Maven repository + explicit `modImplementation` dependencies.**

Rationale:

- It is the fork's documented publishing path (`:fabric:publish` → `https://maven.pkg.github.com/TheyCallMeAbstract/ldlib2-Architectury`, coordinates `com.lowdragmc.lowdraglib2:ldlib2-fabric:26.1.2.40`)
- Identical resolution behavior locally and in CI once credentials exist
- Versions are pinned via Gradle properties, giving reproducible builds

**Alternatives considered and rejected:**

- **Local source build + `publishToMavenLocal()`** — avoids the PAT requirement entirely, but adds a manual sync step and version-drift risk; not CI-friendly. Retained only as a *documented escape hatch* in the README.
- **Composite build (`includeBuild`) / vendoring the fork** — always-current source, but mixes Architectury-Loom and Fabric-Loom builds in one invocation; fragile, heavyweight, and slow. Rejected.

**Credential strategy:** resolve `username`/`password` from Gradle properties `gpr.user`/`gpr.key` (the fork's own convention, placed in `~/.gradle/gradle.properties`), falling back to `GITHUB_ACTOR`/`GITHUB_TOKEN` environment variables. When absent, configuration still succeeds (warn only); failure surfaces at resolution time with the documented troubleshooting path.

## Architecture

Repository wiring added to the mod's build:

| Repository | Provides | Auth |
|---|---|---|
| `https://maven.pkg.github.com/TheyCallMeAbstract/ldlib2-Architectury` | `com.lowdragmc.lowdraglib2:ldlib2-fabric:26.1.2.40` | required (PAT) |
| `https://maven.architectury.dev/` | `dev.architectury:architectury-fabric:20.0.12` | none |
| `https://maven.isxander.dev/releases` | `dev.isxander:yet-another-config-lib:3.9.1+26.1-fabric` | none |

Dependency declarations (plain `implementation` — this Loom generation, `net.fabricmc.fabric-loom` on unobfuscated MC 26.x, has no `mod*` configurations; Loom auto-detects `fabric.mod.json` jars on the runtime classpath, exactly as the fabric-example-mod template declares Fabric API):

- `modImplementation` → `ldlib2-fabric`
- `modImplementation` → `architectury-fabric`
- `modImplementation` → `yet-another-config-lib`

All versions live in `gradle.properties` next to the existing Fabric properties.

## Components

1. **`gradle.properties`**
   - Add: `ldlib2_version=26.1.2.40`, `architectury_api_version=20.0.12`, `yacl_version=3.9.1+26.1-fabric`
   - Bump: `loader_version` `0.19.3` → `0.19.5` (fork's minimum)
   - Align: `fabric_api_version` `0.155.2+26.1.2` → `0.155.3+26.1.2` (version the fork builds against)

2. **`build.gradle`**
   - Add a `repositories` block containing the three mavens above; GitHub Packages entry reads credentials from Gradle properties with env-var fallback (never hardcode)
   - Add the three dependency declarations (plain `implementation`, per the no-remap Loom note above)
   - Emit a configuration-time warning (not an error) when GitHub Packages credentials are missing

3. **`src/main/resources/fabric.mod.json`**
   - Add hard `depends`: `ldlib2 >= 26.1.2.40`, `architectury >= 20.0.12`, `yet_another_config_lib_v3 >= 3.9.1`
   - Raise `fabricloader` from `>= 0.19.3` to `>= 0.19.5`
   - Ranges mirror the fork's own `depends` so version skew fails loudly at game load

4. **`.github/workflows/build.yml` and `release.yml`**
   - Pass credentials into the Gradle build step via environment: `GITHUB_ACTOR` from repo config/user, `GITHUB_TOKEN` from a new secret (e.g. `LDLIB_PACKAGES_READ_TOKEN`) holding a PAT with `read:packages`

5. **`README.md`**
   - Requirements section: add **Architectury API** and **YACL** as required mods alongside Fabric API; note LDLib2's loader minimum
   - Troubleshooting: GitHub Packages 404 = missing/expired token; escape hatch = build fork + `publishToMavenLocal()` + `mavenLocal()`

6. **`CHANGELOG.md`** — Unreleased entry noting the LDLib2 dependency integration

**Explicitly out of scope (YAGNI):**

- JEI integration (fork only *recommends* it)
- Embedding ldlib2 via Loom `include` (jar-in-jar) — users install it alongside, like Fabric API
- Any UI screen implementation (follow-up task)
- Local JDK 25 provisioning (tracked as environment issue, separate)

## Data Flow

**Development/CI resolution:**

1. Gradle reads `gradle.properties` versions and resolves credentials (properties → env)
2. `ldlib2-fabric` downloads from GitHub Packages (authenticated)
3. Its POM brings `architectury-fabric` and YACL; Gradle finds them in the Architectury/Isxander mavens
4. Loom places all three on the mod classpath; `runClient` launches with them as loaded mods
5. `ldlib2.mixins.json` applies inside the dev environment normally

**End-user runtime:**

- The shipped mod JAR declares hard `depends`, so Fabric Loader requires the user to install **Fabric API + Architectury API + YACL + LDLib2 (fork build)** in `mods/`, failing fast with a clear message otherwise

## Error Handling

- **Missing/invalid GitHub Packages token** → surfaces as a 404 "could not resolve". Mitigations: configuration-time warning when creds are absent; README troubleshooting section with PAT creation steps (`read:packages`, fine-grained, access to the fork repo)
- **Expired/rotated PAT in CI** → build fails at dependency resolution; documented secret rotation in README/CI notes
- **Version skew** (fork bumps its hard deps) → our `depends` ranges mirror the fork's; mismatch fails at game launch with Loader's missing-dependency screen, not silent breakage
- **Loom mismatch** (fabric-loom consuming a loom-no-remap/shadow artifact) → expected to be a no-op because 26.x is unobfuscated; verified by the build + runClient checks below. Fallback if it fails: consume the jar through a plain `files()`/flat dependency and report upstream
- **GitHub Packages retention pruning** (inactive versions may be deleted) → version is pinned; the fork's `publish.yml` also attaches `ldlib2-fabric-*.jar` to GitHub Releases, and `publishToMavenLocal()` remains the recovery path

## Testing Strategy

1. **Resolution check** — `./gradlew dependencies --configuration compileClasspath` (and `dependencyInsight` for each new coordinate) confirms ldlib2/architectury/YACL resolve, and that taffy/kotlin-stdlib/fastutil do **not** leak in transitively
2. **Compile check** — `./gradlew build` on JDK 25 (CI-provisioned; local must use JDK 25)
3. **Compile-proof** — a minimal class referencing the LDLib2 UI API (e.g. ModularUI) proves the artifact is consumable from our Java sources; can be removed after verification or evolved into the first real screen
4. **Runtime smoke** — `./gradlew runClient`: confirm `ldlib2`, `architectury`, `yet_another_config_lib_v3` load, LDLib2 mixins apply without errors, and our mod loads on top
5. **CI** — PR run of `build.yml` green with the package-read secret wired; `release.yml` unaffected except for the same env wiring
6. **Negative test** — with credentials removed, build fails at resolution and the configuration-time warning appears (documents the failure mode)

## Open Questions

- **PAT issuance**: who mints the `read:packages` token (fine-grained vs classic, user- vs repo-scoped), and where is it stored for CI? Blocks CI wiring only; local dev can proceed with a personal token.
- **Local JDK 25**: environment currently has JDK 21. Provisioning (install/SDKMAN/toolchain auto-download) is tracked separately from this task; CI already uses JDK 25.
