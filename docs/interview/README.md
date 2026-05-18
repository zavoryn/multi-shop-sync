# 面试准备文档索引

> 4 份文档循序渐进，建议按 01 → 02 → 03 → 04 顺序读。

---

## 📖 文档总览

| # | 文档 | 用途 | 阅读时机 |
|---|------|------|---------|
| **01** | [架构与核心链路](01-architecture-and-flow.md) | 5 分钟讲清整个项目的链路、模块、3 条核心调用路径 | 面试前 3 天，让你能在白板画出架构 |
| **02** | [踩坑录](02-bugs-and-lessons.md) | 9 个真实 bug 的现象/根因/修复/收获，应对"讲你最难的 bug" | 面试前 2 天，挑 2-3 个反复练熟 |
| **03** | [Q&A 手册](03-interview-qa.md) | 30+ 高频问题分 5 类（设计模式/并发/中间件/平台安全/项目难点）| 面试前 1 周，反复刷 |
| **04** | [简历描述 + 故事板](04-resume-and-storytelling.md) | 3 个版本简历描述 + 5 个 STAR 故事板 | 修简历 + 行为面试准备 |

---

## ⚡ 速记 cheat sheet

### 一句话讲项目

> "多渠道商品同步框架，用策略 + 工厂 + 模板方法封装了 4 个平台（抖音/小红书/微信/本地）的鉴权、推送、状态流转、回调差异，新增渠道只需 3 步。"

### 最容易被深挖的 3 个点

1. **CaffeineTokenManager 单飞机制** —— 能徒手写出 doRefresh 的双重检查
2. **微信 AES-CBC + sha1 验签** —— 能讲清楚字段排序、padding、appId 校验
3. **异常分类 + 重试粒度** —— 能讲清为什么只重试 Network 异常

### 主讲 bug 推荐

- **Bug #1**：`refresh()` 强制刷新被 double-check 吞了，单测抓出 → 修复加 invalidate
- **Bug #2**：100 线程并发测试用了 `newFixedThreadPool(16)` 导致 worker starvation → 改 cached

### 最自豪设计推荐

异常分类 + 重试粒度（@Retryable(value = ChannelNetworkException.class) 的设计）

### 最大遗憾推荐

- 没接 RocketMQ 做回调异步 + DLQ
- 没接 Micrometer + Prometheus 看指标
- 没拿到沙箱凭证做真平台联调

---

## 🎯 真要面试时的最后一晚清单

- [ ] 能徒手画架构图
- [ ] 能讲 3 条核心链路（push / changeStatus / webhook）
- [ ] 能徒手写 CaffeineTokenManager 核心逻辑
- [ ] 能徒手写微信 sha1 验签 + AES 解密关键步骤
- [ ] 准备 2 个 bug 故事
- [ ] 准备 1 个"最自豪设计"
- [ ] 准备 1 个"最大遗憾"
- [ ] 准备 1 个"接下一个渠道"工作流
- [ ] 准备 1 个"多实例下怎么做"答案

---

## 🔗 与项目其他文档的关系

```
docs/
├── README-zh.md                ← 中文 README，给团队/读者看
├── IMPLEMENTATION-PLAN.md       ← 10 个 Phase 的实施记录
├── research/
│   ├── douyin-shop-api.md
│   ├── xiaohongshu-api.md
│   ├── wechat-shop-api.md
│   └── platform-status-mapping.md  ← Phase 5 沉淀的状态字段对照
└── interview/                  ← 本目录（面试用）
    ├── README.md                ← 你现在在看的
    ├── 01-architecture-and-flow.md
    ├── 02-bugs-and-lessons.md
    ├── 03-interview-qa.md
    └── 04-resume-and-storytelling.md
```

面试用的 4 份文档**不要放到简历里发出去**——这是你的"小抄"。简历上发的是 GitHub 仓库链接 + 修订过的项目描述。
