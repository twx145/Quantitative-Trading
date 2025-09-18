package com.twx.platform.portfolio.impl;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.portfolio.Portfolio;

import java.util.HashMap;
import java.util.Map;

// 投资组合的基本实现
public class BasicPortfolio implements Portfolio {
    private double cash;
    private final double initialCash;
    private final Map<String, Double> holdings; // Key: Ticker Symbol, Value: Quantity
    private double totalValue;

    public BasicPortfolio(double initialCash) {
        this.initialCash = initialCash;
        this.cash = initialCash;
        this.holdings = new HashMap<>();
        this.totalValue = initialCash;
    }

    @Override
    public void processOrder(Order order) {
        String symbol = order.ticker().symbol();
        double quantity = order.quantity();
        double price = order.price();
        double cost = quantity * price;

        if (order.signal() == TradeSignal.BUY) {
            if (cash >= cost) {
                cash -= cost;
                holdings.put(symbol, holdings.getOrDefault(symbol, 0.0) + quantity);
                System.out.printf("%s: 买入 %s, 数量 %.2f, 价格 %.2f\n", order.timestamp().toLocalDate(), symbol, quantity, price);
            } else {
                System.out.println("现金不足，无法买入！");
            }
        } else if (order.signal() == TradeSignal.SELL) {
            if (holdings.getOrDefault(symbol, 0.0) >= quantity) {
                cash += cost;
                holdings.put(symbol, holdings.get(symbol) - quantity);
                System.out.printf("%s: 卖出 %s, 数量 %.2f, 价格 %.2f\n", order.timestamp().toLocalDate(), symbol, quantity, price);
            } else {
                System.out.println("持仓不足，无法卖出！");
            }
        }
    }

    @Override
    public void updateValue(Ticker ticker, double currentPrice) {
        double holdingsValue = holdings.getOrDefault(ticker.symbol(), 0.0) * currentPrice;
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
        System.out.printf("初始资金: %.2f\n", initialCash);
        System.out.printf("最终总价值: %.2f\n", totalValue);
        System.out.printf("总收益率: %.2f%%\n", ((totalValue - initialCash) / initialCash) * 100);
        System.out.printf("剩余现金: %.2f\n", cash);
        System.out.println("最终持仓:");
        holdings.forEach((symbol, quantity) -> {
            if (quantity > 0) {
                System.out.printf("  %s: %.2f 股\n", symbol, quantity);
            }
        });
        System.out.println("--------------------\n");
    }
}