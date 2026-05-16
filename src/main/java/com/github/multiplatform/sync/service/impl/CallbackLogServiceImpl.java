package com.github.multiplatform.sync.service.impl;

import com.github.multiplatform.sync.common.dao.entity.CallbackLog;
import com.github.multiplatform.sync.common.dao.mapper.CallbackLogMapper;
import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.service.CallbackLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackLogServiceImpl implements CallbackLogService {

    private final CallbackLogMapper mapper;

    @Override
    public Long record(ChannelEnum channel, String eventType, String rawData, String outProductId) {
        CallbackLog log = new CallbackLog();
        log.setChannel(channel.getCode());
        log.setEventType(eventType);
        log.setRawData(rawData);
        log.setOutProductId(outProductId);
        log.setProcessed(0);
        log.setIdempotencyKey(DigestUtils.md5Hex(channel.getCode() + (rawData == null ? "" : rawData)));

        try {
            mapper.insert(log);
            return log.getId();
        } catch (DuplicateKeyException e) {
            CallbackLogServiceImpl.log.info("回调幂等命中，跳过: channel={}, key={}", channel, log.getIdempotencyKey());
            return null;
        }
    }

    @Override
    public void markProcessed(Long logId, Integer parsedStatus) {
        if (logId == null) return;
        CallbackLog patch = new CallbackLog();
        patch.setId(logId);
        patch.setProcessed(1);
        patch.setParsedStatus(parsedStatus);
        mapper.updateById(patch);
    }

    @Override
    public void markFailed(Long logId, String errorMsg) {
        if (logId == null) return;
        CallbackLog patch = new CallbackLog();
        patch.setId(logId);
        patch.setProcessed(2);
        patch.setErrorMsg(errorMsg);
        mapper.updateById(patch);
    }
}
