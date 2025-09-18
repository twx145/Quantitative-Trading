package com.twx.platform.engine;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.data.DataProvider;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.position.PositionSizer;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        Map<String, Map<Integer, Double>> indicatorsData = new HashMap<>();

        if (series.isEmpty()) {
            System.out.println("数据为空，无法回测！");
            return new BacktestResult(series, executedOrders, portfolio, indicatorsData);
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

        // --- 新增：在这里计算指标数据，以便UI显示 ---
        if (strategy instanceof MovingAverageCrossStrategy macStrategy) {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            // 注意：这些周期应该与策略中的一致，未来可以从策略对象中获取
            int shortPeriod = 10;
            int longPeriod = 30;
            SMAIndicator shortSma = new SMAIndicator(closePrice, shortPeriod);
            SMAIndicator longSma = new SMAIndicator(closePrice, longPeriod);
            indicatorsData.put("SMA" + shortPeriod, indicatorToMap(shortSma));
            indicatorsData.put("SMA" + longPeriod, indicatorToMap(longSma));
        }

        return new BacktestResult(series, executedOrders, portfolio, indicatorsData);
    }

    /**
     * 辅助方法：将一个ta4j的Indicator转换为Map，方便后续使用。
     */
    private Map<Integer, Double> indicatorToMap(Indicator<Num> indicator) {
        return IntStream.range(0, indicator.getBarSeries().getBarCount())
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> indicator.getValue(i).doubleValue()));
    }
}