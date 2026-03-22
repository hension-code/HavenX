# HavenX

面向 Android 的免费 SSH、VNC、RDP 和 SFTP 客户端。

[English README](README.md)

## 下载

- [GitHub Releases](https://github.com/hension-code/HavenX/releases/latest)

## 上游来源声明

本仓库基于 [GlassOnTin/Haven](https://github.com/GlassOnTin/Haven) 二次开发，遵循 [GPLv3](LICENSE) 许可。

原项目版权与许可证声明均已保留。`hension-code` 在此基础上维护 HavenX 的后续改动。

## 主要功能

- 终端：支持 SSH / Mosh / Eternal Terminal，多标签会话，快捷工具栏，文本选择与复制。
- 远程桌面（VNC）：支持缩放、平移、手势和 SSH 隧道。
- 远程桌面（RDP）：基于 IronRDP，支持键鼠输入与 SSH 隧道。
- SFTP：浏览、上传、下载、排序、隐藏文件切换。
- 密钥管理：生成/导入 Ed25519、RSA、ECDSA 密钥并绑定连接。
- 连接管理：保存连接配置、指纹校验、自动重连、端口转发与跳板机。
- 安全：支持生物识别锁，无遥测，数据本地存储。

## 从源码构建

```bash
git clone https://github.com/hension-code/HavenX.git
cd HavenX
```

Windows（PowerShell）：

```powershell
.\gradlew.bat assembleDebug
```

macOS/Linux：

```bash
./gradlew assembleDebug
```

输出 APK 路径：`app/build/outputs/apk/debug/havenx-*-debug.apk`

## 许可证

[GPLv3](LICENSE)
