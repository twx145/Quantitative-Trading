package com.twx.platform.strategy.impl;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class BollingerBandsStrategy implements Strategy {
    private final String name;
    private final int period;
    private final CrossedDownIndicatorRule buyingRule;
    private final CrossedUpIndicatorRule sellingRule;

    /**
     * @param series      完整的历史数据序列
     * @param period      计算周期 (e.g., 20)
     * @param k           标准差倍数 (e.g., 2.0)
     */
    public BollingerBandsStrategy(BarSeries series, int period, double k) {
        this.period = period;
        this.name = String.format("Bollinger Bands(%d, %.1f) Strategy", period, k);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, period);

        BollingerBandsMiddleIndicator middleBand = new BollingerBandsMiddleIndicator(sma);

        // 【修正】使用 series.numOf(k) 来将 double 转换为 ta4j 的 Num 类型
        BollingerBandsLowerIndicator lowerBand = new BollingerBandsLowerIndicator(middleBand, sd, series.numOf(k));
        BollingerBandsUpperIndicator upperBand = new BollingerBandsUpperIndicator(middleBand, sd, series.numOf(k));

        // 买入规则: 收盘价从上向下跌破布林带下轨 (预期反弹)
        this.buyingRule = new CrossedDownIndicatorRule(closePrice, lowerBand);
        // 卖出规则: 收盘价从下向上突破布林带上轨 (预期回调)
        this.sellingRule = new CrossedUpIndicatorRule(closePrice, upperBand);
    }


    @Override
    public TradeSignal generateSignal(int index, BarSeries series, Portfolio portfolio) {
        if (index < this.period) {
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