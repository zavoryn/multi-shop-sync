package com.github.multiplatform.sync.strategy;

import com.github.multiplatform.sync.common.enums.ChannelEnum;
import com.github.multiplatform.sync.common.exception.ChannelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 渠道策略工厂。
 * 利用 Spring 的依赖注入，在项目启动时将所有实现了 IPlatformProductStrategy 的 Bean
 * 自动注册到 Map<ChannelEnum, Strategy> 中，实现 O(1) 路由。
 *
 * 新增渠道无需修改此类 —— 只需创建新的 Strategy 实现类并加 @Component 注解即可。
 */
@Slf4j
@Component
public class PlatformStrategyFactory implements ApplicationContextAware {

    private Map<ChannelEnum, IPlatformProductStrategy> strategyMap = new HashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, IPlatformProductStrategy> beans = ctx.getBeansOfType(IPlatformProductStrategy.class);
        strategyMap = beans.values().stream()
                .collect(Collectors.toMap(IPlatformProductStrategy::getChannel, Function.identity()));

        log.info("渠道策略工厂初始化完成，已注册 {} 个渠道: {}",
                strategyMap.size(),
                strategyMap.keySet());
    }

    /**
     * 根据渠道枚举获取对应的策略实例。
     *
     * @param channel 渠道枚举
     * @return 对应的策略实现
     * @throws ChannelException 不支持的渠道时抛出
     */
    public IPlatformProductStrategy getStrategy(ChannelEnum channel) {
        IPlatformProductStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new ChannelException(channel.getCode(), "不支持的渠道或渠道未启用");
        }
        return strategy;
    }

    /** 查看当前已注册的所有渠道 */
    public Map<ChannelEnum, IPlatformProductStrategy> getAllStrategies() {
        return strategyMap;
    }
}
