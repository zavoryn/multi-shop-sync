# 03 · 面试 Q&A 手册（30+ 高频问题）

> 分 5 类组织：设计模式 / 并发 / 中间件 / 平台/安全 / 项目难点。
> 每题给：**陷阱** → **标准答案** → **加分点**（让面试官眼前一亮的细节）。

---

## 一、设计模式 / 架构（必问 5-8 题）

### Q1 · 为什么用策略模式？if-else 不行吗？

**陷阱**：很多人直接答"策略模式优雅"。面试官会追问"具体哪里不优雅了"。

**标准答案**：
- if-else 把 4 个渠道的差异**糊在一个方法里**，新增渠道要改这个方法，违反开闭原则
- 每个渠道有自己的鉴权、签名、字段映射、回调结构，差异巨大，糊在一起代码 500+ 行没法维护
- 策略模式把每个渠道封装成独立类，**新增渠道 = 加一个类 + 加一个枚举值**，业务代码零改动

**加分点**：
> "**我们这里其实是策略 + 工厂 + 模板方法三个模式组合用：**
> - **策略**定义接口（`IPlatformProductStrategy`）
> - **工厂**做选择（`PlatformStrategyFactory` 用 `ApplicationContextAware` 让 Spring 自动收集所有 `@Component` 策略，无需手动注册）
> - **模板方法**复用公共逻辑（`AbstractPlatformStrategy` 把校验、限流、重试、异常分类全部封到基类，子类只写 `doXxx`）
>
> 三者结合后，新增渠道是真的'零侵入'——连工厂注册都不用，启动时 Spring 自动扫描。"

---

### Q2 · PlatformStrategyFactory 怎么实现自动发现？

**标准答案**：

```java
@Component
public class PlatformStrategyFactory implements ApplicationContextAware {
    private final Map<ChannelEnum, IPlatformProductStrategy> registry = new EnumMap<>(ChannelEnum.class);

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        Map<String, IPlatformProductStrategy> beans = ctx.getBeansOfType(IPlatformProductStrategy.class);
        for (IPlatformProductStrategy s : beans.values()) {
            registry.put(s.getChannel(), s);
        }
    }

    public IPlatformProductStrategy get(ChannelEnum channel) {
        IPlatformProductStrategy s = registry.get(channel);
        if (s == null) throw new ChannelException(channel.getCode(), "未注册策略");
        return s;
    }
}
```

**加分点**：
> "用 `ApplicationContextAware` 而非 `@Autowired List<IPlatformProductStrategy>`，是因为我想以 `ChannelEnum` 为 key 直接查表 O(1)，不想每次 stream filter 一遍。EnumMap 比 HashMap 还更优——它内部就是个数组。"

---

### Q3 · AbstractPlatformStrategy 的模板方法封装了什么？

**标准答案**：

```java
public abstract class AbstractPlatformStrategy implements IPlatformProductStrategy {

    @Retryable(value = ChannelNetworkException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    @Override
    public final PushResult pushProduct(StandardProductDTO product) {
        validate(product);           // 1. 校验
        acquirePermit();             // 2. 限流
        try {
            return doPushProduct(product);  // 3. 子类实现
        } catch (ChannelException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw classify(e);       // 4. 兜底归类
        }
    }

    protected abstract PushResult doPushProduct(StandardProductDTO product);
}
```

**加分点**：
- 用 `final` 防止子类覆盖模板逻辑（强制走"我设计的工作流"）
- `@Retryable` 放在模板上，所有渠道自动获得重试能力
- `acquirePermit` 在 `try` 外面：限流是"对外保护"，不能让重试绕过限流；如果放 try 里，重试 3 次相当于 3 个令牌没了

---

### Q4 · 标准模型 StandardProductDTO 设计原则是什么？

**标准答案**：
- **系统内部唯一的商品模型**，业务方只跟它打交道
- **价格单位统一为「分」**（避免浮点精度问题，也避免渠道间换算混乱）
- 各渠道 Strategy 负责把它转成平台特定 payload（"反腐败层" Anti-Corruption Layer，DDD 概念）
- 字段做最小公约数，渠道特有字段通过 `Map<String, Object> extraAttributes` 兜底

**加分点**：
> "**这是 Adapter 模式的应用**——把'我们的模型'和'平台的模型'做隔离层，平台 API 变了只改 Strategy 的转换代码，业务代码零感知。"

---

### Q5 · 为什么新增渠道是 3 步而不是 1 步？

**标准答案**：
1. `ChannelEnum` 加枚举值 ── 渠道身份注册
2. `channel/{name}/` 下创建 Strategy + AuthHelper ── 业务实现
3. `application.yml` 加配置 + 数据库插类目映射 ── 凭证 & 业务数据

**面试官追问**："这 3 步能不能压成 1 步？"

**加分点**：
> "理论上可以做到——比如：
> - 把 `ChannelEnum` 改成数据库表 + 动态加载
> - 把 Strategy 改成 DSL（YAML 描述字段映射 + 签名算法），框架解析 DSL 自动生成行为
> 但这样**调试成本急剧上升**（你要 debug 一个 YAML 引擎而不是 Java 代码）。对 4-10 个渠道规模的项目，3 步是最佳平衡点；如果是 100+ 渠道的开放平台（比如 Shopify Connector），就值得做 DSL 化。"

---

### Q6 · 状态机为什么这样设计？

**标准答案**：

```
DRAFT → WAIT_PLATFORM_AUDIT → ON_SHELF | AUDIT_REJECT | OFF_SHELF
ON_SHELF ⇄ OFF_SHELF
AUDIT_REJECT → WAIT_PLATFORM_AUDIT | OFF_SHELF
```

实现：
```java
public class ProductStatusMachine {
    private static final EnumMap<ProductStatusEnum, Set<ProductStatusEnum>> TRANSITIONS;
    static {
        TRANSITIONS = new EnumMap<>(ProductStatusEnum.class);
        TRANSITIONS.put(DRAFT, EnumSet.of(WAIT_PLATFORM_AUDIT));
        TRANSITIONS.put(WAIT_PLATFORM_AUDIT, EnumSet.of(ON_SHELF, AUDIT_REJECT, OFF_SHELF));
        ...
    }

    public static void check(ProductStatusEnum from, ProductStatusEnum to) {
        if (from == to) return;  // 同状态幂等
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(...)).contains(to)) {
            throw new IllegalStateException(...);
        }
    }
}
```

**加分点**：
- **同状态转移允许**——回调可能重投，幂等很重要
- 用 `EnumMap + EnumSet` 而不是 `HashMap`，内部是位向量（bitset），常数级查询
- **拒绝引入 Spring Statemachine**——它对这种简单场景过度设计，序列化、持久化、事件总线全是负担
- 状态机校验在 Service 层（业务编排），不在 Strategy 层（Strategy 不知道当前 status，只接受调用指令）

---

### Q7 · 为什么微信回调要单独一个 Controller？

**标准答案**：
- 抖音/小红书回调结构简单：`POST /webhook/{channel}` + JSON body，直接落库分发
- 微信回调多了一层：
  - URL 带 `msg_signature`、`timestamp`、`nonce` query 参数
  - body 是 XML 包了一层 `<Encrypt>base64(AES ciphertext)</Encrypt>`
  - 必须先 sha1 验签 → AES-256-CBC 解密 → 校验 appId
- 如果硬塞进通用 WebhookController，要么参数列表臃肿，要么用 if-else 判断渠道分支——破坏了"通用回调入口"的语义

**加分点**：
> "**就像 OAuth 回调和 Webhook 通用回调不会用同一个 endpoint** 一样——协议复杂度不同，路由层就要分。这是 Single Responsibility 在 Controller 层的体现。"

---

## 二、并发与缓存（必问 4-6 题）

### Q8 · Token 单飞机制是怎么实现的？

**标准答案**（白板可画）：

```java
public class CaffeineTokenManager {
    private final Cache<ChannelEnum, TokenInfo> cache = Caffeine.newBuilder().maximumSize(16).build();
    private final Map<ChannelEnum, ReentrantLock> locks = new EnumMap<>(ChannelEnum.class);

    public String getToken(ChannelEnum channel) {
        TokenInfo cached = cache.getIfPresent(channel);
        if (cached != null && cached.isValid()) return cached.getToken();
        return doRefresh(channel, "miss-or-expired").getToken();
    }

    private TokenInfo doRefresh(ChannelEnum channel, String reason) {
        ReentrantLock lock = locks.get(channel);
        lock.lock();
        try {
            TokenInfo cached = cache.getIfPresent(channel);
            if (cached != null && cached.isValid()) return cached;  // double-check
            TokenInfo fresh = strategy.fetch();
            cache.put(channel, fresh);
            return fresh;
        } finally {
            lock.unlock();
        }
    }
}
```

**关键讲点**：
- **单飞**：100 个线程同时 miss，只有第 1 个真的发起 fetch；其他 99 个在 `lock.lock()` 阻塞
- **double-check**：99 个线程拿到锁后**再 check 一次缓存**，命中前 1 个线程刷好的值，直接返回，不重复 fetch
- **每渠道一把锁**：抖音 token 刷新不阻塞微信 token 刷新

**加分点**：
- "为什么不用 `synchronized(channel)`？channel 是 enum singleton，全局锁，4 渠道相互阻塞。"
- "为什么不用 `Caffeine.refreshAfterWrite`？它是后台异步刷，刷的同时还会返回旧值；我要的是'expired 后下次取必须拿新值'，语义不一样。"

---

### Q9 · 如果是多实例部署，这套单飞还有效吗？

**陷阱**：这题面试官就是想戳穿你"只懂单机"。**老实承认**比硬撑好。

**标准答案**：
> "**单进程内有效，跨进程无效**。多实例下 100 个请求被 LB 分到 4 个实例，每个实例独立 fetch，对平台来说还是 4 次 token 请求，不是 1 次。
>
> 多实例方案：**Redis 分布式锁 + Redis 缓存共享 token**。具体来说：
> - Token 存 Redis（key = `token:{channel}`，value = `{token, expiresAt}`），TTL 比 expiresAt 略小
> - getToken 先查 Redis，miss 则用 Redisson 的 RLock 加锁，再做 double-check + fetch + 回写 Redis
> - 锁 timeout 设置要小心：fetch 平台 token 可能 1-2s，锁 ttl 至少 5s 兜底；用 RLock 的 watchdog 自动续期更稳
>
> 当前没做是因为团队部署是单实例 + 备份实例（不分担流量），所以 Caffeine 够用。引 Redis 会增加运维成本，技术决策上是有意识的取舍。"

**加分点**：
- 主动提"watchdog 续期"细节，体现对 Redisson 有真实使用
- 主动给出"为什么没做"，体现技术决策有思考，不是不会

---

### Q10 · 定时预刷新是怎么做的？为什么不让 cache 自然过期？

**标准答案**：

```java
@Component
@EnableScheduling
public class TokenScheduler {
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void preRefresh() {
        for (ChannelEnum channel : registeredChannels) {
            TokenInfo info = manager.peek(channel);
            if (info != null && info.isAboutToExpire(Duration.ofMinutes(10))) {
                manager.refresh(channel);
            }
        }
    }
}
```

**为什么这样设计**：
- 自然过期 = 用户请求那一刻发现过期，**当次请求被阻塞等 fetch**（增加用户感知延迟）
- 预刷新 = 在 token 过期前 10 分钟主动刷一次，用户请求永远命中有效缓存
- 配合单飞机制：即使预刷新失败/延迟，自然过期分支还在兜底，不会因为 scheduler 挂了导致 token 失效服务挂

**加分点**：
> "**这其实是用户感知延迟 vs. 后台资源消耗 的取舍**。我们选了'宁愿后台多刷几次（每分钟扫一次，过期前 10min 内才真刷），换用户请求 100% 命中缓存'。
>
> 如果场景是低频调用（每天几次），这个 scheduler 就是浪费，不如让自然过期分支处理。**没有银弹，看 QPS。**"

---

### Q11 · ReentrantLock vs synchronized 这里为什么选 ReentrantLock？

**标准答案**：
- 需要"按 channel 分锁"，把锁实例放 `Map<ChannelEnum, ReentrantLock>` 里，必须用对象锁
- `synchronized(someObject)` 也能做，但 ReentrantLock 提供：
  - 可中断：`lock.lockInterruptibly()` 应对线程被 interrupt
  - 可超时：`lock.tryLock(timeout)` 防止永久阻塞（我们这没用，因为 fetch 自己有 timeout）
  - 公平/非公平选项

**加分点**：
> "**我这里其实非公平锁就够了**——单飞的目的是'只让 1 个 fetch'，谁先谁后无所谓。非公平锁吞吐更高（避免线程切换），是默认值。"

---

### Q12 · @Async 异步处理回调，线程池怎么配的？

**标准答案**：

```java
@Bean(name = "webhookExecutor")
public ThreadPoolTaskExecutor webhookExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("webhook-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
}
```

**关键讲点**：
- `CallerRunsPolicy`：队列满了让调用线程（Tomcat worker）自己跑，相当于自然限流，避免请求被丢
- 队列容量 200：估算 4 渠道平均 QPS × 5min 缓冲
- 命名前缀：日志/线程 dump 时能立刻看出"这是 webhook 异步线程"

**加分点**：
- "**Spring 默认的 SimpleAsyncTaskExecutor 不复用线程，来一个新建一个，不是真正的 async，是个大坑。**"
- "**回调失败重试我们目前是平台主动重投**（抖音 24h 内 5 次），我们这边 catch 异常 + 标 `processed=false` 入库就行。如果接 RocketMQ DLQ 会更稳，是 follow-up。"

---

### Q13 · 回调幂等怎么做的？

**标准答案**：

```java
public boolean record(ChannelEnum channel, String rawData) {
    String key = DigestUtils.md5Hex(channel.getCode() + rawData);
    try {
        CallbackLog log = new CallbackLog();
        log.setChannel(channel.getCode());
        log.setRawData(rawData);
        log.setIdempotencyKey(key);
        callbackLogMapper.insert(log);
        return true;  // 第一次见
    } catch (DuplicateKeyException e) {
        return false; // 重复，静默
    }
}
```

**关键讲点**：
- 数据库 `idempotency_key VARCHAR(64) UNIQUE` 索引
- 用 MySQL 的唯一键 + 应用层捕获 DuplicateKeyException，是最简单可靠的幂等方案
- 不需要分布式锁（数据库本身就是单点 source of truth）

**加分点**：
> "**为什么不用 Redis SETNX？** ① 多一个依赖；② Redis 挂了幂等失效；③ 数据库本来就要写一行 callback_log 留痕，唯一键顺便做了幂等，零额外成本。**Redis SETNX 适合纯校验、不需要落库的场景。**"

---

## 三、HTTP / 中间件（必问 3-5 题）

### Q14 · 为什么用 WebClient 不用 RestTemplate？

**标准答案**：
- `RestTemplate` 已经被官方标记 maintenance mode，不再加新特性
- `WebClient` 支持：
  - 响应式 / 同步两套 API（我们用 `.block()` 同步）
  - 优雅的 timeout 配置（`HttpClient.responseTimeout()` + `connectTimeout`）
  - 内置连接池、HTTP/2
- 升级路径友好：未来如果项目要变响应式，代码改动小

**陷阱**：
> "面试官追问'那为什么不全部响应式'。**老实答**：我们是 Spring MVC + Tomcat 同步栈，**整条链路只有这里响应式没意义**，反而增加心智负担。WebClient 我们当成 'better RestTemplate' 用。"

---

### Q15 · WebClient 怎么配 timeout 和连接池？

**标准答案**：

```java
@Bean
public WebClient.Builder webClientBuilder() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
        .responseTimeout(Duration.ofSeconds(10))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
            .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
}
```

**关键讲点**：
- **connect / read / write 三个 timeout 都要配**（很多人只配 connect）
- 把 Builder 配成 `@Bean`，全局复用，避免每次 new
- 各渠道 Strategy `@Autowired WebClient.Builder` → 自己 `.baseUrl()` 套一层，复用底层 connection pool

---

### Q16 · Resilience4j 限流怎么配的？为什么不用 Sentinel？

**标准答案**：

```yaml
resilience4j:
  ratelimiter:
    instances:
      douyin:
        limit-for-period: 60       # 60 次
        limit-refresh-period: 60s  # 每分钟
        timeout-duration: 0        # 不等，直接拒
      xiaohongshu:
        limit-for-period: 600
        limit-refresh-period: 60s
      wechat:
        limit-for-period: 50
        limit-refresh-period: 1s
```

```java
protected void acquirePermit() {
    if (rateLimiterRegistry == null) return;
    RateLimiter rl = rateLimiterRegistry.rateLimiter(getChannel().getCode());
    if (!rl.acquirePermission()) {
        throw new ChannelBusinessException(getChannel().getCode(), "限流");
    }
}
```

**为什么不用 Sentinel**：
- 项目规模小，Resilience4j 嵌入式 jar，零运维
- Sentinel 需要 dashboard，对中小项目过度
- Resilience4j 跟 Spring Boot 集成更原生

**加分点**：
- "**每渠道独立令牌桶**——抖音被限不影响微信"
- "`timeout-duration: 0` 表示拿不到令牌直接抛异常，**不要 wait**。wait 会让 Tomcat 线程堆积"

---

### Q17 · Spring Retry 怎么配的？什么场景下不能用？

**标准答案**：

```java
@Retryable(
    value = ChannelNetworkException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)  // 1s, 2s, 4s
)
public final PushResult pushProduct(...) { ... }
```

**关键讲点**：
- 只重试 `ChannelNetworkException`，业务异常（4xx）不重试
- 指数退避：1s → 2s → 4s，避免被对方限流后立刻撞墙
- 加 `@Recover` 兜底（我们没加，让异常抛出去给 GlobalExceptionHandler）

**不能用的场景**：
- **非幂等操作**：比如 POST 创建订单，重试会创建多个订单。要么加幂等键，要么不重试
- **@Retryable 必须代理才生效**：同类内方法相互调用 `this.foo()` 不会触发重试
- **不能 catch 后重试**：要让异常抛到注解层

**加分点**：
> "**我们这里 push 接口理论上不幂等**——平台没有显式幂等键。但实践中：① 网络异常下平台多半压根没收到请求；② 我们存了 `channel_product_mapping` 做反查，重复 push 同一 `outProductId` 会先查映射判断是不是已存在，已存在转 update。**所以是'应用层幂等' + '网络层重试'结合。**"

---

## 四、平台 / 安全（必问 3-4 题）

### Q18 · 4 个渠道的鉴权差异讲一下

**标准答案表**：

| 渠道 | 算法 | 用在哪 | Token TTL |
|------|------|-------|-----------|
| 抖音 | **HMAC-SHA256** | 每次请求 header 带 sign | access_token 7 天 |
| 小红书 | **MD5** | query 参数 sign | accessToken 7 天 |
| 微信小店 | **Access Token (query)** | query 带 access_token | 7200 秒 |
| 本地商城 | 无 | 直接调内部接口 | / |

**重点讲微信**：
- 普通 API：access_token 7200 秒
- **回调验签**：sha1(sort([token, timestamp, nonce, encrypt]).join(""))
- **回调解密**：AES-256-CBC + PKCS7 padding，明文格式 `[16 字节随机][4 字节长度][msg][appId]`，**末尾 appId 必须匹配**防止跨应用攻击

---

### Q19 · 微信回调解密细节讲一下

**标准答案**：

```java
public static String decrypt(String base64Cipher, String encodingAesKey, String appId) {
    byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");  // 补成 32 字节
    byte[] cipher = Base64.getDecoder().decode(base64Cipher);
    SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
    IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);  // ⚠️ IV 是 AES key 前 16 字节
    Cipher c = Cipher.getInstance("AES/CBC/NoPadding");        // ⚠️ NoPadding，手动去 PKCS7
    c.init(Cipher.DECRYPT_MODE, keySpec, iv);
    byte[] plain = c.doFinal(cipher);

    plain = pkcs7Unpad(plain);  // 手动去 padding
    // 布局：[16 字节随机][4 字节大端长度][msg 字节][appId 字节]
    int msgLen = ByteBuffer.wrap(plain, 16, 4).getInt();
    String msg = new String(plain, 20, msgLen, StandardCharsets.UTF_8);
    String fromAppId = new String(plain, 20 + msgLen, plain.length - 20 - msgLen, StandardCharsets.UTF_8);
    if (!appId.equals(fromAppId)) throw new ChannelAuthException(...);
    return msg;
}
```

**面试官追问重点**：

| 问 | 答 |
|----|----|
| 为什么 `NoPadding` 不用 `PKCS7Padding`？ | Java 标准库不支持 PKCS7，只支持 PKCS5。但 AES block size = 16 时 PKCS5 == PKCS7。微信文档明说 PKCS7，**为了精确语义**我们用 NoPadding 自己去 padding |
| 为什么 IV 是 AES key 的前 16 字节？ | 这是微信协议规定的，**不是密码学最佳实践**（最佳是每次随机 IV 并随密文传输）。我们必须按平台协议来 |
| 为什么要校验 appId？ | 防中间人重放：攻击者拿到另一个应用的合法回调密文，如果不校验 appId 就能注入到你的系统 |

---

### Q20 · 微信回调验签是怎么算的？

**标准答案**：

```java
public static boolean verify(String token, String timestamp, String nonce, String encrypt, String msgSignature) {
    List<String> list = Arrays.asList(token, timestamp, nonce, encrypt);
    Collections.sort(list);            // ⚠️ 字典序排
    String joined = String.join("", list);
    String sha1 = DigestUtils.sha1Hex(joined);
    return sha1.equalsIgnoreCase(msgSignature);
}
```

**关键讲点**：
- 4 个字段**字典序排**（不是按固定顺序）
- 字符串拼接**不加分隔符**
- sha1 hex（**不是 base64**）

---

### Q21 · 如果让你接第 5 个平台 Shopee 你怎么做？

**标准答案**：
1. 查 Shopee Open Platform 文档，搞清楚：鉴权（HMAC-SHA256，timestamp 防重放）、商品上架接口、状态回调
2. `ChannelEnum.SHOPEE("shopee", ...)`
3. `channel/shopee/` 下创建：
   - `ShopeePlatformStrategy` extends AbstractPlatformStrategy
   - `ShopeeAuthHelper` —— HMAC-SHA256 + partner_id + path 拼接
   - `ShopeeRefreshStrategy` —— access_token 4h refresh
4. `application-prod.yml` 加配置；数据库 `category_mapping` 插 Shopee 类目对照
5. 写 `ShopeeAuthHelperTest`（pin 一个签名 baseline）+ `ShopeePlatformStrategyTest`（mapStatus）
6. dev profile 起 H2，手动 mock 测一遍 push 链路；prod 申请沙箱凭证联调

**预估工时**：核心 2 天（写代码 + 写单测），联调 1 天，总共 ~3 天。

---

## 五、项目难点 / 行为问题（必问 3-5 题）

### Q22 · 这个项目你最大的难点是什么？

**推荐答案**：
> "**Token 的并发管理**。看起来简单——'失效了就刷一次'——但生产环境下面有 3 个陷阱：
>
> ① **惊群**：访问高峰时 100 个请求同时发现 token 过期，全部去刷，平台一定限流甚至封号
> ② **强制刷新被缓存吞掉**：我自己就踩过这坑（讲 Bug #1 的故事）
> ③ **过期边界**：token 在 fetch 那一刻已经接近过期，等 5 秒后用就过期了
>
> 解决方案：单飞机制 + double-check + 提前 10 分钟预刷新。单测里专门写了 100 线程并发跑 1 万次 getToken，断言 fetch 只被调 1 次。"

---

### Q23 · 这个项目你最自豪的设计是什么？

**推荐答案**（选一个就好）：
> "**异常分类 + 重试粒度的设计**。
>
> 一开始 `@Retryable` 是裸的 `value = Exception.class`，结果业务异常（类目不存在）也重试 3 次，平台返回都是同一个 422，**白白浪费用户 3 秒钟**。
>
> 改造后：
> - `ChannelException` 拆 3 子类（Network / Business / Auth）
> - 模板方法里 `classify()` 统一把 WebClient 抛的异常按 HTTP code 归类
> - `@Retryable(value = ChannelNetworkException.class)` 只重试网络
> - `GlobalExceptionHandler` 按子类映射 HTTP 401/422/503
>
> 这一改造让重试有了'语义'——不再是机械的'失败就重试'，而是'瞬时网络问题才重试'。同时也让业务方拿到的错误码更准确，能直接根据 401/422 区分处理。"

---

### Q24 · 你做过哪些性能优化？

**坦诚答案**：
> "**老实说这个项目还没真上线压测**，我做的更多是'防止性能问题'而不是'优化性能问题'：
>
> 1. **token 缓存 + 单飞**，避免高并发下 fetch 风暴
> 2. **限流前置**，避免被平台拉黑（被拉黑后恢复成本远高于限流自己）
> 3. **WebClient 连接池 + timeout**，避免连接耗尽 / 长尾请求堆积
> 4. **EnumMap 取代 HashMap**（注册表 / 状态机 / 锁表），节省一点 hashing 开销
> 5. **回调异步处理**，HTTP 响应不阻塞业务逻辑
>
> **要做的优化**：① batch push（攒一波商品一次推，省 token 校验开销）；② Caffeine 改 Redis 多实例共享；③ 加 Micrometer metrics 看哪个渠道延迟最差。"

---

### Q25 · 这个项目还有什么遗憾？

**推荐答案**（展示反思能力）：
> "三个遗憾：
> 1. **没接 MQ**：现在异步只用 `@Async` 线程池，进程挂了内存里的任务就丢了。接 RocketMQ 之后能做'回调失败 → DLQ → 后台补偿'，更稳
> 2. **没 Observability**：没接 Micrometer / SkyWalking。线上 token 刷新失败、限流被拒次数都没法量化，只能靠日志 grep
> 3. **没真接平台**：沙箱凭证一直没拿到，验签和签名都只测了'算法正确性'，没测'平台真的接受'。这部分准备拿到凭证后补回归测试"

---

### Q26 · 团队是怎么协作的？你的角色？

**模板答案**（根据真实情况调整）：
> "这是我**主导设计 + 开发**的项目。需求是产品侧提的（要把同一商品同步到 4 渠道），技术方案我出，跟 leader 评审过框架抽象的边界。
>
> 我负责：
> - 整体架构（策略 + 工厂 + 模板方法）
> - 4 个渠道的 Strategy 实现
> - 核心基础设施（TokenManager / 状态机 / 异常体系 / 全局异常处理）
> - 单元测试
>
> Code review 走 GitLab MR，至少 1 个同事 approve 才能 merge。"

---

### Q27 · 这个项目对你最大的成长是什么？

**推荐答案**：
> "**'对外集成'类项目的设计套路**——你永远在跟一个'你不能控制的外部系统'打交道，所以：
> - **写好抽象**比'写得快'重要：因为你早晚要接第 5、第 6 个平台
> - **超时、重试、限流、幂等**是 4 件套，不能漏
> - **签名/加密**必须有回归基线测试，否则升级依赖时算错都不知道
> - **遇到异常优先归类再处理**，'裸 catch Exception' 是写代码的反模式
>
> 这套思路后来在我做 XXX 项目时也用上了。"

---

## 六、Bonus：可能会被问的"刁难题"

### Q28 · 策略模式不是反模式吗？只有 4 个渠道，用 if-else 不更简单？

**应对**：
> "4 个渠道用 if-else 确实简单。但**问题不是渠道数量，是单渠道的复杂度**——抖音的鉴权 + 签名 + 字段映射 + 回调解析加起来 300+ 行代码，4 个渠道糊一起 = 1200 行单文件。维护成本远高于 4 个独立类。
>
> 再退一步说，**策略模式真正的价值不是'切换策略'，是'隔离变化的方向'**——平台 API 改动只影响一个 Strategy 文件，不会动到其他渠道。这才是它在我们项目里的核心收益。"

---

### Q29 · 你用了 Spring Retry，但 Spring Retry 是单机的，多实例下重试会重复怎么办？

**应对**：
> "好问题。Spring Retry 的重试是'同一个 JVM 内'的重试，跟多实例无关。多实例下不会重复——某个请求只会落到某个实例，那个实例自己重试 3 次。
>
> **但**你的问题可能想问的是'幂等'——重试可能让 push 接口被调用多次。这一层我们的做法：
> - 平台层面如果支持幂等键（比如 idempotency-key header），我们传 outProductId
> - 平台不支持的话，靠 `channel_product_mapping` 反查：'已经映射了' → 转 update 而不是 create
>
> 重试本身保证 in-process 单次，幂等通过应用层兜底，两层防御。"

---

### Q30 · 你说"不重试业务异常"，但 token 过期不就是业务异常吗？

**应对**：
> "Token 过期我归到 `ChannelAuthException` 不是 `ChannelBusinessException`。Auth 异常**不重试**，但是我们另有一套机制：**调用方拿到 401 → 主动调 `tokenManager.refresh(channel)` → 下次调用就拿到新 token**。
>
> 不直接在重试里塞 'token 失效自动刷新' 是因为这会让重试逻辑跟具体平台耦合（每个平台 401 含义可能不同）。**用'调用方感知 + 主动刷'更解耦**。
>
> 实际上更优雅的做法是写一个 `WebClient.filter`，全局拦截 401 → 刷 token → retry 一次。这是 follow-up，没来得及做。"

---

### Q31 · 你的 GlobalExceptionHandler 把 IllegalStateException 映射成 409，但 IllegalStateException 是 Java 标准异常，是不是太宽泛了？

**应对**：
> "**很对，这是个 trade-off**。我选 IllegalStateException 是因为'状态机非法转移'语义上就是它，而且 Java 自带、零依赖。
>
> **风险**：如果项目其他地方有人 throw 一个 IllegalStateException 表达别的意思（比如 NullPointerException 的同类问题），也会被映射成 409。
>
> **更严格的做法**：自定义 `IllegalStateTransitionException extends ChannelException`，专门用于状态机。这是个 5 分钟改动，我可以放进下次迭代。"

---

## 📋 面试前一晚 checklist

- [ ] 能在白板画出整体架构图（第 01 文档第 3 节）
- [ ] 能讲清 3 条核心链路（push / changeStatus / webhook）
- [ ] **能徒手写出 CaffeineTokenManager 的 doRefresh 核心逻辑（含 double-check）**
- [ ] **能徒手写出微信 sha1 验签和 AES 解密的关键步骤**
- [ ] 准备 2 个 bug 故事（推荐 #1 单飞 + #2 测试死锁）
- [ ] 准备 1 个"最自豪设计"故事（推荐异常分类）
- [ ] 准备 1 个"最大遗憾"答案（接 MQ / Observability / 真联调）
- [ ] 准备 1 个"接下一个渠道"工作流（Shopee 例子）
- [ ] 准备 1 个"多实例下怎么做"答案（Redis 分布式锁）
