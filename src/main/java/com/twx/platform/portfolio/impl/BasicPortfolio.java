package com.twx.platform.portfolio.impl;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;

import java.util.*;

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

    // --- 【新增】用于性能追踪的成员变量 ---
    /**
     * 记录每笔已完成交易（卖出时）的盈亏金额。
     */
    private final List<Double> tradeProfits = new ArrayList<>();
    /**
     * 记录每个持仓的平均成本，用于计算盈亏。
     * Key: Ticker Symbol, Value: Average Cost per Share
     */
    private final Map<String, Double> averageCost = new HashMap<>();


    public BasicPortfolio(double initialCash, double commissionRate) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.commissionRate = commissionRate;
        this.holdings = new HashMap<>();
        this.totalValue = initialCash;
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

                // --- 【修改】更新平均持仓成本 ---
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
                // --- 【修改】计算并记录交易盈亏 ---
                double costBasis = averageCost.getOrDefault(symbol, 0.0) * quantity;
                double profit = grossValue - costBasis;
                tradeProfits.add(profit); // 记录税前盈亏

                double totalProceeds = grossValue - commission;
                cash += totalProceeds;
                holdings.put(symbol, holdings.get(symbol) - quantity);

                // 如果全部卖出，清理成本记录
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

    @Override
    public void updateValue(Ticker ticker, double currentPrice) {
        double holdingsValue = holdings.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * (entry.getKey().equals(ticker.symbol()) ? currentPrice : 0))
                .sum();
        this.totalValue = this.cash + holdingsValue;
    }

    @Override
    public double getCash() {
        return cash;
    }

    @Override
    public double getTotalValue() {
        return totalValue;
    }

    /**
     * 【增强版】生成并返回详细的业绩总结报告字符串。
     * @return 格式化后的业绩报告
     */
    @Override
    public String getSummary() {
        // --- 核心计算逻辑 ---
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

        // --- 格式化输出 ---
        StringBuilder summary = new StringBuilder();
        summary.append("\n--- 回测业绩报告 ---\n\n");

        summary.append("## 核心表现\n");
        summary.append(String.format("初始资金: %,.2f\n", initialCash));
        summary.append(String.format("最终总价值: %,.2f\n", totalValue));
        double returnRate = (initialCash == 0) ? 0 : (totalValue - initialCash) / initialCash;
        summary.append(String.format("净利润: %,.2f\n", totalValue - initialCash));
        summary.append(String.format("总收益率: %.2f%%\n\n", returnRate * 100));

        summary.append("## 交易统计\n");
        summary.append(String.format("总交易次数: %d\n", totalTrades));
        summary.append(String.format("盈利交易: %d\n", winningTrades));
        summary.append(String.format("亏损交易: %d\n", losingTrades));
        summary.append(String.format("胜率: %.2f%%\n", winRate * 100));
        summary.append(String.format("利润因子: %.2f\n", profitFactor));
        summary.append(String.format("平均盈亏比: %.2f\n\n", profitLossRatio));

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
        summary.append("--------------------\n");
        return summary.toString();
    }
}