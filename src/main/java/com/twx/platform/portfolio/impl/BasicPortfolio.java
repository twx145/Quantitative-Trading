package com.twx.platform.portfolio.impl;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;

import java.text.NumberFormat;
import java.util.*;

/**
 * 投资组合的基本实现。
 * 增加了手续费功能，使回测更真实。
 */
public class BasicPortfolio implements Portfolio {
    private final double initialCash;
    private final double commissionRate; // 新增：手续费率 (e.g., 0.0003 for 0.03%)
    private double cash;
    private final Map<String, Double> holdings; // Key: Ticker Symbol, Value: Quantity
    private double totalValue;

    /**
     * 构造函数
     * @param initialCash    初始资金
     * @param commissionRate 交易手续费率（双边收取）
     */
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
        double grossValue = quantity * price; // 交易总额（毛值）
        double commission = grossValue * commissionRate; // 手续费

        if (order.signal() == TradeSignal.BUY) {
            double totalCost = grossValue + commission; // 买入总花费
            if (cash >= totalCost) {
                cash -= totalCost;
                holdings.put(symbol, holdings.getOrDefault(symbol, 0.0) + quantity);
                return true;
            } else {
                return false;
            }
        } else if (order.signal() == TradeSignal.SELL) {
            if (holdings.getOrDefault(symbol, 0.0) >= quantity) {
                double totalProceeds = grossValue - commission; // 卖出总收入
                cash += totalProceeds;
                holdings.put(symbol, holdings.get(symbol) - quantity);
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    public void updateValue(Ticker ticker, double currentPrice) {
        // 计算所有持仓的总市值
        double holdingsValue = holdings.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * (entry.getKey().equals(ticker.symbol()) ? currentPrice : 0))
                .sum(); // 假设只有一个持仓，更复杂的需要传入一个价格Map
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
     * 【新增】生成并返回业绩总结报告字符串。
     * @return 格式化后的业绩报告
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("\n--- 投资组合总结 ---\n");
        summary.append(String.format("初始资金: %,.2f\n", initialCash));
        summary.append(String.format("最终总价值: %,.2f\n", totalValue));
        double returnRate = (initialCash == 0) ? 0 : (totalValue - initialCash) / initialCash;
        summary.append(String.format("总收益率: %.2f%%\n", returnRate * 100));
        summary.append(String.format("剩余现金: %,.2f\n", cash));
        summary.append("最终持仓:\n");

        boolean hasHoldings = holdings.values().stream().anyMatch(q -> q > 0.0001);
        if (!hasHoldings) {
            summary.append("  (空仓)\n");
        } else {
            holdings.forEach((symbol, quantity) -> {
                if (quantity > 0.0001) {
                    summary.append(String.format("  %s: %.2f 股\n", symbol, quantity));
                }
            });
        }
        summary.append("--------------------\n");
        return summary.toString();
    }

}