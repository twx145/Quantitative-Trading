package com.twx.platform.position.impl;

import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.position.PositionSizer;

/**
 * 固定数量的仓位管理器。
 * 每次交易固定的股数。
 */
public class FixedQuantityPositionSizer implements PositionSizer {
    private final double quantity;

    public FixedQuantityPositionSizer(double quantity) {
        this.quantity = quantity;
    }

    @Override
    public double calculateQuantity(double price, Portfolio portfolio) {
        return this.quantity;
    }
}