# fabric / Minecraft 1.21.5: version sources

This branch builds only Minecraft **1.21.5** with **fabric**. `targets.json` contains one dependency entry, and `common/gradle/minecraft-target.gradle` rejects every other `mcTarget`.

| Field | Value |
| --- | --- |
| Minecraft | 1.21.5 |
| Loader dependency | Fabric Loader 0.19.5 / Fabric API 0.128.2+1.21.5 |
| Required overlay directories | 1.21.2, 1.21.4 |
| Release JAR | `fabric/build/mc-1.21.5/libs/crafty_cards-fabric-mc1.21.5-1.0.0.jar` |

The overlays are cumulative: a later target may need source adaptations first introduced for an earlier Minecraft version. Their directory names describe where an adaptation began; they do not enable additional build targets in this branch.

Build from a fresh branch download with `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` at the repository root. The script runs this target's Gradle `build`, which includes its checks, and verifies the release JAR filename. The historical multi-version evidence under `docs/evidence/` records earlier matrix runs and is not the build procedure for this branch.
