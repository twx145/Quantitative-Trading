package com.twx.platform.portfolio;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;

import java.util.Map;

/**
 * 投资组合接口，定义了账户需要具备的功能。
 * 它负责管理资金、持仓，并执行交易。
 */
public interface Portfolio {

    /**
     * 处理一笔交易订单。
     * @param order 包含交易详情的订单对象
     */
    void processOrder(Order order);

    /**
     * 根据最新的市场价格更新投资组合的总价值。
     * @param ticker       标的
     * @param currentPrice 最新价格
     */
    void updateValue(Ticker ticker, double currentPrice);

    /**
     * 获取当前可用现金。
     * @return 现金余额
     */
    double getCash();

    /**
     * 获取投资组合的总价值（现金 + 持仓市值）。
     * @return 总价值
     */
    double getTotalValue();

    /**
     * 打印投资组合的业绩总结报告。
     */
    void printSummary();

    Map<String, String> getSummaryMap();
}