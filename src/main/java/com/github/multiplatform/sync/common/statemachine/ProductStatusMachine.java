package com.github.multiplatform.sync.common.statemachine;

import com.github.multiplatform.sync.common.enums.ProductStatusEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 商品状态机：定义并校验合法的状态转移。
 *
 * 转移图：
 * <pre>
 *   DRAFT ────────────┐
 *                     ▼
 *   WAIT_PLATFORM_AUDIT ──→ ON_SHELF
 *           ▲    │
 *           │    └────────→ AUDIT_REJECT
 *           │                    │
 *           └────（修改重提）─────┘
 *
 *   ON_SHELF  ⇄  OFF_SHELF
 *   AUDIT_REJECT → OFF_SHELF（商家放弃）
 * </pre>
 *
 * 任何状态都可以无条件转回 DRAFT（数据修复用），由调用方决定是否绕过校验。
 *
 * 设计为无状态工具类（含静态转移表），不依赖 Spring 容器，便于在 service / unit test 直接使用。
 */
public final class ProductStatusMachine {

    private static final Map<ProductStatusEnum, Set<ProductStatusEnum>> TRANSITIONS;

    static {
        Map<ProductStatusEnum, Set<ProductStatusEnum>> m = new EnumMap<>(ProductStatusEnum.class);
        m.put(ProductStatusEnum.DRAFT,
                EnumSet.of(ProductStatusEnum.WAIT_PLATFORM_AUDIT));
        m.put(ProductStatusEnum.WAIT_PLATFORM_AUDIT,
                EnumSet.of(ProductStatusEnum.ON_SHELF,
                           ProductStatusEnum.AUDIT_REJECT,
                           ProductStatusEnum.OFF_SHELF));
        m.put(ProductStatusEnum.ON_SHELF,
                EnumSet.of(ProductStatusEnum.OFF_SHELF));
        m.put(ProductStatusEnum.AUDIT_REJECT,
                EnumSet.of(ProductStatusEnum.WAIT_PLATFORM_AUDIT,
                           ProductStatusEnum.OFF_SHELF));
        m.put(ProductStatusEnum.OFF_SHELF,
                EnumSet.of(ProductStatusEnum.ON_SHELF,
                           ProductStatusEnum.WAIT_PLATFORM_AUDIT));
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    private ProductStatusMachine() {}

    /**
     * 检查状态转移是否合法。
     * 同状态 → 同状态返回 true（幂等场景，回调可能多次到达同一终态）。
     */
    public static boolean canTransition(ProductStatusEnum from, ProductStatusEnum to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        Set<ProductStatusEnum> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 强校验版本，非法转移抛 IllegalStateException。
     * 调用方应在收到平台回调或主动调用 changeStatus 前调用。
     */
    public static void assertCanTransition(ProductStatusEnum from, ProductStatusEnum to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "非法状态转移: " + from + " → " + to);
        }
    }
}
