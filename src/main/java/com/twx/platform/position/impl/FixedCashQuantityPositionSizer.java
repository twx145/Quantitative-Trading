package com.twx.platform.position.impl;

import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.position.PositionSizer;

/**
 * 现金百分比仓位管理器。
 * 每次使用一定比例的可用现金进行交易。
 */
public class FixedCashQuantityPositionSizer implements PositionSizer {
    private final double amount;

    public FixedCashQuantityPositionSizer(double amount) {
        this.amount = amount;
    }

    @Override
    public double calculateQuantity(double price, Portfolio portfolio) {
        if (price <= 0) {
            return 0;
        }
        return Math.floor(this.amount / price);
    }
}