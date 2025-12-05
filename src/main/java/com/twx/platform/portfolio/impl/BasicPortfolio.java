// 文件路径: com/twx/platform/portfolio/impl/BasicPortfolio.java
package com.twx.platform.portfolio.impl;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资组合的基本实现。
 * 【增强版】增加了详细的性能指标追踪和报告。
 */
public class BasicPortfolio implements Portfolio {
    private final double initialCash;
    private final double commissionRate;
    private double cash;
    private final Map<String, Double> holdings; // Key: Ticker Symbol, Value: Quantity
    private double totalValue;

    private final List<Double> tradeProfits = new ArrayList<>();
    private final Map<String, Double> averageCost = new HashMap<>();

    // --- 【新增】用于记录账户每日净值的序列 ---
    private final BarSeries valueHistory;

    public BasicPortfolio(double initialCash, double commissionRate) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.commissionRate = commissionRate;
        this.holdings = new HashMap<>();
        this.totalValue = initialCash;
        // --- 【新增】初始化净值序列 ---
        this.valueHistory = new BaseBarSeries("PortfolioValue", DoubleNum::valueOf);
    }

    @Override
    public boolean processOrder(Order order) {
        String symbol = order.ticker().symbol();
        double quantity = order.quantity();
        double price = order.price();
        double grossValue = quantity * price;
        double commission = grossValue * commissionRate;

        if (order.signal() == TradeSignal.BUY) {
            double totalCost = grossValue + commission;
            if (cash >= totalCost) {
                cash -= totalCost;
                double currentQuantity = holdings.getOrDefault(symbol, 0.0);
                double currentAvgCost = averageCost.getOrDefault(symbol, 0.0);
                double newTotalQuantity = currentQuantity + quantity;
                double newAvgCost = ((currentAvgCost * currentQuantity) + (price * quantity)) / newTotalQuantity;
                holdings.put(symbol, newTotalQuantity);
                averageCost.put(symbol, newAvgCost);
                return true;
            } else {
                return false;
            }
        } else if (order.signal() == TradeSignal.SELL) {
            if (holdings.getOrDefault(symbol, 0.0) >= quantity) {
                double costBasis = averageCost.getOrDefault(symbol, 0.0) * quantity;
                double profit = grossValue - costBasis;
                tradeProfits.add(profit);
                double totalProceeds = grossValue - commission;
                cash += totalProceeds;
                holdings.put(symbol, holdings.get(symbol) - quantity);
                if (Math.abs(holdings.get(symbol)) < 0.0001) {
                    averageCost.remove(symbol);
                }
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * 【修改】更新总价值，并记录到历史序列中。
     */
    @Override
    public void updateValue(Ticker ticker, double currentPrice, BarSeries series, int index) {
        double holdingsValue = holdings.getOrDefault(ticker.symbol(), 0.0) * currentPrice;
        this.totalValue = this.cash + holdingsValue;

        // --- 【新增】将当前的总价值作为一根K线记录下来 ---
        // 使用价格序列的Bar来获取时间戳，确保对齐
        Bar underlyingBar = series.getBar(index);
        // 创建一个只包含收盘价（即我们的总价值）的Bar
        Bar valueBar = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(underlyingBar.getEndTime())
                .openPrice(this.totalValue)
                .highPrice(this.totalValue)
                .lowPrice(this.totalValue)
                .closePrice(this.totalValue)
                .volume((double) 0)
                .build();
        this.valueHistory.addBar(valueBar);
    }

    @Override
    public double getCash() {
        return cash;
    }

    @Override
    public double getTotalValue() {
        return totalValue;
    }

    @Override
    public double getInitialCash() {
        return initialCash;
    }

    /**
     * 【新增】实现获取净值历史的接口方法。
     */
    @Override
    public BarSeries getValueHistory() {
        return valueHistory;
    }

    @Override
    public String getSummary() {
        // 这个方法现在只负责提供基于交易的统计，更复杂的指标移至 PerformanceAnalyzer
        double totalProfit = tradeProfits.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).sum();
        double totalLoss = tradeProfits.stream().filter(p -> p < 0).mapToDouble(Double::doubleValue).sum();
        long winningTrades = tradeProfits.stream().filter(p -> p > 0).count();
        long losingTrades = tradeProfits.stream().filter(p -> p < 0).count();
        int totalTrades = tradeProfits.size();

        double winRate = (totalTrades == 0) ? 0 : (double) winningTrades / totalTrades;
        double avgProfit = (winningTrades == 0) ? 0 : totalProfit / winningTrades;
        double avgLoss = (losingTrades == 0) ? 0 : totalLoss / losingTrades;
        double profitLossRatio = (avgLoss == 0) ? Double.POSITIVE_INFINITY : -avgProfit / avgLoss;
        double profitFactor = (totalLoss == 0) ? Double.POSITIVE_INFINITY : -totalProfit / totalLoss;

        StringBuilder summary = new StringBuilder();
        summary.append("## 交易统计\n");
        summary.append(String.format("总交易次数: %d\n", totalTrades));
        summary.append(String.format("盈利交易: %d\n", winningTrades));
        summary.append(String.format("亏损交易: %d\n", losingTrades));
        summary.append(String.format("胜率: %.2f%%\n", winRate * 100));
        summary.append(String.format("利润因子 (Profit Factor): %.2f\n", profitFactor));
        summary.append(String.format("平均盈亏比 (P/L Ratio): %.2f\n\n", profitLossRatio));

        summary.append("## 最终状态\n");
        summary.append(String.format("剩余现金: %,.2f\n", cash));
        summary.append("最终持仓:\n");
        boolean hasHoldings = holdings.values().stream().anyMatch(q -> q > 0.0001);
        if (!hasHoldings) {
            summary.append("  (空仓)\n");
        } else {
            holdings.forEach((symbol, quantity) -> {
                if (quantity > 0.0001) {
                    summary.append(String.format("  %s: %.2f 股 (平均成本: %.2f)\n", symbol, quantity, averageCost.get(symbol)));
                }
            });
        }
        return summary.toString();
    }
}