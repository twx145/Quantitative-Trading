package com.twx.platform.strategy.impl;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class RsiStrategy implements Strategy {

    private final String name;
    private final int rsiPeriod;
    private final CrossedUpIndicatorRule buyingRule;
    private final CrossedDownIndicatorRule sellingRule;

    /**
     * @param series         完整的历史数据序列
     * @param rsiPeriod      RSI 计算周期 (e.g., 14)
     * @param lowerThreshold RSI 超卖阈值 (e.g., 30)
     * @param upperThreshold RSI 超买阈值 (e.g., 70)
     */
    public RsiStrategy(BarSeries series, int rsiPeriod, int lowerThreshold, int upperThreshold) {
        this.rsiPeriod = rsiPeriod;
        this.name = String.format("RSI(%d) [%d/%d] Strategy", rsiPeriod, lowerThreshold, upperThreshold);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, rsiPeriod);

        // 买入规则: RSI 从下向上穿过超卖线
        this.buyingRule = new CrossedUpIndicatorRule(rsi, lowerThreshold);
        // 卖出规则: RSI 从上向下穿过超买线
        this.sellingRule = new CrossedDownIndicatorRule(rsi, upperThreshold);
    }

    @Override
    public TradeSignal generateSignal(int index, BarSeries series, Portfolio portfolio) {
        // 确保有足够的数据来计算 RSI
        if (index < this.rsiPeriod) {
            return TradeSignal.HOLD;
        }

        if (buyingRule.isSatisfied(index)) {
            return TradeSignal.BUY;
        } else if (sellingRule.isSatisfied(index)) {
            return TradeSignal.SELL;
        }

        return TradeSignal.HOLD;
    }

    @Override
    public String getName() {
        return this.name;
    }
}