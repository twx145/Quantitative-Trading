package com.twx.platform.strategy.impl;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

/**
 * 双均线交叉策略的具体实现。
 * 当短期移动平均线 (SMA) 上穿长期移动平均线时，产生买入信号。
 * 当短期移动平均线下穿长期移动平均线时，产生卖出信号。
 */
public class MovingAverageCrossStrategy implements Strategy {
    private final int shortSmaPeriod;
    private final int longSmaPeriod;

    // 关键优化：指标和规则在构造时就初始化，而不是在每次信号生成时。
    private final CrossedUpIndicatorRule buyingRule;
    private final CrossedDownIndicatorRule sellingRule;

    /**
     * 构造函数，需要传入完整的 BarSeries 来初始化技术指标。
     * @param series         完整的历史数据序列
     * @param shortSmaPeriod 短期均线的周期 (e.g., 5, 10)
     * @param longSmaPeriod  长期均线的周期 (e.g., 20, 30)
     */
    public MovingAverageCrossStrategy(BarSeries series, int shortSmaPeriod, int longSmaPeriod) {
        if (shortSmaPeriod >= longSmaPeriod) {
            throw new IllegalArgumentException("短期均线周期必须小于长期均线周期");
        }
        this.shortSmaPeriod = shortSmaPeriod;
        this.longSmaPeriod = longSmaPeriod;

        // 1. 创建收盘价指标
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 2. 创建短期和长期SMA指标
        Indicator<Num> shortSma = new SMAIndicator(closePrice, shortSmaPeriod);
        Indicator<Num> longSma = new SMAIndicator(closePrice, longSmaPeriod);

        // 3. 创建买入规则 (金叉) 和卖出规则 (死叉)
        this.buyingRule = new CrossedUpIndicatorRule(shortSma, longSma);
        this.sellingRule = new CrossedDownIndicatorRule(shortSma, longSma);
    }

    /**
     * 优化后的信号生成方法。
     * 直接使用传入的 index 进行判断，避免了创建子序列的开销。
     */
    @Override
    public TradeSignal generateSignal(int index, BarSeries series, Portfolio portfolio) {
        // 确保有足够的数据来计算最长的均线
        if (index < longSmaPeriod) {
            return TradeSignal.HOLD;
        }

        // 检查买入和卖出规则在当前 index 是否满足
        if (buyingRule.isSatisfied(index)) {
            return TradeSignal.BUY;
        } else if (sellingRule.isSatisfied(index)) {
            return TradeSignal.SELL;
        }

        return TradeSignal.HOLD;
    }

    @Override
    public String getName() {
        return String.format("SMA(%d)/SMA(%d) Cross Strategy", shortSmaPeriod, longSmaPeriod);
    }
}