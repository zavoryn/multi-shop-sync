# 01 · 项目架构与核心链路

> 目标：让你能在白板/纸上 5 分钟内画出整套架构 + 讲清 3 条核心链路。

---

## 一、一句话项目定位

**多渠道商品同步框架** —— 一套接口接抖音/小红书/微信小店/本地商城 4 个平台的"商品推送 + 上下架 + 状态回写"。

业务方只关心"我有一个商品要同时上 4 个平台"，不关心：
- 4 个平台鉴权各不一样（HMAC-SHA256 / MD5 / Access Token + AES）
- 4 个平台 API 字段命名、价格单位（分/元/美元）、类目体系全不同
- 4 个平台审核异步，回调结构也各不一样

---

## 二、技术栈速记

| 类别 | 选型 | 一句话理由 |
|------|------|-----------|
| 语言/框架 | Java 8 + Spring Boot 2.7 | 业务团队栈对齐 |
| 持久层 | MyBatis-Plus 3.5 | 国内生态熟、零 CRUD 代码 |
| 数据库 | MySQL 8（prod）/ H2（dev） | dev profile 零配置启动 |
| 缓存 | Caffeine | 单进程缓存够用，无 Redis 依赖；多实例场景会换 Redis |
| HTTP Client | WebClient（Spring WebFlux） | 看中其 timeout + 连接池 API；本项目仍是 MVC 同步调用 |
| 重试 | Spring Retry `@Retryable` | 注解式，与模板方法天然契合 |
| 限流 | Resilience4j RateLimiter | 比 Sentinel 轻量，per-channel 独立令牌桶 |
| 序列化 | FastJSON2 | 国内主流，性能优 |
| 工具 | Hutool（HMAC）+ commons-codec（MD5/SHA1/Base64） | 避免自己写密码学代码 |
| 测试 | JUnit 5 + Mockito + MockWebServer | MockWebServer 用于 HTTP mock |

---

## 三、整体架构图

```
                  ┌─────────────────────────┐
                  │  业务方 / 内部调用方       │
                  └────────────┬─────────────┘
                               │ HTTP REST
                  ┌────────────▼─────────────┐
                  │  ProductSyncController   │  ← 商品推送/上下架统一入口
                  └────────────┬─────────────┘
                               │
                  ┌────────────▼─────────────┐
                  │   ProductSyncService     │  ← 业务编排：查映射、查类目、状态机校验
                  └────────────┬─────────────┘
                               │
                  ┌────────────▼─────────────┐
                  │  PlatformStrategyFactory │  ← 工厂：根据 ChannelEnum 取 Strategy
                  └────────────┬─────────────┘
                               │
                  ┌────────────▼─────────────────────────────────────┐
                  │           AbstractPlatformStrategy               │
                  │  ┌──────────────────────────────────────────┐    │
                  │  │ 模板方法：校验 → 限流 → 调 doXxx → 异常分类 │   │
                  │  │ @Retryable(仅 ChannelNetworkException)    │    │
                  │  └──────────────────────────────────────────┘    │
                  └────────┬──────────┬──────────┬──────────┬────────┘
                           │          │          │          │
            ┌──────────────┘   ┌──────┘     ┌────┘    ┌─────┘
            ▼                  ▼            ▼         ▼
    ┌─────────────┐  ┌────────────────┐ ┌────────┐ ┌────────┐
    │   Local     │  │    Douyin      │ │  XHS   │ │ Wechat │
    │ (本地商城)   │  │  HMAC-SHA256   │ │  MD5   │ │ Token+ │
    │             │  │                │ │        │ │AES回调 │
    └─────────────┘  └────────────────┘ └────────┘ └────────┘
                              │              │           │
                              └──────┬───────┴───────────┘
                                     │
                  ┌──────────────────▼──────────────────┐
                  │     CaffeineTokenManager            │
                  │  Caffeine cache + 每渠道 ReentrantLock │
                  │  + @Scheduled 提前 10min 预刷新        │
                  └─────────────────────────────────────┘

横切关注点：
├─ GlobalExceptionHandler   ← @ControllerAdvice 统一异常 → HTTP code
├─ ChannelException 体系     ← Network/Business/Auth 3 子类
├─ Resilience4j RateLimiter ← 每渠道独立令牌桶
└─ WechatCallbackController ← 微信单独路由，AES 解密 + sha1 验签
```

---

## 四、核心包结构

```
src/main/java/com/github/multiplatform/sync/
├── MultiChannelSyncApplication.java   ← 入口
│
├── common/
│   ├── enums/
│   │   ├── ChannelEnum                ← LOCAL/DOUYIN/XIAOHONGSHU/WECHAT
│   │   └── ProductStatusEnum          ← DRAFT/WAIT/ON_SHELF/REJECT/OFF
│   ├── dto/
│   │   ├── StandardProductDTO         ← 系统唯一商品模型，价格单位「分」
│   │   └── StandardSkuDTO
│   ├── model/
│   │   ├── ApiResponse                ← REST 统一响应包
│   │   └── PushResult                 ← {success, channelProductId}
│   ├── exception/
│   │   ├── ChannelException           ← 基类
│   │   ├── ChannelNetworkException    ← 网络层（会重试）
│   │   ├── ChannelBusinessException   ← 业务层（不重试，4xx）
│   │   └── ChannelAuthException       ← 鉴权失败（401）
│   ├── statemachine/
│   │   └── ProductStatusMachine       ← EnumMap 静态合法转移
│   ├── auth/
│   │   ├── AccessTokenManager         ← 接口
│   │   ├── CaffeineTokenManager       ← Caffeine + 单飞实现
│   │   ├── RefreshStrategy            ← 各渠道 token 刷新策略接口
│   │   └── TokenInfo                  ← {token, expiresAt}
│   └── dao/
│       ├── entity/ (4 张表)
│       └── mapper/ (4 个 BaseMapper)
│
├── strategy/
│   ├── IPlatformProductStrategy       ← 策略接口（4 方法）
│   ├── AbstractPlatformStrategy       ← 模板方法基类
│   └── PlatformStrategyFactory        ← ApplicationContextAware 自动发现
│
├── channel/                           ← 各渠道实现
│   ├── local/      LocalPlatformStrategy
│   ├── douyin/     DouyinPlatformStrategy + DouyinAuthHelper + DouyinRefreshStrategy
│   ├── xiaohongshu/ XiaohongshuPlatformStrategy + XiaohongshuAuthHelper + XiaohongshuRefreshStrategy
│   └── wechat/     WechatPlatformStrategy + WechatAuthHelper + WechatRefreshStrategy + WechatCryptoUtil
│
├── controller/
│   ├── ProductSyncController          ← /api/product/{push,changeStatus,syncStatus}
│   ├── WebhookController              ← /api/webhook/{douyin,xiaohongshu}
│   ├── WechatCallbackController       ← /api/webhook/wechat  (单独：要解密)
│   └── GlobalExceptionHandler         ← @ControllerAdvice
│
├── service/
│   ├── ProductSyncService + impl      ← 业务编排主入口
│   ├── ChannelProductMappingService   ← outProductId ↔ channelProductId 映射
│   ├── CategoryMappingService         ← 本地类目 → 平台类目
│   └── CallbackLogService             ← 回调原始数据落库 + 幂等
│
└── config/
    ├── ChannelConfig                  ← 各渠道凭证（@ConfigurationProperties）
    ├── AsyncConfig                    ← WebClient Bean + 线程池
    ├── MybatisPlusConfig              ← 分页插件 + 自动填充
    └── TokenScheduler                 ← @Scheduled 提前刷新
```

---

## 五、4 张数据表

| 表 | 字段要点 | 作用 |
|----|---------|------|
| `channel_product_mapping` | `(out_product_id, channel)` 唯一键 + `channel_product_id` + `status` | **核心映射**：本地商品 ID → 各平台商品 ID |
| `channel_sku_mapping` | `(channel_product_mapping_id, sku_code)` + `channel_sku_id` | SKU 维度映射 |
| `category_mapping` | `(local_category_id, channel)` → `channel_category_id` | 类目映射，避免硬编码 |
| `callback_log` | `idempotency_key VARCHAR(64) UNIQUE` + `channel` + `raw_data` + `processed` | 回调原始数据落库 + **幂等去重** |

> `idempotency_key = MD5(channel + rawData)`，靠 MySQL 唯一键 + 捕获 `DuplicateKeyException` 静默去重。

---

## 六、核心调用链路（3 条主路径）

### 链路 ① ── 商品推送 push

```
[业务方]
   │ POST /api/product/push  {channelCode, product}
   ▼
[ProductSyncController]
   │ 参数校验 (@Valid)
   ▼
[ProductSyncServiceImpl#pushProduct]
   │ 1. ChannelEnum.fromCode  ← 失败抛 ChannelException → 400
   │ 2. PlatformStrategyFactory.get(channel)
   ▼
[XxxPlatformStrategy (extends Abstract)]
   │ pushProduct → 模板方法：
   │   ┌─ 1. 校验商品必填项
   │   ├─ 2. acquirePermit() ← Resilience4j 限流
   │   ├─ 3. doPushProduct() ← 子类实现：
   │   │    ├─ CategoryMappingService.translateRequired
   │   │    ├─ 拼接平台 payload（字段转换、价格换算）
   │   │    ├─ AccessTokenManager.getToken(channel)
   │   │    │   └─ Caffeine cache hit → 直接返回
   │   │    │   └─ miss → ReentrantLock → fetch
   │   │    ├─ AuthHelper.calcSign(payload)
   │   │    └─ WebClient.post().retrieve() ← 网络异常包成 ChannelNetworkException
   │   ├─ 4. catch (ChannelException) → 直接抛
   │   ├─ 5. catch (Exception) → classify() → ChannelNetworkException
   │   └─ @Retryable(ChannelNetworkException.class, maxAttempts=3, backoff×2)
   ▼
[PushResult {success=true, channelProductId="xxx"}]
   │
   ▼
[ProductSyncServiceImpl 续]
   │ ChannelProductMappingService.upsertAfterPush()
   │   → INSERT/UPDATE channel_product_mapping
   │   → 初始 status = WAIT_PLATFORM_AUDIT
   ▼
[返回 ApiResponse {code: 0, data: PushResult}]
```

**关键设计点**：
- `acquirePermit` 在重试外面：限流是"对外保护"，不能让重试绕过限流
- `@Retryable` 只匹配 `ChannelNetworkException`：业务错误（类目不存在）重试一万次也没用
- token 获取在 doPushProduct 内部，每次拿最新的；过期了下次走 refresh

---

### 链路 ② ── 状态变更 changeStatus (上下架)

```
[业务方]
   │ POST /api/product/changeStatus {channelCode, outProductId, status=OFF_SHELF}
   ▼
[ProductSyncServiceImpl#changeStatus]
   │ 1. ChannelProductMappingService.find(outProductId, channel)
   │    ↳ 找不到 → ChannelException("商品未推送")
   │ 2. ProductStatusMachine.checkTransition(currentStatus, targetStatus)
   │    ↳ 非法 → IllegalStateException → GlobalExceptionHandler → 409
   │ 3. Strategy.changeStatus(channelProductId, targetStatus)
   │    ↳ 调用平台 API，模板方法走完同 ①
   │ 4. ChannelProductMappingService.updateStatus(id, targetStatus)
   ▼
[OK]
```

**状态机表（来自 `ProductStatusMachine`）**：

```
DRAFT             → WAIT_PLATFORM_AUDIT
WAIT_PLATFORM_AUDIT → ON_SHELF | AUDIT_REJECT | OFF_SHELF
ON_SHELF          ⇄ OFF_SHELF
AUDIT_REJECT      → WAIT_PLATFORM_AUDIT | OFF_SHELF
OFF_SHELF         → ON_SHELF | WAIT_PLATFORM_AUDIT
同状态转移：允许（idempotent，应对回调重投）
```

---

### 链路 ③ ── 平台回调 webhook（含微信解密）

#### 普通渠道（抖音/小红书）

```
[平台]
   │ POST /api/webhook/{channel}  {raw json}
   ▼
[WebhookController]
   │ 1. CallbackLogService.record(channel, rawData)
   │    └─ 计算 idempotencyKey = MD5(channel + rawData)
   │    └─ INSERT callback_log (idempotency_key 唯一键)
   │    └─ 捕获 DuplicateKeyException → 静默返回 200
   │ 2. @Async("webhookExecutor") 异步分发
   ▼
[ProductSyncService#handleCallback]
   │ 1. Strategy.parseCallback(rawData) → 标准事件对象
   │    （parseCallback 是纯本地计算，不限流不重试）
   │ 2. 根据事件类型：审核通过/驳回/下架 → 更新本地状态
   │    ↳ 经过 ProductStatusMachine 校验
   ▼
[CallbackLogService.markProcessed(id)]
```

#### 微信渠道（多一层 AES 解密）

```
[微信]
   │ POST /api/webhook/wechat?msg_signature&timestamp&nonce
   │ body: <xml><Encrypt>base64(AES-CBC ciphertext)</Encrypt></xml>
   ▼
[WechatCallbackController]
   │ 1. WechatCryptoUtil.verifySignature(token, ts, nonce, encrypt)
   │    ↳ sha1(sort([token,ts,nonce,encrypt]).join("")).equals(msg_signature)
   │    ↳ 失败 → ChannelAuthException → 401
   │ 2. WechatCryptoUtil.decrypt(encrypt, encodingAesKey, appId)
   │    ↳ AES-256-CBC + PKCS7 unpad
   │    ↳ 解出来：16 字节随机 + 4 字节长度 + 明文 msg + appId
   │    ↳ 校验 appId 匹配 → 防止跨应用攻击
   │ 3. CallbackLogService.record(...)
   │ 4. @Async 分发同普通渠道
   ▼
[OK]
```

**关键安全点**：
- sha1 签名验证：防请求被篡改/伪造
- AES-CBC + PKCS7：微信官方协议，保证传输保密
- appId 校验：解密后的明文末尾必须匹配自己的 appId，否则被中间人重放

---

## 七、Token 自动刷新（单飞 + 预刷新）

这是项目最容易被面试官深挖的部分。

```
┌──────────────────────────────────────────────────────────────────┐
│                     CaffeineTokenManager                          │
│                                                                   │
│   ┌─────────────────────┐                                         │
│   │ Caffeine<Channel,    │  ← KV cache（不用 expireAfter，          │
│   │   TokenInfo>         │     由 TokenInfo.expiresAt 自管理）       │
│   └─────────────────────┘                                         │
│                                                                   │
│   ┌─────────────────────┐                                         │
│   │ Map<Channel,         │  ← 单飞锁：每个渠道一个 ReentrantLock     │
│   │   ReentrantLock>     │                                         │
│   └─────────────────────┘                                         │
│                                                                   │
│   getToken(channel):                                              │
│     1. cache.getIfPresent → valid → 直接返回                         │
│     2. miss/expired → doRefresh                                   │
│                                                                   │
│   doRefresh:                                                      │
│     1. lock.lock()                                                │
│     2. double-check：再 getIfPresent 一次（前一个线程可能已刷好）       │
│     3. 调 RefreshStrategy.fetch() → 平台 token endpoint            │
│     4. cache.put + unlock                                         │
│                                                                   │
│   refresh(channel) [forced]:                                      │
│     1. cache.invalidate(channel)  ← ⚠️ 必须先 invalidate，否则       │
│     2. doRefresh(channel, "forced")     double-check 会命中旧值     │
└──────────────────────────────────────────────────────────────────┘

       ┌─────────────────────────────────────────────────┐
       │       TokenScheduler @Scheduled                  │
       │  fixedDelay=60s, initialDelay=30s                │
       │                                                   │
       │  for each registered channel:                    │
       │    info = manager.peek(channel)                  │
       │    if info != null && info.isAboutToExpire(10min)│
       │      manager.refresh(channel)  ← 强制刷           │
       └─────────────────────────────────────────────────┘
```

**为什么这样设计**：

| 选择 | 替代方案 | 为什么 |
|------|---------|--------|
| 不用 Caffeine 的 `expireAfter` | 让 Caffeine 自动过期 | TTL 由 server 返回的 `expires_in` 决定，而非创建时刻；用 `expiresAt` 字段自管理更准 |
| 每渠道一个独立锁 | 全局一把锁 | 抖音 token 刷新阻塞了微信 token，毫无理由的串行 |
| double-check | 单层 check | 高并发下"前 100 个线程同时 miss" 全部进入 fetch → 平台限流爆炸 |
| 定时器只刷"快过期的" | 固定每小时全刷 | 减少不必要的 fetch，避免平台限流 |
| refresh() 先 invalidate | 直接 doRefresh | **真 bug**：不 invalidate 的话 double-check 命中旧值，强制刷新失效 |

---

## 八、模板方法 + 异常分类的工作流

```java
// AbstractPlatformStrategy.pushProduct 实际工作流
public final PushResult pushProduct(StandardProductDTO product) {
    validate(product);              // 1. 校验
    acquirePermit();                // 2. 限流
    try {
        return doPushProduct(product);  // 3. 子类实现
    } catch (ChannelException | IllegalStateException e) {
        throw e;                    // 4. 已分类的，直接抛
    } catch (Exception e) {
        throw classify(e);          // 5. 未分类的，按类型归类
    }
}

// classify 核心规则
private ChannelException classify(Exception e) {
    if (e instanceof WebClientResponseException) {
        int status = ((WebClientResponseException) e).getRawStatusCode();
        if (status >= 500) return new ChannelNetworkException(...);  // 5xx 重试
        if (status == 401) return new ChannelAuthException(...);     // 鉴权
        return new ChannelBusinessException(...);                    // 4xx 不重试
    }
    if (e instanceof WebClientRequestException) {  // 连接被拒/超时
        return new ChannelNetworkException(...);
    }
    return new ChannelException(...);  // 其他
}

// 注解层
@Retryable(value = ChannelNetworkException.class,  // ⚠️ 只重试网络异常
           maxAttempts = 3,
           backoff = @Backoff(delay = 1000, multiplier = 2))
public final PushResult pushProduct(...) { ... }
```

**面试问：为什么不重试所有异常？**
答：
- 业务异常（类目不存在）重 100 次还是不存在
- 鉴权异常（token 失效）重试只会被平台标黑
- 只有"瞬时网络抖动"才值得重试

---

## 九、GlobalExceptionHandler 异常映射表

| 异常 | HTTP | 场景 |
|------|------|------|
| `MethodArgumentNotValidException` | 400 | @Valid 校验失败 |
| `ChannelException` (基类) | 400 | 业务参数错误（渠道不支持等） |
| `ChannelAuthException` | 401 | 平台鉴权失败 |
| `ChannelBusinessException` | 422 | 平台业务错误（类目不存在等） |
| `IllegalStateException` | 409 | 状态机非法转移 |
| `ChannelNetworkException` | 503 | 上游不可用（重试 3 次后） |
| `Exception` | 500 + traceId | 兜底，日志可追 |

---

## 十、新增渠道的 3 步流程

这是简历里的"5-7 天缩短到 3 天"的具体支撑。

```
Step 1: ChannelEnum 加一个枚举值
   public enum ChannelEnum {
     ...
     SHOPEE("shopee", "Shopee 东南亚");
   }

Step 2: 在 channel/shopee/ 下创建 3 个类
   ├── ShopeePlatformStrategy.java  ← 继承 AbstractPlatformStrategy，实现 4 个 doXxx
   ├── ShopeeAuthHelper.java        ← 签名算法
   └── ShopeeRefreshStrategy.java   ← token 刷新（如果有）

Step 3: application.yml 加配置 + database 插类目映射
   channel:
     shopee:
       enabled: true
       appKey: ${SHOPEE_APP_KEY}
       appSecret: ${SHOPEE_APP_SECRET}

✅ 核心业务代码零改动，工厂自动发现新 Strategy
```

完工。下一份文档：踩坑录。
