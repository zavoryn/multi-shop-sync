package com.github.multiplatform.sync.common.statemachine;

import com.github.multiplatform.sync.common.enums.ProductStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.multiplatform.sync.common.enums.ProductStatusEnum.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductStatusMachineTest {

    @ParameterizedTest(name = "legal: {0} -> {1}")
    @CsvSource({
            "DRAFT, WAIT_PLATFORM_AUDIT",
            "WAIT_PLATFORM_AUDIT, ON_SHELF",
            "WAIT_PLATFORM_AUDIT, AUDIT_REJECT",
            "WAIT_PLATFORM_AUDIT, OFF_SHELF",
            "ON_SHELF, OFF_SHELF",
            "OFF_SHELF, ON_SHELF",
            "OFF_SHELF, WAIT_PLATFORM_AUDIT",
            "AUDIT_REJECT, WAIT_PLATFORM_AUDIT",
            "AUDIT_REJECT, OFF_SHELF",
    })
    void legalTransitionShouldPass(ProductStatusEnum from, ProductStatusEnum to) {
        assertTrue(ProductStatusMachine.canTransition(from, to));
        assertDoesNotThrow(() -> ProductStatusMachine.assertCanTransition(from, to));
    }

    @ParameterizedTest(name = "illegal: {0} -> {1}")
    @CsvSource({
            "DRAFT, ON_SHELF",
            "DRAFT, AUDIT_REJECT",
            "OFF_SHELF, AUDIT_REJECT",
            "ON_SHELF, AUDIT_REJECT",
            "ON_SHELF, WAIT_PLATFORM_AUDIT",
            "ON_SHELF, DRAFT",
    })
    void illegalTransitionShouldThrow(ProductStatusEnum from, ProductStatusEnum to) {
        assertFalse(ProductStatusMachine.canTransition(from, to));
        assertThrows(IllegalStateException.class,
                () -> ProductStatusMachine.assertCanTransition(from, to));
    }

    @Test
    @DisplayName("same-state transition is idempotent")
    void sameStateIsIdempotent() {
        for (ProductStatusEnum s : ProductStatusEnum.values()) {
            assertTrue(ProductStatusMachine.canTransition(s, s), "same state should pass: " + s);
        }
    }

    @Test
    @DisplayName("null args return false without NPE")
    void nullArgsReturnFalse() {
        assertFalse(ProductStatusMachine.canTransition(null, ON_SHELF));
        assertFalse(ProductStatusMachine.canTransition(DRAFT, null));
        assertFalse(ProductStatusMachine.canTransition(null, null));
    }
}
