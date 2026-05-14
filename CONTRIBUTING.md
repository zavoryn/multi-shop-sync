# Contributing to Multi-Shop Sync

感谢你对 Multi-Shop Sync 的关注！欢迎提交 Issue 和 Pull Request。

---

## 快速开始

### 1. Fork & Clone

```bash
git clone https://github.com/YOUR_USERNAME/multi-shop-sync.git
cd multi-shop-sync
```

### 2. 构建

```bash
mvn clean install
```

### 3. 创建分支

```bash
git checkout -b feature/your-feature-name
```

### 4. 提交 PR

提交 Pull Request 到 `main` 分支，填写模板中的信息。

---

## 如何新增一个渠道

这是最常见的贡献场景。完整步骤如下：

### Step 1: 新增渠道枚举

在 `ChannelEnum.java` 中添加：

```java
JD("jd", "京东"),
```

### Step 2: 创建策略类

在 `channel/jd/` 目录下创建：

```
channel/jd/
├── JdPlatformStrategy.java    # 策略实现
└── JdAuthHelper.java          # 鉴权辅助（签名/Token）
```

继承 `AbstractPlatformStrategy`，实现 4 个抽象方法：

| 方法 | 说明 |
|------|------|
| `doPushProduct` | 将 StandardProductDTO 转换为该平台 API 格式并调用 |
| `doChangeStatus` | 调用该平台的上下架 API |
| `doSyncPlatformStatus` | 主动查询该平台商品状态 |
| `doParseCallback` | 解析该平台的回调数据，返回标准状态 |

### Step 3: 编写调研文档

在 `docs/research/` 下创建该平台的 API 调研文档，包含：
- 官方文档链接（可溯源）
- 认证机制
- 签名算法
- 核心商品 API
- 状态流转机制
- 回调事件说明

### Step 4: 添加配置

在 `application.yml` 中添加该渠道的配置模板：

```yaml
channel:
  jd:
    enabled: false
    app-key: ${JD_APP_KEY:}
    app-secret: ${JD_APP_SECRET:}
    access-token: ${JD_ACCESS_TOKEN:}
```

### Step 5: 测试 & 提交

确保项目可以正常编译：`mvn clean compile`

---

## 代码规范

- **语言**: Java 8+
- **框架**: Spring Boot 2.7
- **命名**: 驼峰命名，类名用 PascalCase，方法名用 camelCase
- **注释**: 只在 WHY 不明显时写注释，不写 WHAT 注释
- **异常**: 使用 `ChannelException` 包装渠道相关的异常
- **日志**: 使用 Slf4j，关键操作必须记录日志

### Commit Message 格式

```
type(scope): description

feat(douyin): add product listing API
fix(wechat): fix token refresh timing
docs: update README with new channel
```

类型：
- `feat` — 新功能
- `fix` — 修复 Bug
- `docs` — 文档更新
- `refactor` — 重构
- `test` — 测试
- `chore` — 构建/配置

---

## PR 检查清单

提交 PR 前，请确认：

- [ ] 代码可以通过 `mvn clean compile`
- [ ] 新增渠道已包含 API 调研文档（`docs/research/`）
- [ ] 新增渠道已包含配置模板（`application.yml`）
- [ ] 没有硬编码的密钥或 Token
- [ ] 关键逻辑有日志记录

---

## Issue 模板

### Bug 报告

```
**渠道**: [抖音/小红书/微信/本地]
**API**: [具体的接口]
**期望行为**:
**实际行为**:
**错误日志**:
```

### 新渠道请求

```
**平台名称**:
**官方 API 文档链接**:
**是否已有开发者账号**: [是/否]
```

---

## 目录结构约定

```
channel/{channel-name}/
├── {ChannelName}PlatformStrategy.java   # 策略实现（必须）
├── {ChannelName}AuthHelper.java         # 鉴权辅助（如需要）
└── {ChannelName}Constants.java          # 平台常量（如需要）
```

---

## 许可

提交代码即表示你同意将代码以 MIT License 发布。
