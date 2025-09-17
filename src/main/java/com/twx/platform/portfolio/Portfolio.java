package com.twx.platform.portfolio;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;

// 投资组合接口，定义了账户需要具备的功能
public interface Portfolio {
    void processOrder(Order order);
    void updateValue(Ticker ticker, double currentPrice);
    double getCash();
    double getTotalValue();
    void printSummary();
}