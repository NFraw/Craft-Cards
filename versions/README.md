# neoforge / Minecraft 1.21.11: version sources

This branch builds only Minecraft **1.21.11** with **neoforge**. `targets.json` contains one dependency entry, and `common/gradle/minecraft-target.gradle` rejects every other `mcTarget`.

| Field | Value |
| --- | --- |
| Minecraft | 1.21.11 |
| Loader dependency | NeoForge 21.11.45 |
| Required overlay directories | 1.21.2, 1.21.4, 1.21.6, 1.21.9, 1.21.11 |
| Release JAR | `build/mc-1.21.11/libs/crafty_cards-neoforge-mc1.21.11-1.0.0.jar` |

The overlays are cumulative: a later target may need source adaptations first introduced for an earlier Minecraft version. Their directory names describe where an adaptation began; they do not enable additional build targets in this branch.

Build from a fresh branch download with `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` at the repository root. The script runs this target's Gradle `build`, which includes its checks, and verifies the release JAR filename. The historical multi-version evidence under `docs/evidence/` records earlier matrix runs and is not the build procedure for this branch.
