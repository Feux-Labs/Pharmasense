package com.pharmasense.inventory.enums;

/** Computed on read from quantity-on-hand vs. threshold - never persisted. */
public enum StockLevelStatusEnum {
    OK,
    LOW_STOCK,
    OUT_OF_STOCK
}
