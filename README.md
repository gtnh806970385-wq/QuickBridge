# QuickBridge（快速搭桥）

面向 **Minecraft 1.7.10 / GT New Horizons** 的客户端搭桥模组。手持方块时按逗号键（`,`）启用，准星对准空处并右键，可从邻近方块边缘延伸放置方块。

## 下载与安装

从 [GitHub Releases](https://github.com/gtnh806970385-wq/QuickBridge/releases/latest) 下载 `QuickBridge` JAR，放入客户端实例的 `mods` 目录。

## 使用

1. 在快捷栏或背包中持有可放置方块。
2. 按 `,` 开启或关闭搭桥功能。
3. 将准星对准墙面、地面或其他可连接方块旁的空处，右键放置。
4. 可在游戏控制设置中修改按键。

模组提供准星提示和半透明方块轮廓；仅在手持方块且没有直接瞄准已有方块时尝试搭桥。

## 兼容性

- Minecraft 1.7.10
- Forge 10.13.4.1614 或更新版本
- 面向 GT New Horizons 2.9.0+ 环境

## 构建

需要兼容项目 Gradle 配置的 JDK。使用 Gradle Wrapper：

```bash
./gradlew build
```

Windows 使用 `gradlew.bat build`。生成文件位于 `build/libs/`。

## 许可证

本项目使用 [MIT License](LICENSE)。
