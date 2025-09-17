package com.twx.platform.strategy.impl;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

// 双均线交叉策略的具体实现
public class MovingAverageCrossStrategy implements Strategy {
    private final int shortSmaPeriod;
    private final int longSmaPeriod;
    private final CrossedUpIndicatorRule buyingRule;
    private final CrossedDownIndicatorRule sellingRule;

    public MovingAverageCrossStrategy(BarSeries series, int shortSmaPeriod, int longSmaPeriod) {
        this.shortSmaPeriod = shortSmaPeriod;
        this.longSmaPeriod = longSmaPeriod;

        // 初始化指标和规则
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, shortSmaPeriod);
        SMAIndicator longSma = new SMAIndicator(closePrice, longSmaPeriod);
        this.buyingRule = new CrossedUpIndicatorRule(shortSma, longSma);
        this.sellingRule = new CrossedDownIndicatorRule(shortSma, longSma);
    }

    @Override
    public TradeSignal generateSignal(BarSeries series, Portfolio portfolio) {
        int endIndex = series.getEndIndex();
        // 确保有足够的数据来计算指标
        if (endIndex < longSmaPeriod) {
            return TradeSignal.HOLD;
        }

        if (buyingRule.isSatisfied(endIndex)) {
            return TradeSignal.BUY;
        } else if (sellingRule.isSatisfied(endIndex)) {
            return TradeSignal.SELL;
        }

        return TradeSignal.HOLD;
    }

    @Override
    public String getName() {
        return String.format("SMA(%d) / SMA(%d) Cross Strategy", shortSmaPeriod, longSmaPeriod);
    }
}