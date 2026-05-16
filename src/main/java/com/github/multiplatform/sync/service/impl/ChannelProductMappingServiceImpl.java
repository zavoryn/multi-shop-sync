package com.github.multiplatform.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.multiplatform.sync.common.dao.entity.ChannelProductMapping;
import com.github.multiplatform.sync.common.dao.mapper.ChannelProductMappingMapper;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import com.github.multiplatform.sync.service.ChannelProductMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelProductMappingServiceImpl implements ChannelProductMappingService {

    private final ChannelProductMappingMapper mapper;

    @Override
    public Optional<ChannelProductMapping> find(String outProductId, ChannelEnum channel) {
        QueryWrapper<ChannelProductMapping> wrapper = new QueryWrapper<>();
        wrapper.eq("out_product_id", outProductId).eq("channel", channel.getCode()).last("LIMIT 1");
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public void upsertAfterPush(String outProductId, ChannelEnum channel, String externalProductId) {
        Optional<ChannelProductMapping> existing = find(outProductId, channel);
        if (existing.isPresent()) {
            ChannelProductMapping m = existing.get();
            m.setExternalProductId(externalProductId);
            m.setLocalStatus(ProductStatusEnum.WAIT_PLATFORM_AUDIT.getCode());
            m.setLastSyncTime(LocalDateTime.now());
            mapper.updateById(m);
            log.debug("更新商品映射: outProductId={}, channel={}", outProductId, channel);
        } else {
            ChannelProductMapping m = new ChannelProductMapping();
            m.setOutProductId(outProductId);
            m.setChannel(channel.getCode());
            m.setExternalProductId(externalProductId);
            m.setLocalStatus(ProductStatusEnum.WAIT_PLATFORM_AUDIT.getCode());
            m.setLastSyncTime(LocalDateTime.now());
            mapper.insert(m);
            log.debug("新增商品映射: outProductId={}, channel={}", outProductId, channel);
        }
    }

    @Override
    public void updateStatus(String outProductId, ChannelEnum channel, ProductStatusEnum status,
                             String externalStatus, String rejectReason) {
        Optional<ChannelProductMapping> existing = find(outProductId, channel);
        if (!existing.isPresent()) {
            log.warn("状态更新失败，映射记录不存在: outProductId={}, channel={}", outProductId, channel);
            return;
        }
        ChannelProductMapping m = existing.get();
        m.setLocalStatus(status.getCode());
        if (externalStatus != null) m.setExternalStatus(externalStatus);
        if (rejectReason != null) m.setRejectReason(rejectReason);
        m.setLastSyncTime(LocalDateTime.now());
        mapper.updateById(m);
    }

    @Override
    public void upsertLocal(String outProductId) {
        Optional<ChannelProductMapping> existing = find(outProductId, ChannelEnum.LOCAL);
        if (existing.isPresent()) {
            ChannelProductMapping m = existing.get();
            m.setLocalStatus(ProductStatusEnum.ON_SHELF.getCode());
            m.setLastSyncTime(LocalDateTime.now());
            mapper.updateById(m);
        } else {
            ChannelProductMapping m = new ChannelProductMapping();
            m.setOutProductId(outProductId);
            m.setChannel(ChannelEnum.LOCAL.getCode());
            m.setExternalProductId(outProductId);
            m.setLocalStatus(ProductStatusEnum.ON_SHELF.getCode());
            m.setLastSyncTime(LocalDateTime.now());
            mapper.insert(m);
        }
    }
}
