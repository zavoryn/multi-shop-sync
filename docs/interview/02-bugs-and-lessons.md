# 02 · 踩坑录：9 个真实 bug + 修复 + 收获

> 面试官最爱问的题：**"你在这个项目里遇到过最难调的 bug 是什么？"** —— 不要答"没遇到过"，也不要答"小事情记不清了"。挑 2-3 个有技术深度的讲。

每个 bug 按 STAR 结构组织：**S**ymptom 现象 → **R**oot Cause 根因 → **F**ix 修复 → **L**esson 收获。

---

## 🔥 Bug #1 ── CaffeineTokenManager.refresh() 强制刷新失效（推荐主讲）

> **这是单测抓出来的真 bug，最适合讲。**

### Symptom

设计上 `refresh(channel)` 是给"调用方明确知道 token 失效"用的强制刷新接口（比如平台返回 401 后主动刷一次）。但单测里写了一个用例：
1. 先 `getToken(DOUYIN)` 让 cache 缓存 token A
2. 再调 `refresh(DOUYIN)` 期望拿到新 token B
3. 断言返回值是 B

**结果**：返回的是 token A，单测红。

### Root Cause

最初的 `refresh()` 实现：

```java
public String refresh(ChannelEnum channel) {
    if (channel == ChannelEnum.LOCAL) return "";
    return doRefresh(channel, "forced").getToken();
}
```

而 `doRefresh` 内部有 double-check（这是单飞机制的关键）：

```java
private TokenInfo doRefresh(...) {
    lock.lock();
    try {
        TokenInfo cached = cache.getIfPresent(channel);
        if (cached != null && cached.isValid()) {
            return cached;  // ← 这里命中了上次 getToken 缓存的 token A
        }
        // 真正的 fetch 永远走不到
        ...
    }
}
```

**Double-check 是给"多线程同时 miss → 抢锁 → 后到的复用前一个 fetch 结果"用的，但被 `refresh()` 误用了，导致强制刷新永远命中缓存。**

### Fix

`refresh()` 必须先 invalidate：

```java
public String refresh(ChannelEnum channel) {
    if (channel == ChannelEnum.LOCAL) return "";
    cache.invalidate(channel);  // ← 关键，先清缓存
    return doRefresh(channel, "forced").getToken();
}
```

### Lesson（面试加分点）

> "**这个 bug 在 Phase 4 端到端测试时没暴露，因为当时 cache 一直是空的（没人提前 getToken），doRefresh 自然走到 fetch 那一行。等到 Phase 8 写单测特意复现 `getToken → refresh` 顺序时才被抓出来。**
>
> **收获是：① 单测的价值不是覆盖率，是给你逼出来这种"语义陷阱"；② 共享同一个底层函数的多个 public API，要小心它们对预置条件（precondition）的要求不一样。**"

---

## 🔥 Bug #2 ── 100 线程并发单飞测试本身写出了死锁

### Symptom

写 `CaffeineTokenManager` 的单飞机制测试，预期 100 个线程同时 `getToken`，统计 `RefreshStrategy.fetch()` 被调用了几次。原代码：

```java
ExecutorService pool = Executors.newFixedThreadPool(16);
CountDownLatch fire = new CountDownLatch(1);
CountDownLatch ready = new CountDownLatch(100);

for (int i = 0; i < 100; i++) {
    pool.submit(() -> {
        ready.countDown();
        fire.await();
        manager.getToken(DOUYIN);
    });
}

ready.await(100, SECONDS);  // ← 永远等不到
fire.countDown();
```

**结果**：单测挂住，超时失败。

### Root Cause

`newFixedThreadPool(16)` 只有 16 个 worker。前 16 个 task 调 `ready.countDown` 后立刻 `fire.await()` 阻塞。剩下 84 个 task 进队列等 worker，**worker 永远不释放**，导致 `ready` 从未数到 100。

主线程在 `ready.await(100, SECONDS)` 上等了 100 秒后超时。

### Fix

```java
ExecutorService pool = Executors.newCachedThreadPool();  // ← 按需起新线程
```

### Lesson

> "**测并发的工具自身也是并发代码，容易踩到 thread starvation。`newFixedThreadPool(N)` 的隐含含义是'我承诺最多用 N 个 worker'，如果 task 之间有协作/阻塞依赖，必须保证 worker 数 ≥ 所有可能同时阻塞的 task 数，否则就是 deadlock。**
>
> **`newCachedThreadPool` 不限制 worker 数，对'临时短任务并发压测'更合适。生产代码不能这么用（会爆线程数），但单测里没问题。**"

---

## 🐛 Bug #3 ── WebhookController 启动报 Ambiguous mapping

### Symptom

`mvn spring-boot:run` 报错：

```
Ambiguous mapping. Cannot map 'webhookController' method
  public ResponseEntity ...handleWechat(...)
to {POST /api/webhook/wechat}: There is already 'webhookController' bean method ...
```

### Root Cause

`WebhookController` 同时定义了 2 个方法 mapping 到 `/wechat`：

```java
@PostMapping("/wechat")
public ResponseEntity<?> handleWechat(@RequestBody String raw) { ... }

@PostMapping("/wechat")  // ← 重复
public ResponseEntity<?> handleWechatNew(@RequestParam ... @RequestBody ...) { ... }
```

是开发过程中一边按原始结构写、一边按微信加密协议写（带 `msg_signature` 等参数），忘了删旧的。

### Fix

把微信回调拆到独立的 `WechatCallbackController`，路由用精确 path `@RequestMapping("/api/webhook/wechat")`，主 WebhookController 只留抖音/小红书。

### Lesson

> "**Spring 路由冲突在编译期不会报，启动时才报，这种'启动失败'但'本地能编译过'的问题在 CI 里也不一定能拦住——除非有 smoke test 真的启动一次 context。**
>
> **后来在 ProductSyncControllerTest 里加了 `@SpringBootTest` 起 context 的烟雾测试，正好能拦下这种 mapping 冲突。**"

---

## 🐛 Bug #4 ── FastJSON2 依赖一直 resolve 不到

### Symptom

`mvn compile`：

```
Could not find artifact com.alibaba:fastjson2:jar:2.0.43 in central
```

### Root Cause

习惯性写了 `<groupId>com.alibaba</groupId>`（FastJSON 1.x 的 GA），但 FastJSON2 的 groupId 是 `com.alibaba.fastjson2`。

### Fix

```xml
<dependency>
  <groupId>com.alibaba.fastjson2</groupId>  <!-- 改这里 -->
  <artifactId>fastjson2</artifactId>
  <version>2.0.43</version>
</dependency>
```

### Lesson

> "**升大版本时 groupId 可能也变（FastJSON 1→2、ZooKeeper apache→org.apache 等），别凭印象写。**"

---

## 🐛 Bug #5 ── GlobalExceptionHandler 把 IllegalStateException 错映射成 400

### Symptom

状态机非法转移本应返回 409 Conflict，实际返回 400 Bad Request。

### Root Cause

`AbstractPlatformStrategy` 模板里：

```java
try {
    return doPushProduct(...);
} catch (Exception e) {
    throw classify(e);   // ← 把 IllegalStateException 也吞了
}
```

`classify` 兜底走 `return new ChannelException(...)`，被 GlobalExceptionHandler 映射为 400。

### Fix

```java
try {
    return doPushProduct(...);
} catch (ChannelException | IllegalStateException e) {
    throw e;                       // ← 已分类，原样抛
} catch (Exception e) {
    throw classify(e);             // ← 未分类，归类
}
```

### Lesson

> "**`catch (Exception)` 是个危险信号，它会把一切都吃掉。模板方法里更危险——子类抛的任何业务异常都被'打扮成网络异常'。一条原则：'我不认识的异常才 wrap'。**"

---

## 🐛 Bug #6 ── 后台跑 mvn test 重定向后日志为空

### Symptom

```bash
mvn test | tail -40 > /tmp/log &  # 想后台跑、tail 看最后几行
```

后台 process 停掉后查看 `/tmp/log`，**完全是空的**。

### Root Cause

shell pipe 中，`tail` 在收到 EOF 前会 buffer。`mvn` 没结束 → `tail` 没收到 EOF → `tail` 不输出 → `/tmp/log` 一直为空。手动 `kill` 掉 `mvn` 时，`tail` 收到管道关闭信号，但来不及 flush 就跟着退出了。

### Fix

```bash
mvn test > /tmp/log 2>&1 &  # 直接重定向，绕开 tail
```

### Lesson

> "**Unix pipe 默认是 block buffer（4KB），不是 line buffer。用 `stdbuf -oL <cmd> | ...` 或者直接重定向才能拿到实时输出。这个坑在调试长任务时很容易碰到。**"

---

## 🐛 Bug #7 ── 中文 payload 经 curl 发出去服务器报 Invalid UTF-8

### Symptom

测试 push 接口的 curl 命令带了 `"title": "测试商品"`，服务器返回 400 + "Invalid UTF-8 character"。

### Root Cause

Windows 上 Git Bash 的 stdin 默认 GBK 编码，curl 把 heredoc 里的中文按 GBK 编码后发了出去，server 端 Spring 按 `Content-Type: application/json; charset=utf-8` 解码报错。

### Fix（多个备选）

- 把测试 payload 改成纯 ASCII（debug 用，糙快）
- curl 加 `-H "Content-Type: application/json; charset=GBK"`（治标）
- 真正修：把 heredoc 内容写文件，`curl --data-binary @file.json` 直接二进制读

### Lesson

> "**Windows 测试中文 API 必须意识到 'shell 编码' 和 'HTTP charset' 是两件事。生产代码倒没影响，因为 Spring + UTF-8 是正确的，只是本地 curl 不能用 heredoc 测。**"

---

## 🐛 Bug #8 ── 小红书签名基线测试一直红

### Symptom

写 `XiaohongshuAuthHelperTest.calcSignBaselineRegression` 时凭直觉填了一个期望值 `d54f1d4cb95dccf9b3a5bb98ce21027e`，单测直接红。

### Root Cause

我自己心算/在线 MD5 计算器搞错了字段拼接顺序。实际算法是：

```
sign = MD5(method + "?" + sortAsc("appId=ak", "timestamp=1", "version=2.0").join("&") + appSecret)
     = MD5("m?appId=ak&timestamp=1&version=2.0sk")
     = "8e07b82dc6a9153a58162421aecc5ede"
```

我错把 secret 拼在中间了。

### Fix

让代码先跑一次拿到实际值，pin 进单测。

### Lesson

> "**回归基线（regression baseline）的目的不是'校验值是否正确'，而是'校验算法是否变化'。pin 实际值进去后，如果代码逻辑改动导致 sign 变了，单测会红——这是它的价值。**
>
> **第一次 pin 时'值是否正确'是另一个问题，要靠对照官方文档 demo 数据来保证。**"

---

## 🐛 Bug #9 ── 测试方法名带中文带空格编译失败

### Symptom

```java
@Test
void calcSign_MD5_32 位_hex() { ... }  // ← 空格
```

`mvn compile` 报 `'(' expected` 之类乱七八糟的错。

### Root Cause

Java 标识符不允许空格。中文字符其实可以（Unicode letter），但带空格就废了。

### Fix

全部改成英文：`calcSignMd5Hex32Chars`。

### Lesson

> "**测试方法名建议全英文。一是兼容性问题（CI/IDE/报告工具对中文支持不一致），二是 stacktrace 看起来 cleaner。可读性靠 JavaDoc 或者 `@DisplayName("MD5 应输出 32 位 hex")` 补。**"

---

## 📊 Bug 总结表（面试时可以列出来给面试官）

| # | 类别 | 抓出渠道 | 是否修了根因 |
|---|------|---------|------|
| 1 | 并发 / 缓存语义 | 单元测试 | ✅（API 语义对齐） |
| 2 | 测试基础设施 | 单测超时 | ✅（换线程池实现） |
| 3 | Spring 路由 | 启动失败 | ✅（拆 Controller） |
| 4 | 依赖管理 | 编译失败 | ✅（改 groupId） |
| 5 | 异常处理 | 端到端测试 | ✅（catch 优先级） |
| 6 | Shell IO | 调试观察 | ✅（绕过 pipe） |
| 7 | 编码 | curl 测试 | ⚠️（仅 dev 影响） |
| 8 | 测试设计 | 单测红 | ✅（理解 baseline 语义） |
| 9 | 语言基础 | 编译失败 | ✅（命名规范） |

---

## 🎯 面试主讲 bug 推荐顺序

| 优先级 | Bug | 理由 |
|--------|-----|------|
| ⭐⭐⭐ | #1 单飞 cache 失效 | 体现"读自己的代码、理解并发原语、用单测倒逼接口语义" |
| ⭐⭐⭐ | #2 测试代码死锁 | 体现"懂 Executor、懂 Latch、能 debug 自己的测试" |
| ⭐⭐ | #5 异常分类 catch 优先级 | 体现"懂模板方法的副作用 + 异常处理设计" |
| ⭐ | #3 路由冲突 | 简单，但能引出"smoke test 的价值"话题 |

剩下的 5 个属于"开发卫生"类，太琐碎，不要主讲。
