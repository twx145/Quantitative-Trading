package com.twx.platform.strategy;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import org.ta4j.core.BarSeries;

/**
 * 策略接口，定义了所有交易策略必须实现的功能。
 * 策略是量化交易的 "大脑"。
 */
public interface Strategy {

    /**
     * 根据给定的历史数据和当前时间点，生成交易信号。
     *
     * @param index     当前 Bar 在 BarSeries 中的索引
     * @param series    完整的历史数据序列
     * @param portfolio 当前的投资组合，策略可以根据持仓或资金情况做决策
     * @return 交易信号 (BUY, SELL, HOLD)
     */
    TradeSignal generateSignal(int index, BarSeries series, Portfolio portfolio);

    /**
     * 获取策略的名称。
     * @return 策略名
     */
    String getName();
}