package com.twx.platform.common;

import java.time.ZonedDateTime;

// 订单类，记录一笔交易的详细信息
public class Order {
    private final Ticker ticker;
    private final TradeSignal signal;
    private final double quantity;
    private final double price;
    private final ZonedDateTime timestamp; // 交易时间

    public Order(Ticker ticker, TradeSignal signal, double quantity, double price, ZonedDateTime timestamp) {
        this.ticker = ticker;
        this.signal = signal;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    // Getters ...
    public Ticker getTicker() { return ticker; }
    public TradeSignal getSignal() { return signal; }
    public double getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public ZonedDateTime getTimestamp() { return timestamp; }
}