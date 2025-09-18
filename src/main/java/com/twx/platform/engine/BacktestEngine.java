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
    public void run(Strategy strategy, Portfolio portfolio, PositionSizer positionSizer) {
        System.out.printf("--- 开始回测 [%s] 策略 ---\n", strategy.getName());
        System.out.printf("标的: %s, 时间: %s to %s\n", ticker, startDate, endDate);

        // 1. 获取完整的历史数据
        BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, timeFrame);
        if (series.isEmpty()) {
            System.out.println("数据为空，无法回测！");
            return;
        }

        // 2. 核心回测循环：遍历每一根K线
        for (int i = 0; i < series.getBarCount(); i++) {
            // 2.1 策略根据当前索引生成信号 (优化点)
            TradeSignal signal = strategy.generateSignal(i, series, portfolio);

            // 2.2 如果有买卖信号，则创建并处理订单
            if (signal != TradeSignal.HOLD) {
                double price = series.getBar(i).getClosePrice().doubleValue();

                // 2.3 使用仓位管理器计算交易数量 (优化点)
                double quantity = positionSizer.calculateQuantity(price, portfolio);

                if (quantity > 0) {
                    Order order = new Order(ticker, signal, quantity, price, series.getBar(i).getEndTime());
                    // 2.4 投资组合处理订单
                    portfolio.processOrder(order);
                }
            }

            // 2.5 每日结束时，用收盘价更新投资组合的总价值
            portfolio.updateValue(ticker, series.getBar(i).getClosePrice().doubleValue());
        }

        System.out.println("--- 回测结束 ---");
        portfolio.printSummary();
    }
}