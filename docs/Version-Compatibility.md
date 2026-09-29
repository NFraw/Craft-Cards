# 本分支版本兼容性

当前分支固定为 **forge / Minecraft 1.21.1**，依赖版本为 **Forge 1.21.1-52.1.0**。Java 需要 21。该分支的安装用产物是 `forge/build/mc-1.21.1/libs/crafty_cards-forge-mc1.21.1-1.0.0.jar`。

在仓库根目录运行 `powershell -ExecutionPolicy Bypass -File .\build-target.ps1`。构建脚本只允许 Minecraft 1.21.1，并在构建后核对 JAR 文件名。`versions/` 中较早的覆盖目录是累计的源码输入，不表示可以构建那些版本。

客户端和服务器须使用相同的 Minecraft 版本、相同加载器和对应 JAR。Fabric 分支另需匹配的 Fabric API。历史跨版本验收记录保留在 `docs/evidence/`，不改变本分支的固定目标。
