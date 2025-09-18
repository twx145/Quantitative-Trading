package com.twx.platform.portfolio.impl;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
    public void processOrder(Order order) {
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
                System.out.printf("%s: 买入 %s, 数量 %.2f, 价格 %.2f, 手续费 %.2f\n",
                        order.timestamp().toLocalDate(), symbol, quantity, price, commission);
            } else {
                System.out.printf("%s: 现金不足 (需要 %.2f, 只有 %.2f)，无法买入！\n",
                        order.timestamp().toLocalDate(), totalCost, cash);
            }
        } else if (order.signal() == TradeSignal.SELL) {
            if (holdings.getOrDefault(symbol, 0.0) >= quantity) {
                double totalProceeds = grossValue - commission; // 卖出总收入
                cash += totalProceeds;
                holdings.put(symbol, holdings.get(symbol) - quantity);
                System.out.printf("%s: 卖出 %s, 数量 %.2f, 价格 %.2f, 手续费 %.2f\n",
                        order.timestamp().toLocalDate(), symbol, quantity, price, commission);
            } else {
                System.out.printf("%s: 持仓不足 (需要 %.2f, 只有 %.2f)，无法卖出！\n",
                        order.timestamp().toLocalDate(), quantity, holdings.getOrDefault(symbol, 0.0));
            }
        }
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

    @Override
    public void printSummary() {
        System.out.println("\n--- 投资组合总结 ---");
        System.out.printf("初始资金: %,.2f\n", initialCash);
        System.out.printf("最终总价值: %,.2f\n", totalValue);
        double returnRate = (totalValue - initialCash) / initialCash;
        System.out.printf("总收益率: %.2f%%\n", returnRate * 100);
        System.out.printf("剩余现金: %,.2f\n", cash);
        System.out.println("最终持仓:");
        if (holdings.values().stream().allMatch(q -> q == 0)) {
            System.out.println("  (空仓)");
        } else {
            holdings.forEach((symbol, quantity) -> {
                if (quantity > 0.0001) { // 避免打印极小的浮点数
                    System.out.printf("  %s: %.2f 股\n", symbol, quantity);
                }
            });
        }
        System.out.println("--------------------\n");
    }

    @Override
    public Map<String, String> getSummaryMap() {
        Map<String, String> summary = new LinkedHashMap<>();
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(); // 用于格式化货币
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMinimumFractionDigits(2);

        summary.put("初始资金", currencyFormat.format(initialCash));
        summary.put("最终总价值", currencyFormat.format(totalValue));
        double returnRate = (initialCash == 0) ? 0 : (totalValue - initialCash) / initialCash;
        summary.put("总收益率", percentFormat.format(returnRate));
        summary.put("剩余现金", currencyFormat.format(cash));

        // 添加持仓信息
        holdings.forEach((symbol, quantity) -> {
            if (quantity > 0.0001) {
                summary.put("持仓: " + symbol, String.format("%.2f 股", quantity));
            }
        });

        return summary;
    }
}