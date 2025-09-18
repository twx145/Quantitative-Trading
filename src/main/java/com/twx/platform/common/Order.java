package com.twx.platform.common;

import java.time.ZonedDateTime;

/**
 * @param timestamp 交易时间
 */ // 订单类，记录一笔交易的详细信息
public record Order(Ticker ticker, TradeSignal signal, double quantity, double price, ZonedDateTime timestamp) {
}