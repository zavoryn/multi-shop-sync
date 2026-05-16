# 三平台商品状态字段 → 标准枚举映射

> 起草日期：2026-05-17
> 用途：Phase 5（`doSyncPlatformStatus` 完整实现）的对照表 single source of truth
> 标准枚举：`com.github.multiplatform.sync.common.enums.ProductStatusEnum`
>   - DRAFT(0) / WAIT_PLATFORM_AUDIT(10) / ON_SHELF(20) / AUDIT_REJECT(30) / OFF_SHELF(40)

---

## 1. 抖音小店

**接口**：`product.detail`
**关键字段**：`data.status`
**官方状态机文档**：https://op.jinritemai.com/docs/question-docs/92/2070

| status | 含义 | 标准枚举 |
|--------|------|---------|
| 0 | 在线 | `ON_SHELF` |
| 1 | 下线 | `OFF_SHELF` |
| 2 | 删除 | `OFF_SHELF` |

> 注：抖音另有 `check_status` 字段（审核：0=待审/1=通过/2=驳回/3=封禁），
> 在 SPU 创建后短暂出现。Phase 5 主路径只用 `status`；如果业务需要更细的"审核中"
> 信号，再补 `check_status` 联合判定。

---

## 2. 小红书

**接口**：`product.getDetailItemList`（按 `id` 单查，原 `product.getItemInfo` 在官方索引中**不存在**）
**关键字段**：`data[0].itemData.buyable: boolean` + `freeze: boolean`
**索引来源**：`docs/xiaohongshu-research/xhs-api-index.md`、`docs/xiaohongshu-research/api/api-24685962.md`

| buyable | freeze | 标准枚举 |
|---------|--------|---------|
| true    | false  | `ON_SHELF` |
| true    | true   | `OFF_SHELF` （平台冻结视为下架）|
| false   | *      | `OFF_SHELF` |

> 注：小红书的"审核中 / 驳回"不通过本查询接口暴露，只能通过回调（msgTag）感知：
> - `msg_item_create` → WAIT_PLATFORM_AUDIT
> - `msg_item_audit_reject` → AUDIT_REJECT
> 因此 syncPlatformStatus 返回的状态只覆盖 ON_SHELF / OFF_SHELF。
> 如果业务里本地状态是 WAIT，sync 拿到 ON_SHELF 是合法转移（状态机已允许）。

---

## 3. 微信小店 / 视频号小店

**接口**：`channels/ec/product/get`
**关键字段**：`data.status`（也出现在 `edit_product` 接口）
**官方文档**：https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_getproduct.html

| status | 描述 | 标准枚举 | 备注 |
|--------|------|---------|------|
| 0  | 初始值 | `DRAFT` ||
| 1  | 编辑中 | `DRAFT` ||
| 2  | 审核中 | `WAIT_PLATFORM_AUDIT` ||
| 3  | 审核失败 | `AUDIT_REJECT` ||
| 4  | 审核成功 | `WAIT_PLATFORM_AUDIT` | 审核通过但尚未上架 |
| 5  | 上架 | `ON_SHELF` ||
| 6  | 回收站 | `OFF_SHELF` ||
| 7  | 商品异步提交，上传中 | `WAIT_PLATFORM_AUDIT` ||
| 8  | 商品异步提交，上传失败 | `AUDIT_REJECT` | 视为同步失败 |
| 9  | 彻底删除 | `OFF_SHELF` | 终态，业务侧可单独清理 mapping |
| 10 | 冻结，审核通过但不能上架 | `OFF_SHELF` ||
| 11 | 自主下架 | `OFF_SHELF` ||
| 12 | 售罄下架 | `OFF_SHELF` ||
| 13 | 违规下架 / 风控系统下架 | `OFF_SHELF` ||
| 14 | 保证金不足下架 | `OFF_SHELF` ||
| 15 | 品牌过期下架 | `OFF_SHELF` ||
| 20 | 商品被封禁 | `AUDIT_REJECT` | 严重违规，按驳回类处理 |
| 21 | SKU 逻辑删除 | `OFF_SHELF` ||
| 30 | 商品不存在 | `null` | 上层应清理 mapping |
| 70 | 商品异步提审中 | `WAIT_PLATFORM_AUDIT` ||
| 71 | 质检不通过 | `AUDIT_REJECT` ||
| 72 | 异步提审失败：当日 quota 不足 | `AUDIT_REJECT` ||
| 73 | 异步提审失败：限频触发 | `AUDIT_REJECT` ||

---

## 4. Phase 5 实施清单

- [ ] 抖音：`DouyinPlatformStrategy.doSyncPlatformStatus` 解析 `data.status` 三值映射
- [ ] 小红书：换接口为 `product.getDetailItemList` + buyable/freeze 联合判定
- [ ] 微信：完整 22 个状态值的 switch 映射
- [ ] 三家共享辅助：把映射逻辑各自封装为静态私有方法 `mapStatus(int) → ProductStatusEnum`，便于 Phase 8 单测
