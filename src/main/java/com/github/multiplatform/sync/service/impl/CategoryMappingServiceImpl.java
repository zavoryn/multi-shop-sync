package com.github.multiplatform.sync.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.multiplatform.sync.common.dao.entity.CategoryMapping;
import com.github.multiplatform.sync.common.dao.mapper.CategoryMappingMapper;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import com.github.multiplatform.sync.service.CategoryMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryMappingServiceImpl implements CategoryMappingService {

    private final CategoryMappingMapper mapper;

    @Override
    public Optional<String> translate(Long localCategoryId, ChannelEnum channel) {
        if (localCategoryId == null) return Optional.empty();
        QueryWrapper<CategoryMapping> wrapper = new QueryWrapper<>();
        wrapper.eq("local_category_id", localCategoryId)
                .eq("channel", channel.getCode())
                .last("LIMIT 1");
        CategoryMapping found = mapper.selectOne(wrapper);
        return Optional.ofNullable(found).map(CategoryMapping::getExternalCategoryId);
    }

    @Override
    public String translateRequired(Long localCategoryId, ChannelEnum channel) {
        return translate(localCategoryId, channel)
                .orElseThrow(() -> new ChannelException(channel.getCode(),
                        "类目映射不存在: localCategoryId=" + localCategoryId));
    }
}
