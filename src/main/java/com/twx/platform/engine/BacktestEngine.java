package com.twx.platform.engine;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.data.DataProvider;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;

// 回测引擎，负责模拟交易流程
public class BacktestEngine {
    private final DataProvider dataProvider;
    private final Ticker ticker;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public BacktestEngine(DataProvider dataProvider, Ticker ticker, LocalDate startDate, LocalDate endDate) {
        this.dataProvider = dataProvider;
        this.ticker = ticker;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void run(Strategy strategy, Portfolio portfolio) {
        System.out.printf("--- 开始回测 [%s] 策略 ---\n", strategy.getName());
        System.out.printf("标的: %s, 时间: %s to %s\n", ticker, startDate, endDate);

        BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, null);
        if (series.isEmpty()) {
            System.out.println("数据为空，无法回测！");
            return;
        }

        for (int i = 0; i < series.getBarCount(); i++) {
            // 1. 策略根据当前及之前的历史数据生成信号
            TradeSignal signal = strategy.generateSignal(series.getSubSeries(0, i + 1), portfolio);

            // 2. 如果有买卖信号，则创建订单
            if (signal != TradeSignal.HOLD) {
                double price = series.getBar(i).getClosePrice().doubleValue();
                Order order = new Order(ticker, signal, 10, price, series.getBar(i).getEndTime()); // 每次模拟交易10股

                // 3. 投资组合处理订单
                portfolio.processOrder(order);
            }

            // 4. 每日结束时，用收盘价更新投资组合的总价值
            portfolio.updateValue(ticker, series.getBar(i).getClosePrice().doubleValue());
        }

        System.out.println("--- 回测结束 ---");
        portfolio.printSummary();
    }
}
