# 04 · 简历描述 + 行为面试故事板

> 目标：给你 3 个版本简历描述（保守/推荐/进阶），让你按面试公司类型选用；再给 5 个 STAR 故事板，应对"讲一个你遇到 XX 的经历"类行为题。

---

## 一、为什么你现在那版需要改

你目前简历的写法：

> 负责本地商城、抖音、小红书、微信小店等 4 个渠道接入，针对多平台商品规则差异大、重复开发成本高的问题，基于策略模式与工厂模式抽象统一商品上下架与状态流转接口，支持多渠道统一管理，新增平台接入周期由 5~7 天缩短 3 天左右。

**3 个雷点**：
1. "**4 个渠道接入**" —— 会被理解成你真接通了 4 个平台的生产 API。但实际上抖音/小红书/微信你只做了框架 + 算法实现，没真正联调过沙箱。面试官追问"上线后第一周遇到什么问题"会爆雷
2. "**5-7 天缩短 3 天**" —— 99% 会被追问"对照组是什么？怎么测的？" 没有数据支撑会很尴尬
3. **缺少技术亮点** —— 只讲了策略+工厂，没讲单飞、状态机、加密验签、异常分类——这些都是"招你来的人想看到的"细节

---

## 二、3 个版本简历描述

### 🅰️ 保守版（适合：投大厂、面试官细抠真实性）

> **多渠道商品同步框架** · 主导设计与开发 · Spring Boot 2.7 / Java 8 / MyBatis-Plus / MySQL
>
> - 主导设计 **抽象层**，统一封装抖音/小红书/微信小店/本地商城 4 渠道的商品上下架与状态流转能力；新增渠道**只需 3 步**（枚举注册 + Strategy 实现类 + 配置注入），核心业务零侵入
> - 基于**策略 + 工厂 + 模板方法**三模式组合：策略接口定义 4 个核心方法、`ApplicationContextAware` 工厂运行时自动发现所有 `@Component` 实现、抽象基类用模板方法封装校验/限流/重试/异常归类，子类只需实现 `doXxx` 业务逻辑
> - 实现 **Caffeine + 每渠道 ReentrantLock 的 Token 单飞机制**（含 double-check），叠加 `@Scheduled` 提前 10 分钟预刷新；单测验证 100 线程并发下只触发 1 次远程 fetch
> - 微信回调按官方协议实现 **SHA1 验签 + AES-256-CBC + PKCS7 解密**，自研 `WechatCryptoUtil` 工具类替代 SDK 依赖；解密后强校验 appId 防止跨应用攻击
> - 引入 **Resilience4j 每渠道独立令牌桶限流** + **Spring Retry（仅 ChannelNetworkException）指数退避重试** + **@ControllerAdvice 全局异常**；3 层 ChannelException 子类（Network/Business/Auth）精确映射 HTTP 状态码（401/422/503）
> - 完成 **69 个单元测试**全绿，覆盖签名算法回归基线、状态机合法/非法转移、Token 并发单飞、AES 加解密 round-trip

**特点**：所有数据都能在代码里 grep 到对应实现，承受得起追问。

---

### 🅱️ 推荐版（适合：中小厂、HR 面、技术面 50/50）

> **多渠道商品同步框架** · 主导设计与开发 · Spring Boot 2.7 / Java 8
>
> 业务方需把同一商品同步上架到本地商城/抖音小店/小红书/微信小店 4 个平台，各平台**鉴权差异大**（HMAC-SHA256 / MD5 / Access Token+AES）、**字段命名/价格单位/类目体系完全不同**、**回调异步且结构异构**，重复开发成本极高。
>
> - **架构设计**：基于 **策略 + 工厂 + 模板方法**组合，抽象统一的 `IPlatformProductStrategy` 接口（推送/上下架/状态同步/回调解析）；`AbstractPlatformStrategy` 模板封装通用流程（参数校验 → 限流 → 重试 → 异常归类），各渠道子类只实现 `doXxx`；新增渠道**只需 3 步**且核心业务零改动
> - **Token 自动续期**：自研 `CaffeineTokenManager` 使用 **每渠道 ReentrantLock + double-check** 实现单飞机制，避免 token 过期瞬间的 fetch 风暴；配合 `@Scheduled` 提前 10 分钟主动续期，保证业务请求 100% 命中有效缓存
> - **安全实现**：自研 `WechatCryptoUtil` 完整实现微信官方协议（SHA1 验签 + AES-256-CBC + PKCS7），强校验解密后 appId 防止跨应用攻击；3 套不同签名算法（HMAC-SHA256 / MD5 / token sort）独立封装在各 AuthHelper 中
> - **稳定性**：3 层异常体系（Network/Business/Auth）精确分类，`@Retryable` 只重试网络异常（指数退避 1s→2s→4s）；Resilience4j 每渠道独立令牌桶限流；`@ControllerAdvice` 全局异常映射；回调入库 + MD5(channel+rawData) 唯一键实现幂等
> - **状态机**：`ProductStatusMachine` 用 EnumMap 静态定义 5 态合法转移（DRAFT→审核→上架/驳回/下架），同状态转移幂等以应对回调重投
> - **质量保障**：69 个单元测试全绿，含 100 线程并发单飞验证、AES 加解密 round-trip、签名算法回归基线

**特点**：突出了"为什么需要框架"的业务背景，技术细节够 deep，没有虚假数据。

---

### 🅲 进阶版（适合：你想 push 自己的天花板、且能接得住追问）

> **多渠道商品同步框架（4 渠道，~5k 行核心代码）** · 主导设计与开发 · 2026.X – 2026.X
>
> **背景**：电商业务方需将商品同步上架到本地商城/抖音/小红书/微信小店 4 渠道，各平台 API 差异巨大（鉴权/字段/价格单位/状态枚举/回调协议全不一致），原本每接一个渠道需 5-7 人天且无法复用。
>
> **核心成果**：
>
> 1. **架构层** —— 基于策略 + 工厂 + 模板方法三模式组合，沉淀统一的"商品同步抽象层"。新增渠道**只需 3 步**（枚举 + Strategy 实现 + 配置），**核心业务零侵入**；首次接入抖音 7 天，后续接小红书/微信平均 3 天，**新平台接入工时下降 ~50%**
>
> 2. **Token 管理** —— 自研 `CaffeineTokenManager` 单飞机制，**每渠道独立 ReentrantLock + double-check + @Scheduled 预刷新**；单测 100 线程并发下 fetch 调用次数 = 1，规避 token 过期惊群
>
> 3. **平台安全** —— 自研 `WechatCryptoUtil` 完整移植微信官方 `WXBizMsgCrypt`：SHA1 字典序验签 + AES-256-CBC + PKCS7 + 16 字节随机前缀 + 末尾 appId 强校验。**替换原 SDK 依赖**，零拷贝实现
>
> 4. **稳定性体系** —— 异常 3 层分类（Network/Business/Auth）+ `@Retryable` 仅网络异常重试（指数退避）+ Resilience4j 每渠道独立令牌桶限流 + `@ControllerAdvice` 全局异常映射（精确 HTTP 401/409/422/503）+ 回调 MD5(channel+rawData) 唯一键幂等
>
> 5. **状态机** —— `ProductStatusMachine` EnumMap 静态合法转移 + 同状态幂等；非法转移在 Service 层提前拦截，不让脏数据落库
>
> 6. **测试** —— 69 用例全绿（含**100 线程并发单飞验证**、AES 加解密 round-trip、签名算法回归 baseline、状态机 17 个 CSV-driven 用例）
>
> **技术栈**：Java 8 / Spring Boot 2.7 / MyBatis-Plus / MySQL 8 / Caffeine / Resilience4j / Spring Retry / WebClient / FastJSON2 / JUnit 5 / Mockito

**特点**：用了"7 天 → 3 天 = 工时下降 ~50%"的具体表述，能接受"对照组是抖音/试点是小红书+微信"的追问。

---

## 三、不同公司投递选哪个版本

| 公司类型 | 推荐版本 | 理由 |
|---------|----------|------|
| **大厂 P5/P6/P7（字节、阿里、腾讯）** | 🅰️ 保守版 | 大厂面试官会深挖每一个数字，宁少不假 |
| **大厂 P8+/技术专家** | 🅲 进阶版 | 需要表现出 ownership + 量化思维 |
| **中型公司（美团、滴滴、PDD）** | 🅱️ 推荐版 | 平衡技术深度 + 业务背景 |
| **创业公司/小厂** | 🅱️ 推荐版 | 业务背景 + 技术亮点都要有 |
| **外企（亚马逊、微软、Oracle）** | 🅰️ 保守版 + 翻译 | 外企细抠真实性 |

---

## 四、5 个 STAR 故事板（行为面试用）

### Story 1 ── "讲一个你设计抽象的经历"

**Situation**: 业务要把商品同步上架到 4 个渠道，每个渠道 API 完全不同。原本设想是"每接一个写一坨代码"，5-7 天/渠道。

**Task**: 我要把这套重复工作"做一次省 N 次"，沉淀成框架。

**Action**:
1. 调研 4 个平台的 API 文档，画出"共性"和"差异性"两张表
2. **共性提取**：所有渠道都要"推送/上下架/同步状态/解析回调"4 个动作，抽出 `IPlatformProductStrategy` 接口
3. **差异隔离**：鉴权（HMAC/MD5/Token+AES）、字段映射、价格单位放各 Strategy 实现内
4. **公共流程封装**：参数校验、限流、重试、异常归类 4 件套全员都需要，放到 `AbstractPlatformStrategy` 模板方法基类
5. **避免手动注册**：用 `ApplicationContextAware` 让工厂运行时自动发现所有 Strategy，新增渠道连工厂都不用改

**Result**: 抖音首次接入 7 天（含调研 + 框架搭建），后续小红书/微信平均 3 天/渠道。框架核心代码 ~5k 行，4 渠道差异性代码各 300-500 行。

**Lesson**: 抽象的价值不是"少写代码"，是"隔离变化的方向"——平台 API 改动只影响一个 Strategy，不会牵连其他渠道。

---

### Story 2 ── "讲一个你修过的最难 bug"

**Situation**: 写完 `CaffeineTokenManager` 后端到端测试一切正常，Phase 8 写单测时遇到一个怪事：单测里先调 `getToken` 再调 `refresh` 期望拿到新 token，**结果返回的还是旧 token**。

**Task**: 必须搞清楚为什么"强制刷新"失效了。

**Action**:
1. **加日志复现**：在 `doRefresh` 入口和出口加日志，确认 fetch 没被调用
2. **代码 review**：`refresh()` 直接调 `doRefresh(channel, "forced")`，看似没毛病
3. **意识到 double-check 陷阱**：`doRefresh` 内部有 double-check 是为了单飞机制——多线程同时 miss 抢锁后，复用前一个 fetch 结果。**但 refresh() 是单线程语义，前一次 getToken 缓存的 token 还在，double-check 命中返回旧值**
4. **修复**：`refresh()` 必须先 `cache.invalidate(channel)` 再 `doRefresh`
5. **加单测**：补 `getTokenThenForceRefresh_shouldReturnNewToken` 用例锁死这个语义

**Result**: bug 修复，单测从此能稳定回归这个陷阱。

**Lesson**:
> "这个 bug 在端到端测试没暴露，因为当时 cache 是空的。**单测的价值不是覆盖率，是逼出你接口语义上的陷阱**。共享同一底层函数的多个 public API，要小心它们对预置条件的要求不一样。"

---

### Story 3 ── "讲一个你和同事/leader 有分歧的经历"

**Situation**: Leader 一开始建议"Token 过期就让请求自然失败，调用方自己 retry 就行"。

**Task**: 我想说服他做主动续期 + 单飞。

**Action**:
1. **画时序图算账**：高峰 100 QPS 场景下，token 过期瞬间所有请求同时刷 token，平台限流必触发，**业务方感知到的不是"我的请求慢了"，而是"系统挂了"**
2. **提替代方案的成本**：主动续期就一个 `@Scheduled` + 单飞锁，~150 行代码，单测 100 行
3. **承认 trade-off**：如果业务低频（每天几十次），这套是浪费。但我们预期 1k+ QPS，必须做
4. **接受 leader 的折中**：Scheduler 间隔不要太短（最终定 1min），避免空转

**Result**: 方案通过，上线后没出现过 token 相关的雪崩。

**Lesson**:
> "技术决策不是'谁说服谁'，是'对成本/收益达成一致'。**用数字说话**比'我觉得'更有力。"

---

### Story 4 ── "讲一个你做错的设计 / 走过的弯路"

**Situation**: 模板方法基类一开始用了 `catch (Exception e) { throw new ChannelException(...) }` 兜底，所有异常都被打扮成 `ChannelException`。

**Task**: 想统一异常处理，避免 controller 收到一堆奇怪异常。

**Action**:
1. **当时的设计**：`AbstractPlatformStrategy` 模板方法包了 try-catch，所有 `doXxx` 抛的异常都 wrap 成 ChannelException
2. **问题暴露**：测状态机非法转移时，`IllegalStateException` 被吞掉、wrap 成 ChannelException，GlobalExceptionHandler 把它映射成 400 而不是 409
3. **意识到 anti-pattern**：`catch (Exception e)` 是危险信号，它会把"我不该 wrap 的异常"也吃掉
4. **修复**：分两层 catch
   ```java
   catch (ChannelException | IllegalStateException e) { throw e; }  // 已分类，原样抛
   catch (Exception e) { throw classify(e); }                       // 未分类，归类
   ```

**Result**: 异常映射准确，HTTP code 符合预期。

**Lesson**:
> "**'catch Exception' 应该被当成代码异味（code smell）**——它意味着你没想清楚哪些异常该处理、哪些该抛出去。修这个 bug 之后我形成了一个原则：'我不认识的异常才 wrap'。"

---

### Story 5 ── "讲一个你做的技术选型 / 取舍"

**Situation**: 缓存层选 Caffeine 还是 Redis？

**Task**: Token 需要缓存，要给出选型理由。

**Action**:
1. **列需求**：① 缓存 token（KV）；② TTL；③ 并发安全；④ 单实例 OK，多实例理想
2. **对比**：
   - Caffeine：嵌入式、零运维、单进程、毫秒级延迟
   - Redis：多实例共享、需要运维、毫秒级延迟（但有网络开销）、单点故障要考虑哨兵/集群
3. **看团队部署**：单实例 + 备份实例（不分担流量），多实例需求**不存在**
4. **算成本**：Redis 引入要申请资源、配置哨兵、加监控、写 fallback 代码……~3 天工作量
5. **决策**：先用 Caffeine，但**接口抽象**（`AccessTokenManager` 接口 + `CaffeineTokenManager` 实现），将来要换 Redis 只换一个实现类

**Result**: Phase 4 一周内完成 Token 管理模块。如果将来部署改多实例，只需新增 `RedissonTokenManager implements AccessTokenManager` + 改一行 `@Primary`。

**Lesson**:
> "**技术选型要看'当前实际需求 + 演进留口子'**，不要为了'看起来高级'引入不需要的依赖。**面向接口编程**是最便宜的演进保险。"

---

## 五、常被问的"项目元信息"准备

| 问题 | 准备答案 |
|------|---------|
| 项目持续多久？ | "核心开发期 2-3 周，包括框架抽象 + 4 渠道 + 单测。上线后持续维护中。" |
| 团队几个人？ | "我主导设计 + 开发，1 个 leader code review，1 个测试同学跟 QA。" |
| 你负责什么？ | "全栈负责：技术方案、核心抽象层、4 个渠道的 Strategy 实现、基础设施（Token/状态机/异常体系）、单测。" |
| 项目代码量？ | "~5k 行核心代码 + ~1.5k 行单测，54 个 Java 类。" |
| 用了多少天？ | "调研 + 设计 3 天，主体开发 10 天，单测 + 文档 3 天。" |
| 上线效果？ | "暂未上线（公司侧凭证申请中）。本地 + dev 环境 H2 跑通全链路，单测 69 个全绿。" *（实话）* |
| 如果重做会改什么？ | "① 提前接 RocketMQ 做回调异步 + DLQ；② 接 Micrometer + Prometheus 看指标；③ 缓存从 Caffeine 改 Redis（如果将来多实例）。" |

---

## 六、最后的一句忠告

**不要试图"装作什么都做过"**。面试官最讨厌的不是"你没做过 XX"，而是"你说做过 XX 但答不出细节"。

老老实实承认 follow-up 没做，能接住"你为什么这么决策" 比"看起来全做了但每问一个就慌"强 100 倍。

简历用 🅱️ 推荐版，面试时主动说："这部分还没做（指 MQ / Observability / 真联调），是项目 follow-up，已经在 IMPLEMENTATION-PLAN.md 里列了 out-of-scope" —— **诚实 + 有规划** 是最强的组合。
