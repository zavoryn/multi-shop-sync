# CLAUDE.md — 项目上下文

> 此文件为 Claude Code（或任何 AI 编程助手）提供项目快速上下文，避免每次会话重新探索代码库。

---

## 项目概览

**Multi-Shop Sync** — 多渠道商品同步框架。基于策略模式 + 工厂模式 + 模板方法，统一管理抖音/小红书/微信小店/本地商城的商品上下架与状态流转。

## 技术栈

- Java 8, Spring Boot 2.7, Maven
- WebClient (Spring WebFlux) 作为 HTTP Client
- FastJSON2 做 JSON 序列化
- Hutool 提供工具类（HMAC 等）
- Spring Retry 做重试
- Lombok 简化 POJO

## 构建命令

```bash
mvn clean compile          # 编译
mvn spring-boot:run        # 启动（需要配置各平台凭证）
mvn clean install          # 打包
mvn test                   # 测试
```

## 关键设计决策

1. **策略接口**: `IPlatformProductStrategy` 定义 4 个方法：pushProduct / changeStatus / syncPlatformStatus / parseCallback
2. **抽象基类**: `AbstractPlatformStrategy` 用模板方法封装校验、日志、@Retryable 重试、异常包装。子类只需实现 `doXxx` 方法
3. **工厂注册**: `PlatformStrategyFactory` 通过 Spring `ApplicationContextAware` 自动发现所有 `@Component` 策略类，无需手动注册
4. **标准模型**: `StandardProductDTO` 是系统内部唯一的商品模型，各渠道 Strategy 负责转换为目标平台格式。价格单位统一为「分」
5. **状态枚举**: `ProductStatusEnum` — DRAFT / WAIT_PLATFORM_AUDIT / ON_SHELF / AUDIT_REJECT / OFF_SHELF

## 各渠道鉴权方式

| 渠道 | 签名算法 | Token 有效期 | 辅助类 |
|------|---------|-------------|--------|
| 抖音 | HMAC-SHA256 | access_token 7天 | `DouyinAuthHelper` |
| 小红书 | MD5 | accessToken 7天 | `XiaohongshuAuthHelper` |
| 微信 | Access Token (query param) | 7200秒 | `WechatAuthHelper` |

## 新增渠道的 Checklist

1. `ChannelEnum` 中新增枚举值
2. `channel/{name}/` 下创建 Strategy（继承 `AbstractPlatformStrategy`）和 AuthHelper
3. `docs/research/` 下创建 API 调研文档
4. `application.yml` 中添加配置模板
5. 数据库 `category_mapping` 表中维护类目映射

## 目录结构速查

```
src/main/java/com/github/multiplatform/sync/
├── common/enums/       → ChannelEnum, ProductStatusEnum
├── common/dto/         → StandardProductDTO, StandardSkuDTO
├── common/exception/   → ChannelException
├── strategy/           → 核心框架：IPlatformProductStrategy, AbstractPlatformStrategy, PlatformStrategyFactory
├── channel/            → 各渠道实现（douyin/xiaohongshu/wechat/local）
├── controller/         → ProductSyncController, WebhookController
├── service/            → ProductSyncService + impl
└── config/             → ChannelConfig, AsyncConfig
```

## API 调研文档位置

- `docs/research/douyin-shop-api.md`
- `docs/research/xiaohongshu-api.md`
- `docs/research/wechat-shop-api.md`

每个文档都包含：官方链接、认证机制、签名算法、核心 API、状态流转、回调事件。
