package com.twx.platform.engine;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.data.DataProvider;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.position.PositionSizer;
import com.twx.platform.strategy.Strategy;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测引擎，负责模拟交易流程。
 * 优化后，引擎还将负责计算策略中用到的技术指标，并将结果返回给UI。
 */
public class BacktestEngine {
    // ... 构造函数和成员变量保持不变 ...
    private final DataProvider dataProvider;
    private final Ticker ticker;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final TimeFrame timeFrame;

    public BacktestEngine(DataProvider dataProvider, Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        this.dataProvider = dataProvider;
        this.ticker = ticker;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timeFrame = timeFrame;
    }


    public BacktestResult run(Strategy strategy, Portfolio portfolio, PositionSizer positionSizer) {
        List<Order> executedOrders = new ArrayList<>();
        BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, timeFrame);

        if (series.isEmpty()) {
            System.out.println("数据为空，无法回测！");
            // 返回一个包含空序列的结果对象
            return new BacktestResult(series, executedOrders, portfolio);
        }

        // 核心循环
        for (int i = 0; i < series.getBarCount(); i++) {
            TradeSignal signal = strategy.generateSignal(i, series, portfolio);

            if (signal != TradeSignal.HOLD) {
                double price = series.getBar(i).getClosePrice().doubleValue();
                double quantity = positionSizer.calculateQuantity(price, portfolio);
                if (quantity > 0) {
                    Order order = new Order(ticker, signal, quantity, price, series.getBar(i).getEndTime());
                    portfolio.processOrder(order);
                    executedOrders.add(order);
                }
            }
            portfolio.updateValue(ticker, series.getBar(i).getClosePrice().doubleValue());
        }

        // --- 移除所有指标计算代码 ---
        // UI相关的计算逻辑已经移动到UIController中

        return new BacktestResult(series, executedOrders, portfolio);
    }
}