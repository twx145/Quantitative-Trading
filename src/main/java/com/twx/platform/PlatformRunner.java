package com.twx.platform;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import com.twx.platform.data.impl.SinaDataProvider;
import com.twx.platform.engine.BacktestEngine;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.portfolio.impl.BasicPortfolio;
import com.twx.platform.position.PositionSizer;
import com.twx.platform.position.impl.CashPercentagePositionSizer;
import com.twx.platform.position.impl.FixedQuantityPositionSizer;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;

/**
 * 程序的总入口，负责组装和启动所有模块。
 */
public class PlatformRunner {
    public static void main(String[] args) {
        // --- 1. 初始化核心配置 ---
        Ticker tickerToTest = new Ticker("sh600519"); // 测试贵州茅台 (A股代码需要加 sh/sz 前缀)
        LocalDate startDate = LocalDate.of(2022, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 12, 31);
        double initialCash = 100000.00; // 初始资金10万
        double commissionRate = 0.0003; // 手续费率设置为万分之三

        // --- 2. 实例化核心模块 ---
        // 可以轻松切换数据源
        DataProvider dataProvider = new SinaDataProvider();
        // DataProvider dataProvider = new YahooDataProvider(); // 如果要用雅虎

        BacktestEngine engine = new BacktestEngine(dataProvider, tickerToTest, startDate, endDate, TimeFrame.DAILY);

        // 投资组合现在需要传入手续费率
        Portfolio portfolio = new BasicPortfolio(initialCash, commissionRate);

        // 选择一个仓位管理器
        // 每次买入时，动用95%的可用现金
        PositionSizer positionSizer = new CashPercentagePositionSizer(0.95);
        // 或者每次固定买100股
        // PositionSizer positionSizer = new FixedQuantityPositionSizer(100);

        // --- 3. 准备策略 ---
        // 策略的初始化需要用到完整数据序列，这是ta4j指标计算的要求
        BarSeries series = dataProvider.getHistoricalData(tickerToTest, startDate, endDate, TimeFrame.DAILY);
        if (series.isEmpty()) {
            System.out.println("无法获取数据，程序终止。");
            return;
        }
        // 使用10日均线和30日均线
        Strategy smaStrategy = new MovingAverageCrossStrategy(series, 10, 30);

        // --- 4. 运行回测 ---
        engine.run(smaStrategy, portfolio, positionSizer);
    }
}