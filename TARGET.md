# Crafty Cards · forge / Minecraft 1.21.5

This branch contains the forge source snapshot for Minecraft 1.21.5. It keeps shared game code and the version overlays needed by this target. Earlier overlay directories are cumulative inputs, not additional build targets.

| Item | Fixed value |
| --- | --- |
| Minecraft | 1.21.5 |
| Loader dependency | Forge 1.21.5-55.1.14 |
| Java | 21 |
| Release JAR | `forge/build/mc-1.21.5/libs/crafty_cards-forge-mc1.21.5-1.0.0.jar` |

From a fresh download on Windows, run `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` at the repository root. The script builds and tests only this target, then checks the release JAR filename. A `-sources.jar` file, when produced, is source code and is not a mod to install. A different `-PmcTarget` is rejected during Gradle configuration.
