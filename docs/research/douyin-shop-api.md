# 抖音小店（抖店）API 调研文档

> 调研日期：2026-05-15
> 调研版本：基于抖店开放平台最新文档

---

## 一、文档入口

| 资源 | 链接 |
|------|------|
| 开放平台首页 | https://op.jinritemai.com/docs/api-docs |
| 商品发布 API | https://op.jinritemai.com/docs/api-docs/14/5980 |
| 商品上架 API | https://op.jinritemai.com/docs/api-docs/14/5980 |
| 商品下架 API | https://op.jinritemai.com/docs/api-docs/14/252 |
| 商品详情 API | https://op.jinritemai.com/docs/api-docs/14/56 |
| 签名算法文档 | https://op.jinritemai.com/docs/api-docs/14/56 |
| Token 获取 | https://op.jinritemai.com/docs/api-docs/14/56 |
| 商品变更回调 | https://op.jinritemai.com/docs/api-docs/14/56 |

---

## 二、认证机制

### 2.1 获取 Access Token

**步骤一：获取授权码**

测试店铺可从控制台复制（有效期 10 分钟）；生产环境通过服务市场授权获取。

**步骤二：用授权码换取 Token**

```
GET https://openapi-fxg.jinritemai.com/token/create
    ?app_key=xxx
    &method=token.create
    &param_json={"code":"xxx","grant_type":"authorization_code"}
    &timestamp=xxx
    &v=2
    &sign=xxx
    &sign_method=hmac-sha256
```

**返回**:
```json
{
  "data": {
    "access_token": "6c4699a1-f2ff-433e-a73e-87378009f0bb",
    "expires_in": 530808,
    "refresh_token": "ed14a703-1f27-4a0b-9b94-759242744ec8",
    "shop_id": "4463798",
    "shop_name": "店铺名"
  }
}
```

- `access_token` 有效期：**7天**
- `refresh_token` 有效期：**14天**

**步骤三：刷新 Token**

过期前 >1h 刷新返回原 token；<1h 刷新返回新 token。

### 2.2 签名算法（HMAC-SHA256）

**公共参数（每个请求必传）**:

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `method` | String | 是 | API 方法名 |
| `app_key` | String | 是 | 应用 Key |
| `access_token` | String | 是 | OAuth Token |
| `param_json` | String | 是 | 业务参数 JSON（POST Body） |
| `timestamp` | String | 是 | Unix 时间戳 |
| `v` | String | 是 | 版本号，固定 `2` |
| `sign` | String | 是 | 签名 |
| `sign_method` | String | 否 | `hmac-sha256`（推荐） |

**签名计算步骤**:

```
Step 1: 按字母序排列参数拼接
  paramPattern = "app_key" + appKey + "method" + method + "param_json" + paramJson + "timestamp" + timestamp + "v" + v

Step 2: 前后拼接 app_secret
  signPattern = appSecret + paramPattern + appSecret

Step 3: HMAC-SHA256
  sign = HMAC-SHA256(signPattern, appSecret)
```

**HTTP 请求格式**:
```
POST https://openapi-fxg.jinritemai.com/product/addV2
    ?method=product.addV2&app_key=xxx&access_token=xxx&timestamp=xxx&v=2&sign=xxx
Content-Type: application/json

{"name":"xxx","category_leaf_id":20000,...}
```

---

## 三、核心商品 API

### 3.1 商品发布（product.addV2）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 是 | 商品名称（8-60字符） |
| `category_leaf_id` | Number | 是 | 叶子类目 ID |
| `pic` | String | 是 | 轮播图，`\|` 分隔，最多5张 |
| `description` | String | 是 | 商品描述（仅图片URL） |
| `product_type` | Number | 是 | 0=普通, 3=虚拟 |
| `reduce_type` | Number | 是 | 1=下单减库存, 2=付款减库存 |
| `freight_id` | Number | 是 | 运费模板 ID |
| `mobile` | String | 是 | 客服电话 |
| `commit` | Boolean | 是 | `false`=保存草稿, `true`=提交审核 |
| `spec_prices` | String | 是 | SKU JSON 数组 |
| `start_sale_type` | Number | 否 | 0=审核后立即上架, 1=放入仓库 |

**SKU 结构（spec_prices）**:
```json
[{
  "spec_detail_name1": "红色",
  "spec_detail_name2": "S",
  "stock_num": 11,
  "price": 100,
  "code": "",
  "outer_sku_id": ""
}]
```

**注意**：`price` 单位为**分**。

### 3.2 商品上架（product.launchProduct）

```json
{"product_id": 12345}
```

限流：200请求/秒（每应用），1500请求/秒（总计）。

### 3.3 商品下架（product.setOffline）

### 3.4 其他商品 API

| API | 说明 |
|-----|------|
| `product.editV2` | 编辑商品 |
| `product.detail` | 商品详情 |
| `product.listV2` | 商品列表 |
| `product.del` | 删除商品 |
| `product.auditList` | 审核记录 |
| `product.getCatePropertyV2` | 类目属性 |
| `product.getCategories` | 获取商家类目 |

---

## 四、商品状态流转

```
创建(addV2, commit=false) → 草稿
     │
提交审核(addV2, commit=true 或 launchProduct)
     │
     ▼
待审核 ──→ 审核通过(event=4) → 售卖中(start_sale_type=0)
       └──→ 审核不通过(event=5) → 修改后重新提交
     │
     ▼
下架(setOffline) → 已下架
```

**关键限制**：
- 每天每店最多新增 10,000 个商品
- 审核期间无法编辑
- 未修改被驳回商品直接重提会被拒绝

---

## 五、回调事件通知

### 5.1 商品变更回调（doudian_product_change）

**Tag**: `400`

**事件类型**:

| event 值 | 说明 |
|----------|------|
| 1 | 商品创建 |
| 2 | 商品保存 |
| 3 | 商品提交审核 |
| **4** | **商品审核通过** |
| **5** | **商品审核不通过** |
| 6 | 商品删除 |
| 8 | 商品封禁 |
| **10** | **商品下架** |
| **11** | **商品上架** |
| 14 | 审核通过待上架 |

**回调数据格式**:
```json
[{
  "tag": "400",
  "msgId": "xxx",
  "data": {
    "event": 4,
    "product_id": 3488625851803765000,
    "shop_id": 95250,
    "update_time": 16473829442,
    "reason": "审核通过"
  }
}]
```

**鉴权**：回调请求体使用 `app_secret` 进行 **AES 加密**，需要解密后读取。

**响应格式**: `{"code": 0, "msg": "success"}`

---

## 六、完整对接流程

```
1. 创建应用 → 获取 app_key + app_secret
2. 商家授权 → 获取 code
3. token.create → 获取 access_token
4. 获取类目 / 类目属性 / 运费模板 等前置数据
5. product.addV2(commit=true) → 提交审核
6. 等待回调 event=4/5
7. 审核通过 → 自动/手动上架
8. 日常管理：editV2 / setOffline / launchProduct
9. 定期 token.refresh 刷新令牌
```
