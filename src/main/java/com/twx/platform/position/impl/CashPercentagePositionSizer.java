package com.twx.platform.position.impl;

import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.position.PositionSizer;

/**
 * 现金百分比仓位管理器。
 * 每次使用一定比例的可用现金进行交易。
 */
public class CashPercentagePositionSizer implements PositionSizer {
    private final double percentage; // e.g., 0.9 for 90%

    /**
     * @param percentage 使用现金的百分比 (0 到 1.0)
     */
    public CashPercentagePositionSizer(double percentage) {
        if (percentage <= 0 || percentage > 1) {
            throw new IllegalArgumentException("百分比必须在 (0, 1] 之间");
        }
        this.percentage = percentage;
    }

    @Override
    public double calculateQuantity(double price, Portfolio portfolio) {
        if (price <= 0) {
            return 0;
        }
        double cashToUse = portfolio.getCash() * this.percentage;
        return Math.floor(cashToUse / price); // 向下取整，确保能买得起
    }
}