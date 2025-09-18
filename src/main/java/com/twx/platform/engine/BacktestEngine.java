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
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDate;

/**
 * 回测引擎，负责模拟交易流程，连接数据、策略、仓位和投资组合。
 * 这是整个回测系统的核心驱动。
 */
public class BacktestEngine {
    private final DataProvider dataProvider;
    private final Ticker ticker;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final TimeFrame timeFrame; // 新增

    /**
     * 构造函数
     * @param dataProvider 数据提供者
     * @param ticker       交易标的
     * @param startDate    回测开始日期
     * @param endDate      回测结束日期
     * @param timeFrame    时间周期
     */
    public BacktestEngine(DataProvider dataProvider, Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        this.dataProvider = dataProvider;
        this.ticker = ticker;
        this.startDate = startDate;
        this.endDate = endDate;
        this.timeFrame = timeFrame;
    }

    /**
     * 运行回测。
     * @param strategy      要测试的交易策略
     * @param portfolio     投资组合
     * @param positionSizer 仓位管理器
     */
    public BacktestResult run(Strategy strategy, Portfolio portfolio, PositionSizer positionSizer) {
        System.out.printf("--- 开始回测 [%s] 策略 ---\n", strategy.getName());
        System.out.printf("标的: %s, 时间: %s to %s\n", ticker, startDate, endDate);

        List<Order> executedOrders = new ArrayList<>(); // 用于记录所有交易
        BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, timeFrame);

        if (series.isEmpty()) {
            System.out.println("数据为空，无法回测！");
            return new BacktestResult(series, executedOrders, portfolio); // 返回空结果
        }

        for (int i = 0; i < series.getBarCount(); i++) {
            TradeSignal signal = strategy.generateSignal(i, series, portfolio);

            if (signal != TradeSignal.HOLD) {
                double price = series.getBar(i).getClosePrice().doubleValue();
                double quantity = positionSizer.calculateQuantity(price, portfolio);

                if (quantity > 0) {
                    Order order = new Order(ticker, signal, quantity, price, series.getBar(i).getEndTime());
                    portfolio.processOrder(order);
                    executedOrders.add(order); // 记录已执行的订单
                }
            }
            portfolio.updateValue(ticker, series.getBar(i).getClosePrice().doubleValue());
        }

        System.out.println("--- 回测结束 ---");
        // portfolio.printSummary(); // 不再在这里打印

        return new BacktestResult(series, executedOrders, portfolio); // 返回完整的结果对象
    }
}