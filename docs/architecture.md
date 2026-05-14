# 架构设计文档

---

## 一、设计原则

| 原则 | 落地方式 |
|------|---------|
| 开闭原则 (OCP) | 新增渠道不修改现有代码，只需新增 Strategy 实现类 |
| 单一职责 (SRP) | 每个 Strategy 只负责一个渠道的对接逻辑 |
| 依赖倒置 (DIP) | Service 层依赖抽象接口，不依赖具体实现 |
| 防腐层 (ACL) | Strategy 充当防腐层，屏蔽各平台 API 差异 |

---

## 二、标准商品模型

系统内部只流转 `StandardProductDTO`，各渠道 Strategy 负责将其转换为目标平台所需的格式：

```
StandardProductDTO (内部标准模型)
        │
        ├──→ DouyinStrategy      → 抖店 product.addV2 格式
        ├──→ XiaohongshuStrategy → 小红书 createItemAndSku 格式
        ├──→ WechatStrategy      → 微信 channels/ec/product/add 格式
        └──→ LocalStrategy       → 本地商城格式（直接入库）
```

### 标准模型字段

| 字段 | 说明 | 对应关系 |
|------|------|---------|
| title | 商品标题 | 各平台通用 |
| categoryId | 内部类目 ID | 通过 CategoryMapping 转换为目标平台类目 |
| mainImages | 主图列表 | 各平台图片格式不同，Strategy 负责转换 |
| skus | SKU 列表 | 各平台 SKU 字段名和单位不同（分/元） |
| description | 商品描述 | 微信只支持图片URL，抖音也是，小红书支持文本 |

---

## 三、策略模式实现

### 3.1 策略接口 (IPlatformProductStrategy)

```java
public interface IPlatformProductStrategy {
    ChannelEnum getChannel();
    boolean pushProduct(StandardProductDTO product);
    boolean changeStatus(Long productId, ProductStatusEnum status);
    void syncPlatformStatus(String outProductId);
    ProductStatusEnum parseCallback(Map<String, Object> callbackData);
}
```

### 3.2 抽象基类 (AbstractPlatformStrategy)

利用**模板方法模式**，将通用逻辑上提到抽象类：

```
pushProduct(StandardProductDTO)              ← 模板方法
  ├── validateProduct(StandardProductDTO)     ← 公共校验
  ├── doPushProduct(StandardProductDTO)       ← 子类实现（抽象方法）
  ├── logResult(result)                       ← 公共日志
  └── handleException(e)                      ← 公共异常处理
```

抽象基类处理：
- 请求前的参数校验
- 统一日志记录
- 限流控制
- Token 自动刷新
- 重试机制（Spring Retry）
- 异常统一包装

### 3.3 工厂类 (PlatformStrategyFactory)

```java
@Component
public class PlatformStrategyFactory implements ApplicationContextAware {

    private Map<ChannelEnum, IPlatformProductStrategy> strategyMap;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        // Spring 自动注入所有 Strategy 实现，无需手动注册
        strategyMap = ctx.getBeansOfType(IPlatformProductStrategy.class)
            .values().stream()
            .collect(Collectors.toMap(
                s -> s.getChannel(),
                Function.identity()
            ));
    }

    public IPlatformProductStrategy getStrategy(ChannelEnum channel) {
        IPlatformProductStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new ChannelException("不支持的渠道: " + channel);
        }
        return strategy;
    }
}
```

---

## 四、状态流转机制

### 4.1 状态机设计

```
DRAFT(草稿)
  │
  ▼ pushProduct()
WAIT_PLATFORM_AUDIT(平台审核中)
  │
  ├──→ 回调: 审核通过 ──→ ON_SHELF(已上架)
  ├──→ 回调: 审核驳回 ──→ AUDIT_REJECT(审核驳回)
  │
  ▼ changeStatus(OFF_SHELF)
OFF_SHELF(已下架)
```

### 4.2 双状态管理

每个渠道商品维护两个状态：
- **本地状态 (local_status)**: 系统内部状态
- **外部状态 (external_status)**: 平台实际状态

| 本地状态 | 含义 | 外部状态映射 |
|----------|------|-------------|
| DRAFT | 草稿 | 无 |
| WAIT_PLATFORM_AUDIT | 已提交审核 | 抖音=待审核, 微信=审核中, 小红书=审核中 |
| ON_SHELF | 已上架 | 抖音=售卖中, 微信=已上架, 小红书=可购买 |
| AUDIT_REJECT | 审核驳回 | 各平台驳回状态 |
| OFF_SHELF | 已下架 | 抖音=已下架, 微信=已下架, 小红书=已下架 |

### 4.3 回调处理流程

```
平台 Webhook
  │
  ▼
WebhookController (统一入口: /api/webhook/{channel})
  │
  ▼
WebhookDispatcher
  ├── 解析渠道标识
  ├── 通过工厂获取对应 Strategy
  ├── 调用 parseCallback() 解析平台特有的回调报文
  └── 更新本地状态
```

---

## 五、类目映射机制

各平台的类目体系完全不同，通过**类目映射表**做转换：

```sql
-- category_mapping 表
CREATE TABLE category_mapping (
    id BIGINT PRIMARY KEY,
    local_category_id BIGINT,    -- 本地类目 ID
    channel VARCHAR(32),          -- 渠道标识
    external_category_id VARCHAR(128), -- 外部平台类目 ID
    category_name VARCHAR(255)    -- 外部类目名称
);
```

Strategy 在推送时查询映射表，将本地类目转换为目标平台类目。

---

## 六、异常处理与稳定性

| 场景 | 处理方式 |
|------|---------|
| 网络抖动 | Spring Retry 本地重试（最多3次） |
| 平台限流 | Sentinel 出口限流 + 降级 |
| 平台繁忙 | MQ 延迟消息异步补偿重推 |
| Token 过期 | 抽象基类自动检测并刷新 |
| 回调丢失 | XXL-JOB 定时轮询补偿（查 WAIT_PLATFORM_AUDIT 状态的商品） |
