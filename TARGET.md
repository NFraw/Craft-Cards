# Crafty Cards · fabric / Minecraft 1.21.3

This branch contains the fabric source snapshot for Minecraft 1.21.3. It keeps shared game code and the version overlays needed by this target. Earlier overlay directories are cumulative inputs, not additional build targets.

| Item | Fixed value |
| --- | --- |
| Minecraft | 1.21.3 |
| Loader dependency | Fabric Loader 0.19.5 / Fabric API 0.114.1+1.21.3 |
| Java | 21 |
| Release JAR | `fabric/build/mc-1.21.3/libs/crafty_cards-fabric-mc1.21.3-1.0.0.jar` |

From a fresh download on Windows, run `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` at the repository root. The script builds and tests only this target, then checks the release JAR filename. A `-sources.jar` file, when produced, is source code and is not a mod to install. A different `-PmcTarget` is rejected during Gradle configuration.
