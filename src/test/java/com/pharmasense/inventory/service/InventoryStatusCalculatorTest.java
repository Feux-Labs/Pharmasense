package com.pharmasense.inventory.service;

import com.pharmasense.inventory.enums.ExpiryStatusEnum;
import com.pharmasense.inventory.enums.StockLevelStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryStatusCalculatorTest {

    private final InventoryStatusCalculator calculator = new InventoryStatusCalculator();

    @Test
    void zeroQuantityIsOutOfStock() {
        assertThat(calculator.computeStockLevelStatus(0, 10)).isEqualTo(StockLevelStatusEnum.OUT_OF_STOCK);
    }

    @Test
    void quantityAtOrBelowThresholdIsLowStock() {
        assertThat(calculator.computeStockLevelStatus(10, 10)).isEqualTo(StockLevelStatusEnum.LOW_STOCK);
        assertThat(calculator.computeStockLevelStatus(3, 10)).isEqualTo(StockLevelStatusEnum.LOW_STOCK);
    }

    @Test
    void quantityAboveThresholdIsOk() {
        assertThat(calculator.computeStockLevelStatus(11, 10)).isEqualTo(StockLevelStatusEnum.OK);
    }

    @Test
    void nullExpiryDateIsNotApplicable() {
        assertThat(calculator.computeExpiryStatus(null, 90)).isEqualTo(ExpiryStatusEnum.NOT_APPLICABLE);
    }

    @Test
    void pastExpiryDateIsExpired() {
        assertThat(calculator.computeExpiryStatus(LocalDate.now().minusDays(1), 90)).isEqualTo(ExpiryStatusEnum.EXPIRED);
    }

    @Test
    void withinWarningWindowIsExpiringSoon() {
        assertThat(calculator.computeExpiryStatus(LocalDate.now().plusDays(30), 90)).isEqualTo(ExpiryStatusEnum.EXPIRING_SOON);
    }

    @Test
    void beyondWarningWindowIsFresh() {
        assertThat(calculator.computeExpiryStatus(LocalDate.now().plusDays(200), 90)).isEqualTo(ExpiryStatusEnum.FRESH);
    }

    @Test
    void exactlyOnTheWarningBoundaryIsExpiringSoon() {
        assertThat(calculator.computeExpiryStatus(LocalDate.now().plusDays(90), 90)).isEqualTo(ExpiryStatusEnum.EXPIRING_SOON);
    }
}
