# Crafty Cards · neoforge / Minecraft 1.21.11

This branch contains the neoforge source snapshot for Minecraft 1.21.11. It keeps shared game code and the version overlays needed by this target. Earlier overlay directories are cumulative inputs, not additional build targets.

| Item | Fixed value |
| --- | --- |
| Minecraft | 1.21.11 |
| Loader dependency | NeoForge 21.11.45 |
| Java | 21 |
| Release JAR | `build/mc-1.21.11/libs/crafty_cards-neoforge-mc1.21.11-1.0.0.jar` |

From a fresh download on Windows, run `powershell -ExecutionPolicy Bypass -File .\build-target.ps1` at the repository root. The script builds and tests only this target, then checks the release JAR filename. A `-sources.jar` file, when produced, is source code and is not a mod to install. A different `-PmcTarget` is rejected during Gradle configuration.
