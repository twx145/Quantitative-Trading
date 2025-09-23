package com.twx.platform.strategy.impl;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class MACDStrategy implements Strategy {

    private final String name;
    private final int longBarCount;
    private final CrossedUpIndicatorRule buyingRule;
    private final CrossedDownIndicatorRule sellingRule;

    /**
     * @param series         完整的历史数据序列
     * @param shortBarCount  快线EMA周期 (e.g., 12)
     * @param longBarCount   慢线EMA周期 (e.g., 26)
     * @param signalBarCount 信号线DEA周期 (e.g., 9)
     */
    public MACDStrategy(BarSeries series, int shortBarCount, int longBarCount, int signalBarCount) {
        this.longBarCount = longBarCount;
        this.name = String.format("MACD(%d, %d, %d) Strategy", shortBarCount, longBarCount, signalBarCount);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, shortBarCount, longBarCount);
        EMAIndicator signalLine = new EMAIndicator(macd, signalBarCount);

        // 买入规则: MACD 快线上穿 DEA 信号线
        this.buyingRule = new CrossedUpIndicatorRule(macd, signalLine);
        // 卖出规则: MACD 快线下穿 DEA 信号线
        this.sellingRule = new CrossedDownIndicatorRule(macd, signalLine);
    }

    @Override
    public TradeSignal generateSignal(int index, BarSeries series, Portfolio portfolio) {
        // 确保有足够的数据来计算最长的EMA
        if (index < this.longBarCount) {
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