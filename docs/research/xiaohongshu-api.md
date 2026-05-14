# 小红书电商开放平台 API 调研文档

> 调研日期：2026-05-15
> 调研版本：基于小红书开放平台 API v2.0 最新文档

---

## 一、文档入口

| 资源 | 链接 |
|------|------|
| 开放平台首页 | https://open.xiaohongshu.com |
| 商品创建 API | https://open.xiaohongshu.com/document/api?apiNavigationId=305&id=108&gatewayId=103&gatewayVersionId=1661&apiId=38757&apiParentNavigationId=17 |
| 商品上下架 API | https://open.xiaohongshu.com/document/api?apiNavigationId=305&id=108&gatewayId=103&gatewayVersionId=1661&apiId=38757&apiParentNavigationId=17 |
| OAuth 授权 | https://open.xiaohongshu.com/document/api?apiNavigationId=305&id=108&gatewayId=103&gatewayVersionId=1661&apiId=38757&apiParentNavigationId=17 |
| 签名算法 | https://open.xiaohongshu.com/document/api?apiNavigationId=305&id=108&gatewayId=103&gatewayVersionId=1661&apiId=38757&apiParentNavigationId=17 |
| 消息推送 | https://open.xiaohongshu.com/document/api?apiNavigationId=305&id=108&gatewayId=103&gatewayVersionId=1661&apiId=38757&apiParentNavigationId=17 |

---

## 二、认证机制

### 2.1 OAuth2.0 授权流程

1. 在开放平台创建应用，获取 `appId` + `appSecret`
2. 添加店铺 sellerId 到应用授权管理
3. 浏览器打开授权链接，商户点击授权
4. 平台回调携带 `code`
5. 用 `code` 换取 `accessToken`

### 2.2 获取 Access Token

**请求**:
```json
{
  "sign": "6a76602***68400",
  "appId": "27d***b081e7",
  "timestamp": "171***379",
  "version": "2.0",
  "method": "oauth.getAccessToken",
  "code": "code-17ec062d187248bcbb7e5ba4e10f1c22-xxx"
}
```

**返回**:
```json
{
  "error_code": 0,
  "data": {
    "accessToken": "token-xxx",
    "accessTokenExpiresAt": 1712239430773,
    "refreshToken": "refresh-xxx",
    "refreshTokenExpiresAt": 1712844230773,
    "sellerId": "5a151****ee832",
    "sellerName": "测试店铺"
  },
  "success": true
}
```

- `accessToken` 有效期：**7天**
- `refreshToken` 有效期：**14天**
- 可在 AT 过期前 30 分钟 ~ RT 过期之间刷新

### 2.3 刷新 Token

```json
{
  "method": "oauth.refreshToken",
  "appId": "xxx",
  "refreshToken": "refresh-xxx",
  "sign": "xxx",
  "timestamp": "xxx",
  "version": "2.0"
}
```

---

## 三、签名算法（MD5）

**计算公式**: `MD5(method + "?" + sortedSystemParams + appSecret)`

**步骤**:
```
1. 收集系统参数: appId, timestamp, version
2. 按字母序排序: appId=xxx&timestamp=xxx&version=2.0
3. 拼接: method?appId=xxx&timestamp=xxx&version=2.0
4. 末尾追 appSecret
5. MD5 哈希
```

**注意**：`accessToken` **不参与**签名计算。

```python
import hashlib
def generate_sign(app_id, timestamp, method, app_secret):
    params = [f"appId={app_id}", f"timestamp={timestamp}", "version=2.0"]
    params.sort()
    query_str = "&".join(params)
    sign_source = f"{method}?{query_str}{app_secret}"
    return hashlib.md5(sign_source.encode("utf-8")).hexdigest()
```

---

## 四、公共请求参数

每个 API 调用 JSON 请求体中包含：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `method` | String | 是 | API 方法名 |
| `appId` | String | 是 | 应用 ID |
| `sign` | String | 是 | MD5 签名 |
| `timestamp` | String | 是 | Unix 时间戳（秒） |
| `version` | String | 是 | 固定 `2.0` |
| `accessToken` | String | 是 | OAuth Token（不参与签名） |

**API 网关**: `https://ark.xiaohongshu.com/ark/open_api/v3/common_controller`

---

## 五、核心商品 API

### 5.1 创建商品（product.createItemAndSku）

**必填字段**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | String | 是 | 商品标题 |
| `categoryId` | String | 是 | 叶级类目 ID（通过 common.getCategories 获取） |
| `shippingTemplateId` | String | 是 | 运费模板 ID |
| `images` | Array | 是 | 商品主图 `[{link: "..."}]` |
| `createSkuList` | Array | 是 | SKU 列表（至少一个） |

**SKU 结构（createSkuList）**:
```json
[{
  "originalPrice": 600000,
  "price": 500000,
  "stock": 100,
  "variants": [{"id": "xxx", "name": "颜色分类", "value": "红色"}],
  "erpCode": "ERP001",
  "barcode": "6901234567890"
}]
```

**注意**：价格单位为**分**（如 500000 = 5000.00 元）。

**可选重要字段**:
- `variantIds` — 变体类型 ID（颜色/尺码）
- `attributes` — 商品属性
- `brandId` — 品牌 ID
- `description` — 描述文本（最多500字）
- `deliveryMode` — 0=普通, 1=无物流

### 5.2 商品上架（product.updateSkuAvailable）

```json
{
  "skuId": "1",
  "available": true
}
```

### 5.3 商品下架

```json
{
  "skuId": "1",
  "available": false
}
```

### 5.4 前置数据 API（必须先调用）

| API | 说明 |
|-----|------|
| `common.getCategories` | 获取类目树 |
| `common.getVariations` | 获取规格/变体 |
| `common.getAttributeLists` | 获取属性 |
| `common.getAttributeValues` | 获取属性值 |
| `common.getCarriageTemplateList` | 获取运费模板 |
| `common.brandSearch` | 搜索品牌 |
| `common.getLogisticsList` | 物流方案 |
| `common.categoryMatch` | 标题预测类目 |

### 5.5 其他商品 API

| API | 说明 |
|-----|------|
| `product.updateItemAndSku` | 更新商品 |
| `product.searchItemList` | 搜索商品 |
| `product.getItemInfo` | 商品详情 |
| `product.updateSkuPrice` | 更新价格 |
| `product.updateItemImage` | 更新图片 |

---

## 六、商品状态流转

```
创建(createItemAndSku) → 草稿/审核中
     │
     ▼
平台审核 ──→ 通过 → 可上架(updateSkuAvailable, available=true)
          └──→ 驳回(msg_item_audit_reject) → 修改后重新提交
     │
     ▼
上架 → 售卖中
     │
     ▼
下架(updateSkuAvailable, available=false) → 已下架
```

**关键规则**：
- SKU 一旦启用就无法禁用，只能下架
- 商品创建后会自动进入平台审核流程

---

## 七、回调事件通知（消息推送）

### 7.1 配置方式

1. 开放平台控制台 → 应用管理 → 启用推送服务
2. 配置推送 URL（支持 https/http）
3. 验证推送 URL（对测试 POST 返回 200）
4. 订阅消息类型

### 7.2 商品相关事件

| 事件 | msgTag | 说明 |
|------|--------|------|
| 商品创建 | `msg_item_create` | 商品被创建 |
| 商品上架/下架 | `msg_item_buyable` | 商品上架或下架 |
| 审核驳回 | `msg_item_audit_reject` | 审核不通过 |

### 7.3 推送数据格式

```json
[{
  "msgTag": "msg_item_buyable",
  "sellerId": "xxx",
  "data": "加密字符串"
}]
```

**响应**: `{"success": true, "error_code": 0, "error_msg": ""}`

### 7.4 推送签名验证

对所有 URL 参数（除 sign 外）按字母序排序，用 `&` 连接，拼接 URL + 参数字符串 + appSecret，计算 MD5。

### 7.5 重试策略

失败重试 3 次：30秒、5分钟、30分钟后。

**超时**：开发者服务器需在 **2秒内** 响应。

---

## 八、完整对接流程

```
1. 创建应用 → 获取 appId + appSecret
2. OAuth 授权 → 获取 accessToken
3. 获取前置数据（类目/规格/运费模板/品牌）
4. product.createItemAndSku → 创建商品+SKU
5. 等待审核（监听 msg_item_audit_reject 或 msg_item_buyable）
6. 审核通过 → product.updateSkuAvailable(available=true) 上架
7. 日常管理：updateItemAndSku / updateSkuPrice / updateSkuAvailable
8. 定期 refreshToken 续期
```
