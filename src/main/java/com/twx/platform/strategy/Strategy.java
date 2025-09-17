package com.twx.platform.strategy;

import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import org.ta4j.core.BarSeries;

// 策略接口，定义了所有策略必须有的功能
public interface Strategy {
    TradeSignal generateSignal(BarSeries series, Portfolio portfolio);
    String getName();
}