# Multi-Shop Sync

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-green.svg)](https://spring.io/projects/spring-boot)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

> 基于策略模式 + 工厂模式 + 模板方法，统一管理 **抖音小店 / 小红书 / 微信小店 / 本地商城** 的商品上下架与状态流转。

**English** | [中文文档](docs/README-zh.md)

---

## Why Multi-Shop Sync?

在电商多渠道运营场景中，商家需要将同一商品同时发布到多个平台。但各平台面临三个核心痛点：

| 痛点 | 具体表现 |
|------|---------|
| **API 差异大** | 鉴权方式各不相同（抖音 HMAC-SHA256、小红书 MD5、微信 Access Token） |
| **状态难统一** | 三个平台都有异步审核机制，商品状态流转复杂 |
| **重复开发高** | 每接一个新平台就要写一套完整的商品推送、状态同步、回调处理 |

本项目通过设计模式抽象，实现：
- **核心业务与平台解耦** — 新增渠道只需实现一个 Strategy 类（3步完成）
- **统一状态流转** — 标准化 DRAFT → AUDIT → ON_SHELF 的全生命周期
- **开箱即用** — 配置凭证即可接入，无需从零开发

---

## Architecture

```
                        ┌──────────────────────┐
                        │   ProductSync        │
                        │   Controller         │
                        └──────────┬───────────┘
                                   │
                        ┌──────────▼───────────┐
                        │  ProductSync         │
                        │  ServiceImpl         │
                        └──────────┬───────────┘
                                   │
                        ┌──────────▼───────────┐
                        │ PlatformStrategy     │
                        │ Factory              │
                        │ Map<Channel,Strategy>│
                        └─┬────┬────┬────┬────┘
                          │    │    │    │
                     ┌────▼┐┌──▼─┐┌─▼──┐┌▼──────┐
                     │Local││Douyin││XHS ││WeChat │
                     │     ││     ││    ││       │
                     └─────┘└─────┘└────┘└───────┘
```

### Design Patterns

| Pattern | Role | Implementation |
|---------|------|---------------|
| **Strategy** | 封装各平台 API 差异 | `IPlatformProductStrategy` — 每个渠道一个实现类 |
| **Factory** | 根据渠道标识路由 | `PlatformStrategyFactory` — Spring 自动注入，O(1) 查找 |
| **Template Method** | 公共逻辑上提 | `AbstractPlatformStrategy` — 校验、日志、重试、异常处理 |

### State Machine

```
 DRAFT ──push──▶ WAIT_AUDIT ──callback──▶ ON_SHELF / AUDIT_REJECT
                   (推送审核)        (回调通知)
                       ▲                  │
                       └── 修改后重新提交 ──┘

 ON_SHELF ──▶ OFF_SHELF (主动下架)
```

---

## Quick Start

### Prerequisites

- JDK 1.8+
- Maven 3.6+

### 1. Clone

```bash
git clone https://github.com/CHEN666333-SVG/multi-shop-sync.git
cd multi-shop-sync
```

### 2. Configure

编辑 `src/main/resources/application.yml`，填入各平台凭证（也可通过环境变量注入）：

```yaml
channel:
  douyin:
    enabled: true
    app-key: ${DOUYIN_APP_KEY:}
    app-secret: ${DOUYIN_APP_SECRET:}
    access-token: ${DOUYIN_ACCESS_TOKEN:}

  xiaohongshu:
    enabled: true
    app-id: ${XHS_APP_ID:}
    app-secret: ${XHS_APP_SECRET:}
    access-token: ${XHS_ACCESS_TOKEN:}

  wechat:
    enabled: true
    app-id: ${WECHAT_APP_ID:}
    app-secret: ${WECHAT_APP_SECRET:}
    access-token: ${WECHAT_ACCESS_TOKEN:}
```

### 3. Run

```bash
mvn spring-boot:run
```

### 4. API Call

**推送商品到抖音**:
```bash
curl -X POST http://localhost:8080/api/product/push \
  -H "Content-Type: application/json" \
  -d '{
    "channelCode": "DOUYIN",
    "product": {
      "title": "测试商品",
      "categoryId": 20000,
      "mainImages": ["https://example.com/img1.jpg"],
      "skus": [{
        "skuCode": "SKU001",
        "price": 9900,
        "stock": 100,
        "attributes": {"颜色": "红色", "尺码": "XL"}
      }]
    }
  }'
```

**Webhook 回调入口**（各平台配置此 URL）:
```
POST https://your-domain/api/webhook/douyin
POST https://your-domain/api/webhook/xiaohongshu
POST https://your-domain/api/webhook/wechat
```

---

## Supported Channels

| Channel | Status | Auth | Documentation |
|---------|--------|------|--------------|
| 抖音小店 | ✅ 已实现 | HMAC-SHA256 | [API 调研文档](docs/research/douyin-shop-api.md) |
| 小红书 | ✅ 已实现 | MD5 签名 | [API 调研文档](docs/research/xiaohongshu-api.md) |
| 微信小店 | ✅ 已实现 | Access Token | [API 调研文档](docs/research/wechat-shop-api.md) |
| 本地商城 | ✅ 已实现 | — | 直接入库 |
| 更多平台... | 🔜 欢迎贡献 | — | [贡献指南](CONTRIBUTING.md) |

---

## Add a New Channel (3 Steps)

**Step 1** — 创建策略类，继承 `AbstractPlatformStrategy`：

```java
@Component
public class JDPlatformStrategy extends AbstractPlatformStrategy {

    @Override
    public ChannelEnum getChannel() {
        return ChannelEnum.JD;  // 在 ChannelEnum 中新增枚举值
    }

    @Override
    protected boolean doPushProduct(StandardProductDTO product) {
        // 将标准模型转换为京东 API 参数并调用
        return true;
    }

    @Override
    protected boolean doChangeStatus(String outProductId, ProductStatusEnum status) {
        // 调用京东上下架 API
        return true;
    }

    @Override
    protected void doSyncPlatformStatus(String outProductId) {
        // 主动查询京东商品状态
    }

    @Override
    protected ProductStatusEnum doParseCallback(Map<String, Object> callbackData) {
        // 解析京东回调数据，返回标准状态
        return ProductStatusEnum.ON_SHELF;
    }
}
```

**Step 2** — 在 `ChannelEnum` 中新增枚举值。

**Step 3** — 在 `application.yml` 中添加新渠道的凭证配置。

就这样。工厂会自动发现并注册新策略（Spring `ApplicationContextAware`），无需改动任何现有代码。

---

## Project Structure

```
multi-shop-sync/
├── docs/
│   ├── research/                  # 各平台 API 调研文档（带溯源链接）
│   │   ├── douyin-shop-api.md
│   │   ├── xiaohongshu-api.md
│   │   └── wechat-shop-api.md
│   └── architecture.md            # 架构设计详解
├── src/main/java/.../sync/
│   ├── common/enums/              # ChannelEnum, ProductStatusEnum
│   ├── common/dto/                # StandardProductDTO, StandardSkuDTO
│   ├── common/exception/          # ChannelException
│   ├── strategy/                  # 核心：策略接口 + 工厂 + 抽象基类
│   ├── channel/douyin/            # 抖音策略 + 签名辅助
│   ├── channel/xiaohongshu/       # 小红书策略 + 签名辅助
│   ├── channel/wechat/            # 微信策略 + Token 管理
│   ├── channel/local/             # 本地商城策略
│   ├── controller/                # REST API 控制器
│   ├── service/                   # 业务服务层
│   └── config/                    # Spring 配置
├── src/main/resources/
│   ├── application.yml
│   └── db/schema.sql              # 数据库建表（4张表）
├── CONTRIBUTING.md
├── CLAUDE.md
└── pom.xml
```

---

## API Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/product/push` | POST | 推送商品到指定渠道 |
| `/api/product/list` | POST | 批量推送商品到多个渠道 |
| `/api/product/changeStatus` | POST | 统一上下架控制 |
| `/api/product/syncStatus` | POST | 主动同步外部平台状态 |
| `/api/webhook/{channel}` | POST | 统一 Webhook 回调入口 |

---

## Platform API Research

每个平台的 API 调研文档都包含：认证机制、签名算法、商品 API、状态流转、回调事件，以及原始文档链接可溯源。

| Platform | Research Doc | Official API |
|----------|-------------|-------------|
| 微信小店 | [wechat-shop-api.md](docs/research/wechat-shop-api.md) | [微信开放平台](https://developers.weixin.qq.com/doc/store/shop/) |
| 抖音小店 | [douyin-shop-api.md](docs/research/douyin-shop-api.md) | [抖店开放平台](https://op.jinritemai.com/docs/api-docs) |
| 小红书 | [xiaohongshu-api.md](docs/research/xiaohongshu-api.md) | [小红书开放平台](https://open.xiaohongshu.com) |

---

## Contributing

欢迎贡献代码！无论是新增渠道、修复 Bug、还是改进文档。

详见 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## License

[MIT License](LICENSE)

---

## Acknowledgments

- [抖音开放平台](https://op.jinritemai.com) — 抖店 API
- [小红书开放平台](https://open.xiaohongshu.com) — 小红书电商 API
- [微信开放平台](https://developers.weixin.qq.com) — 微信小店 API
