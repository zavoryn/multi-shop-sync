-- Dev 种子数据：示例类目映射
INSERT INTO category_mapping (local_category_id, channel, external_category_id, category_name) VALUES
  (1001, 'douyin',       '20000', '女装>连衣裙'),
  (1001, 'xiaohongshu',  'XHS_DRESS_001', '女装/连衣裙'),
  (1001, 'wechat',       '900001', '服饰鞋包/连衣裙'),
  (1002, 'douyin',       '30000', '美妆>口红'),
  (1002, 'xiaohongshu',  'XHS_LIPSTICK_001', '彩妆/唇彩'),
  (1002, 'wechat',       '900020', '美妆护肤/唇部彩妆');
