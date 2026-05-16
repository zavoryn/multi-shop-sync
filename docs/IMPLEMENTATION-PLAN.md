# 实施方案：把多平台同步框架补全到生产可用

> 起草日期：2026-05-16
> 维护人：Ming Chen + Claude
> 状态文件：本文件是 single source of truth，每个阶段做完后更新 ✅
> 范围声明：**框架内功优先**，不做真实 API 联调（无沙箱凭证），但所有外部 HTTP 调用通过 WebClient 接口暴露，方便后续接真实环境/MockWebServer 单测

---

## 0. 目标与边界

### 要做（in scope）
1. 补全持久层 —— 4 张表（`channel_product_mapping` / `channel_sku_mapping` / `category_mapping` / `callback_log`）的 Entity / Mapper / Service
2. 修复阻塞性 bug：WebhookController 路由冲突
3. 引入状态机校验，杜绝非法状态跳转
4. 接入 Caffeine + 定时任务做 Token 自动刷新（含单飞机制）
5. 完成 `outProductId ↔ external_product_id` 映射查询、类目映射查询
6. 回调幂等（基于 `event_id` 或 `raw_data hash`）+ 异步处理（用上 `webhookExecutor`）
7. 完成微信回调验签（AES-CBC + sha1）
8. 区分异常类型，只对网络异常重试；引入 Resilience4j 限流
9. 全局异常处理 + WebClient Bean 复用 + timeout
10. 三个渠道 `doSyncPlatformStatus` 完整解析返回 → 写库
11. 单测覆盖：签名算法、状态机、TokenManager、各 Strategy 的参数转换
12. 配置分 profile（dev=H2 / prod=MySQL），补 `docs/README-zh.md`

### 不做（out of scope，留 follow-up）
- 真实平台 API 联调（需要沙箱凭证）
- Observability（Micrometer / Prometheus）
- 鉴权认证（这是 B 端框架，不做对外 API 鉴权）
- 国际化、多语言

---

## 1. 技术决策（已确认）

| 维度 | 选型 | 备注 |
|------|------|------|
| 持久层 | **MyBatis-Plus 3.5.x** | 国内生态熟，CRUD 零代码 |
| 数据库 | **MySQL 8 + H2 并存** | dev profile 用 H2 内存库，prod 用 MySQL |
| 缓存 | **Caffeine** | 单实例够用，无 Redis 依赖 |
| HTTP | 维持 **WebClient** | 但封装成 Bean 复用，加 timeout |
| 限流 | **Resilience4j RateLimiter** | 每渠道独立令牌桶 |
| 重试 | **Spring Retry**（已有）| 重新分类异常 |
| 测试 | **JUnit 5 + Mockito + MockWebServer** | OkHttp 自带的 MockWebServer |

---

## 2. 现状盘点

参见上一轮分析（24 个问题），按严重程度分组：
- 🔴 阻塞性：#1 路由冲突、#2 持久层缺失、#3 ID 映射、#4 类目映射
- 🟠 必修：#5 Token 刷新、#6 微信验签、#7 syncStatus 半成品、#8 回调幂等、#9 状态机
- 🟡 健壮性：#10 重试分类、#11 WebClient 反模式、#12 限流、#13 全局异常、#14 测试缺失、#15 配置分层
- 🟢 文档/小毛病：#16 README 链接、#17 异常风格、#18 observability

---

## 3. 实施阶段

### Phase 0 ── 基础设施铺垫（依赖 + 配置）
**目标**：把所有新依赖和配置文件就位，但不动业务代码。

**改动清单**：
- `pom.xml`：新增 mybatis-plus-boot-starter、mysql-connector-j、h2、caffeine、resilience4j-spring-boot2、reactor-test、okhttp/mockwebserver(test scope)
- `src/main/resources/application.yml`：拆成 `application.yml` + `application-dev.yml` + `application-prod.yml`
- `src/main/resources/db/schema.sql`：补 H2 兼容版本（去掉 `ENGINE=InnoDB`、`COMMENT=`），生产/测试统一
- `src/main/resources/db/data.sql`：dev 环境的种子数据（类目映射示例）
- 新增 `MybatisPlusConfig`：分页插件 + 自动填充 created_time/updated_time

**验收**：`mvn clean compile` 通过；启动后 H2 控制台能看到 4 张表。

---

### Phase 1 ── 修复阻塞性 bug（路由冲突）
**目标**：让项目至少能 `mvn spring-boot:run` 启动。

**改动**：
- `WebhookController.java`：删除重复的 `@PostMapping("/wechat")` 方法；微信验签作为 Phase 6 的事重写
- 增加 `@Async("webhookExecutor")` 注解到 `ProductSyncService.handleCallback`
- 控制器先快速记录原始数据到 `callback_log` 再异步分发

**验收**：启动无 `Ambiguous mapping` 异常；POST `/api/webhook/douyin` 在 5ms 内返回，业务异步处理。

---

### Phase 2 ── 持久层落地
**目标**：4 张表对应的 Entity/Mapper/Service 全部就绪，业务代码所有 `// TODO: 更新数据库` 真实写入。

**新增包结构**：
```
common/dao/entity/
  ├── ChannelProductMapping.java      ← 渠道商品映射表
  ├── ChannelSkuMapping.java
  ├── CategoryMapping.java
  └── CallbackLog.java
common/dao/mapper/
  ├── ChannelProductMappingMapper.java
  ├── ChannelSkuMappingMapper.java
  ├── CategoryMappingMapper.java
  └── CallbackLogMapper.java
service/
  ├── ChannelProductMappingService.java（含查询+upsert）
  ├── CategoryMappingService.java
  └── CallbackLogService.java
```

**业务接入点**：
| 文件 | 改动 |
|------|------|
| `ProductSyncServiceImpl.pushProduct` | 推送成功后 upsert 到 `channel_product_mapping`，状态置为 `WAIT_PLATFORM_AUDIT` |
| `ProductSyncServiceImpl.handleCallback` | 根据 `outProductId` 查映射表，更新 `local_status`、`external_status`、`reject_reason` |
| `ProductSyncServiceImpl.changeStatus` | 通过 `outProductId` + `channel` 查 `external_product_id`，传给 Strategy |
| `LocalPlatformStrategy` | 商品入库到 `channel_product_mapping`（local channel 直接 ON_SHELF） |
| 三个外部渠道 Strategy | `doChangeStatus(outProductId, status)` 改签名为 `doChangeStatus(externalId, status)`，由 service 层提前查库 |

**验收**：跑一次 `pushProduct` → mapping 表有记录 → 模拟回调 → 状态正确流转到 ON_SHELF。

---

### Phase 3 ── 状态机 + 幂等
**目标**：杜绝非法状态跳转；同一回调多次到达不重复处理。

**新增**：
- `common/statemachine/ProductStatusMachine.java`：定义合法转移图
  ```
  DRAFT → WAIT_PLATFORM_AUDIT
  WAIT_PLATFORM_AUDIT → ON_SHELF | AUDIT_REJECT
  AUDIT_REJECT → WAIT_PLATFORM_AUDIT （修改后重提）
  ON_SHELF ↔ OFF_SHELF
  ```
  方法：`canTransition(from, to)` / `assertCanTransition(from, to)`
- `common/util/IdempotencyKey.java`：生成回调幂等键 = `MD5(channel + raw_data)`，写入 `callback_log.idempotency_key`（需要给 schema 加列 + 唯一索引）
- 修改 `ProductSyncServiceImpl.handleCallback`：先 insert callback_log（重复键直接跳过），再走状态机校验后更新 mapping

**验收**：
- 单测：尝试 OFF_SHELF→AUDIT_REJECT 抛 `IllegalStateException`
- 同一 raw_data 调用两次 webhook，第二次 callback_log 不重复写、mapping 表不再 update

---

### Phase 4 ── Token 自动刷新
**目标**：三个渠道的 access_token 都能透明刷新，不出现"7 天后失效"。

**新增**：
- `common/auth/AccessTokenManager.java`（接口）：`String getToken(ChannelEnum)` + `void refresh(ChannelEnum)`
- `common/auth/CaffeineTokenManager.java`：
  - Caffeine cache：`expireAfterWrite` 略短于实际有效期
  - `refresh()` 用 `synchronized` per channel 防并发刷新（单飞）
  - 不命中时 fallback 到 channel 自己的 `RefreshStrategy.fetch()`
- `common/auth/RefreshStrategy.java`（接口）：`TokenInfo fetch()`，由各 channel 实现
  - `WechatRefreshStrategy`：调 `cgi-bin/token`，返回 `(token, 7200s)`
  - `DouyinRefreshStrategy`：调 `token/refresh`，需要 `refresh_token`
  - `XiaohongshuRefreshStrategy`：调 `oauth.getAccessToken`
- `config/TokenScheduler.java`：`@Scheduled(fixedDelay=60s)` 检查每个 channel 的剩余有效期，<10min 触发刷新

**改动**：
- 三个 AuthHelper 把 `getAccessToken()` 改成走 `tokenManager.getToken(channel)`，删除直接 `@Value` 注入
- `application.yml`：把 `access-token` 字段去掉，只保留 `app-key/app-secret/refresh-token`

**验收**：
- 单测：模拟 cache 过期 → 触发 refresh；多线程同时调 getToken 只有一次 fetch
- 集成：MockWebServer 模拟 token 接口返回，验证 strategy 调用使用了新 token

---

### Phase 5 ── doSyncPlatformStatus 全部完成
**目标**：三个渠道的主动查询接口能正确解析平台返回 → 标准状态 → 写回 DB。

**改动**：
- `DouyinPlatformStrategy.doSyncPlatformStatus`：解析 `data.check_status`（0/1/2/3 → standard）
- `XiaohongshuPlatformStrategy`：解析 `data.itemStatus`
- `WechatPlatformStrategy`：解析 `data.status`（0=初始 / 5=上架中 / 11=已下架 等）
- 通过新增的 `ChannelProductMappingService.updateStatus(...)` 持久化

**对照表**（需要在文档中维护，不全的字段会通过 ⚠️ 标注让用户补充官方文档）：

| 平台 | 字段 | 取值 | 对应标准状态 |
|------|------|------|--------------|
| 抖音 | check_status | 0=待审 1=通过 2=驳回 3=封禁 | WAIT/ON_SHELF/REJECT/OFF_SHELF |
| 小红书 | itemStatus | ⚠️ 待核 | ⚠️ |
| 微信 | status | ⚠️ 待核 | ⚠️ |

> **依赖外部输入**：对照表中 ⚠️ 的字段我会在实施时给出 best-effort 实现 + log warn，并在文档中标记需要官方文档核对。

---

### Phase 6 ── 微信回调验签
**目标**：彻底实现微信小店回调的 AES-CBC 解密 + sha1 签名校验，杜绝伪造。

**新增**：
- `channel/wechat/WechatCryptoUtil.java`：参考微信开放平台官方 SDK
  - `verifySignature(token, timestamp, nonce, msgEncrypt) → boolean`
  - `decrypt(encodingAesKey, msgEncrypt) → plaintext xml/json`
- `application.yml`：新增 `channel.wechat.token` 和 `channel.wechat.encoding-aes-key`
- `WebhookController` 微信路径独立处理：
  - 从 query 取 `signature/timestamp/nonce`
  - 从 body 取加密内容
  - 验签失败返回 401
  - 解密后再交给 `productSyncService.handleCallback`

**测试**：用微信官方 [回调验签 demo 数据](https://developers.weixin.qq.com/doc/store/shop/callback/example.html) 做单测 fixture。

> **依赖外部输入**：如果你能提供 token/encoding_aes_key/encrypted_msg 的官方示例三元组，我可以直接做单测断言。否则我先按官方算法描述实现 + 引用官方代码段。

---

### Phase 7 ── 重试分类 + 限流 + 异常处理
**目标**：业务错误不重试，只对网络/5xx 重试；接口被打爆前先被限流挡住；异常返回友好 JSON。

**改动**：
- 新增异常体系：
  - `ChannelNetworkException`（可重试）
  - `ChannelBusinessException`（不重试，4xx）
  - `ChannelAuthException`（触发 token 刷新）
- `AbstractPlatformStrategy`：
  - `@Retryable(value = ChannelNetworkException.class, ...)` 替换原 `ChannelException.class`
  - `changeStatus`、`syncPlatformStatus` 也加 `@Retryable`
  - 增加 `@Recover` 兜底：写入死信表 `failed_request_log`（可选，先 log）
- 三个 Strategy 的 HTTP 调用：根据响应分类异常（5xx → Network；业务 errcode → Business；41001 等鉴权错 → Auth）
- 新增 `config/RateLimiterConfig.java`：每渠道一个 RateLimiter Bean
  - 抖音：60/min
  - 小红书：默认 600/min（待核）
  - 微信：50/s
- `AbstractPlatformStrategy.pushProduct` 入口先 `RateLimiter.acquirePermission()`
- 新增 `controller/GlobalExceptionHandler.java`（@ControllerAdvice）：
  - `ChannelException` → 返回业务错误 ApiResponse
  - `MethodArgumentNotValidException` → 返回校验失败
  - `Exception` → 返回 500 + traceId

**改动 WebClient**：
- `AsyncConfig.webClientBuilder` 改成 `@Bean WebClient defaultWebClient`，全局复用
- 加 `responseTimeout(Duration.ofSeconds(10))` + connection pool

---

### Phase 8 ── 单元测试
**目标**：核心算法和分支覆盖单测，CI 友好。

**新增**：
```
src/test/java/com/github/multiplatform/sync/
├── channel/douyin/DouyinAuthHelperTest.java     ← HMAC-SHA256 对官方示例
├── channel/xiaohongshu/XiaohongshuAuthHelperTest.java
├── channel/wechat/WechatAuthHelperTest.java
├── channel/wechat/WechatCryptoUtilTest.java     ← 验签解密 fixture
├── common/auth/CaffeineTokenManagerTest.java    ← 单飞机制
├── common/statemachine/ProductStatusMachineTest.java
├── strategy/PlatformStrategyFactoryTest.java
└── service/ProductSyncServiceImplTest.java       ← 用 Mockito mock Strategy
```

**关键测试用例**：
- 签名算法：贴官方文档示例 → 断言 hash 一致
- TokenManager：100 个并发线程同时调 getToken → 只 fetch 一次
- 状态机：构造非法转移 → 抛异常
- Webhook 幂等：同一 raw_data 提交两次 → 数据库只插一条

**验收**：`mvn test` 全绿，覆盖率不强制但关键算法 100%。

---

### Phase 9 ── 配置 + 文档收尾
- `application-dev.yml`：H2 + Caffeine 默认 + 假 token
- `application-prod.yml`：MySQL 占位 + env 注入
- 补 `docs/README-zh.md`（README 已有引用）
- `ChannelEnum.fromCode` 改抛 `ChannelException` 而非 `IllegalArgumentException`
- 日志脱敏：`AuthHelper` 中 token/secret 任何 log 都改成 `***`
- 更新主 `README.md` 反映新的启动方式（dev profile）

---

## 4. 风险与外部依赖

我会在实施过程中遇到这些场景时**主动让你提供**：

| 场景 | 我会做什么 | 找你要什么 |
|------|-----------|-----------|
| 抖音 token refresh 接口字段 | 先按 https://op.jinritemai.com/docs/api-docs/14/13 实现 | 如果你有抖店应用控制台截图，可校对 |
| 小红书 itemStatus 取值 | 留 ⚠️ TODO + log warn | 官方文档 URL 或截图 |
| 微信 status 取值 | 同上 | 同上 |
| 微信回调验签算法 | 用 [WXBizMsgCrypt.java 官方源码](https://developers.weixin.qq.com/doc/) 移植 | 一组官方 demo 数据做 fixture |
| 各平台 QPS 限制具体数值 | 用我估的保守值 | 平台限流文档 |

---

## 5. 进度跟踪

实施过程通过 TaskCreate 维护任务列表。每个 Phase 完成后我会在本文件对应章节追加 `✅ DONE @ YYYY-MM-DD` 字样，并在 git commit 信息中引用 Phase 编号。

| Phase | 状态 | 完成日期 |
|-------|------|---------|
| Phase 0 - 基础设施 | ✅ done | 2026-05-16 |
| Phase 1 - 修路由冲突 | ✅ done | 2026-05-16 |
| Phase 2 - 持久层 | ✅ done | 2026-05-16 |
| Phase 3 - 状态机+幂等 | ✅ done | 2026-05-16 |
| Phase 4 - Token 管理 | ⏳ pending | |
| Phase 5 - syncStatus 完善 | ⏳ pending | |
| Phase 6 - 微信验签 | ⏳ pending | |
| Phase 7 - 重试/限流/异常 | ⏳ pending | |
| Phase 8 - 单元测试 | ⏳ pending | |
| Phase 9 - 配置/文档 | ⏳ pending | |

---

## 6. 我会怎么走

1. 写完文档（本步骤）→ 创建 task list
2. 按 Phase 0 → 9 串行推进；每个 Phase 完成跑一次 `mvn clean compile`
3. Phase 8 跑 `mvn test` 必须全绿
4. 任何实现拿不准的地方，**优先查官方文档**；查不到时让你提供链接/凭证/示例
5. 每个 Phase 结束在本文件标 ✅ + git commit（commit 不会自动 push）
