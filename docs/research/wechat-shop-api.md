# 微信小店（视频号小店）API 调研文档

> 调研日期：2026-05-15
> 调研版本：基于微信小店开放平台最新文档

---

## 一、文档入口

| 资源 | 链接 |
|------|------|
| 开放平台首页 | https://developers.weixin.qq.com/doc/store/shop/ |
| 商品添加 API | https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_addproduct.html |
| 商品上架 API | https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_listing.html |
| 商品下架 API | https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_delisting.html |
| 商品审核回调 | https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/callback_audit.html |
| Access Token 获取 | https://developers.weixin.qq.com/doc/store/shop/API/channels-shop-product/shop/api_token.html |

---

## 二、认证机制

### 2.1 获取 Access Token

- **接口**: `GET https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=APPSECRET`
- **返回**:
```json
{
  "access_token": "ACCESS_TOKEN",
  "expires_in": 7200
}
```

### 2.2 第三方服务商模式

通过微信开放平台获取 `authorizer_access_token`，权限集 ID 为 **129**。

### 2.3 鉴权方式

所有 API 调用均通过 **Query 参数** 传递 `access_token`：
```
POST https://api.weixin.qq.com/channels/ec/product/add?access_token=ACCESS_TOKEN
```

---

## 三、核心商品 API

### 3.1 添加商品

- **URL**: `POST /channels/ec/product/add`
- **请求体关键字段**:

```json
{
  "title": "商品标题（5-60字）",
  "head_imgs": ["图片URL1", "图片URL2"],
  "cats_v2": [{"category_id": 123}],
  "deliver_method": 0,
  "skus": [{
    "out_sku_id": "商户自定义SKU ID",
    "sale_price": 9900,
    "stock_num": 100,
    "sku_attrs": {"颜色": "红色", "尺寸": "XL"}
  }],
  "out_product_id": "商户自定义商品ID（不可修改）",
  "listing": 1
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | string | 是 | 商品标题，5-60字符 |
| `head_imgs` | string[] | 是 | 主图，3-9张（食品/生鲜至少4张） |
| `cats_v2` | object[] | 是 | 类目 ID（支持多级） |
| `skus` | object[] | 是 | SKU 数组，1-500个 |
| `out_product_id` | string | 否 | 商户自定义 ID，创建后不可修改 |
| `listing` | number | 否 | 设为 1 则创建后自动提交上架 |
| `sale_price` | number | - | 价格单位为**分**，最大 1,000,000,000 |

**注意**：图片必须使用微信上传接口返回的 URL（前缀 `mmecimage.cn/p/`）。

### 3.2 商品上架

- **URL**: `POST /channels/ec/product/listing`
- **请求体**: `{"product_id": 123456}`

### 3.3 商品下架

- **URL**: `POST /channels/ec/product/delisting`
- **请求体**: `{"product_id": 123456}`

### 3.4 撤回审核

- **URL**: `POST /channels/ec/product/audit/cancel`

### 3.5 免审更新（已上架商品的小修改）

- **URL**: `POST /channels/ec/product/auditfree`

---

## 四、商品状态流转

微信小店采用 **草稿 + 线上 双份数据模型**：

```
创建商品(add) → 草稿存在
     │
     ▼
上架(listing) → 提交审核
     │
     ▼
审核结果 ──→ 通过 → 草稿升级为线上版本 → 已上架
         └──→ 驳回 → 草稿保留，附带驳回原因
     │
     ▼
下架(delisting) → 线上版本移除
```

**关键规则**：
1. `add` 和 `update` 只修改**草稿**，不影响线上版本
2. 必须通过 `listing` 接口提交审核，审核通过后才会上架
3. SKU 超过 25 个时，更新走**异步处理**，未完成时调用 listing 会返回错误码 `10020067`

---

## 五、回调事件通知

### 5.1 支持的事件类型

| 事件 | 说明 |
|------|------|
| 商品审核 | 审核结果（通过/驳回+原因） |
| 商品上下架 | 状态变更通知 |
| 商品更新 | 商品数据更新通知 |

### 5.2 回调机制

- 微信向预配置的回调 URL 发送 HTTP POST
- 服务端需在 **5秒内** 返回成功响应
- 失败时按指数退避重试

---

## 六、关键错误码

| 错误码 | 含义 |
|--------|------|
| 10020008 | 商品不可编辑（状态不对） |
| 10020017 | 无效类目 |
| 10020018 | 缺少类目资质 |
| 10020019 | 无效运费模板 |
| 10020066 | 提交审核频率超限 |
| 10020067 | 异步更新未完成（SKU>25） |
| 10020111 | 审核限频 |

---

## 七、完整对接流程

```
1. 获取 access_token
2. 上传商品图片 → 获得 mmecimage.cn/p/ URL
3. 调用 /channels/ec/product/add（可设 listing=1 自动提交审核）
4. 等待回调：审核通过 → 商品上架
5. 如被驳回 → 修改后重新 listing
6. 日常管理：update → listing 循环
```
