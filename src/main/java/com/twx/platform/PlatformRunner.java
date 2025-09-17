package com.twx.platform;

import com.twx.platform.common.Ticker;
import com.twx.platform.data.DataProvider;
import com.twx.platform.data.impl.YahooDataProvider;
import com.twx.platform.engine.BacktestEngine;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.portfolio.impl.BasicPortfolio;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;

// 程序的总入口，负责组装和启动所有模块
public class PlatformRunner {
    public static void main(String[] args) {
        // --- 1. 初始化配置 ---
        Ticker tickerToTest = new Ticker("AAPL"); // 测试苹果公司
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 1);
        double initialCash = 100000.00; // 初始资金10万

        // --- 2. 实例化核心模块 ---
        DataProvider dataProvider = new YahooDataProvider();
        BacktestEngine engine = new BacktestEngine(dataProvider, tickerToTest, startDate, endDate);
        Portfolio portfolio = new BasicPortfolio(initialCash);

        // --- 3. 准备策略 ---
        // 注意：策略需要用到完整的数据序列来初始化指标，所以我们先获取一次数据
        BarSeries series = dataProvider.getHistoricalData(tickerToTest, startDate, endDate, null);
        Strategy smaStrategy = new MovingAverageCrossStrategy(series, 10, 30); // 10日均线和30日均线

        // --- 4. 运行回测 ---
        engine.run(smaStrategy, portfolio);
    }
}