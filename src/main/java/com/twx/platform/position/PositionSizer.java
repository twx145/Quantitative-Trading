package com.twx.platform.position;

import com.twx.platform.portfolio.Portfolio;

/**
 * 仓位管理器接口。
 * 负责根据当前情况计算应该交易的数量。
 */
public interface PositionSizer {

    /**
     * 计算给定价格和投资组合状态下的交易数量。
     * @param price     当前价格
     * @param portfolio 投资组合
     * @return 应该交易的数量
     */
    double calculateQuantity(double price, Portfolio portfolio);
}