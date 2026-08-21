package com.pharmasense.inventory.service;

import com.pharmasense.inventory.enums.ExpiryStatusEnum;
import com.pharmasense.inventory.enums.StockLevelStatusEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Turns raw numbers into the "stamp" statuses the UI displays. Pure and
 * stateless on purpose - both {@link InventoryItemService} and the catalog/QR
 * scan endpoint need the exact same rules, and thresholds are per-pharmacy
 * (an item can override the pharmacy default), so this is the one place that
 * decides what "low" and "expiring soon" mean.
 */
@Component
public class InventoryStatusCalculator {

    public StockLevelStatusEnum computeStockLevelStatus(int totalQuantityOnHand, int effectiveLowStockThreshold) {
        if (totalQuantityOnHand <= 0) {
            return StockLevelStatusEnum.OUT_OF_STOCK;
        }
        if (totalQuantityOnHand <= effectiveLowStockThreshold) {
            return StockLevelStatusEnum.LOW_STOCK;
        }
        return StockLevelStatusEnum.OK;
    }

    public ExpiryStatusEnum computeExpiryStatus(LocalDate nearestExpiryDate, int expiryWarningDays) {
        if (nearestExpiryDate == null) {
            return ExpiryStatusEnum.NOT_APPLICABLE;
        }
        LocalDate today = LocalDate.now();
        if (nearestExpiryDate.isBefore(today)) {
            return ExpiryStatusEnum.EXPIRED;
        }
        if (!nearestExpiryDate.isAfter(today.plusDays(expiryWarningDays))) {
            return ExpiryStatusEnum.EXPIRING_SOON;
        }
        return ExpiryStatusEnum.FRESH;
    }
}
