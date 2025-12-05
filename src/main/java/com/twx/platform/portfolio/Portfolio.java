// 文件路径: com/twx/platform/portfolio/Portfolio.java
package com.twx.platform.portfolio;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import org.ta4j.core.BarSeries;

/**
 * 投资组合接口，定义了账户需要具备的功能。
 * 它负责管理资金、持仓，并执行交易。
 */
public interface Portfolio {

    /**
     * 处理一笔交易订单。
     * @param order 包含交易详情的订单对象
     * @return 订单是否成功执行
     */
    boolean processOrder(Order order);

    /**
     * 根据最新的市场价格更新投资组合的总价值，并记录历史。
     * @param ticker       标的
     * @param currentPrice 最新价格
     * @param series       K线序列，用于获取当前时间点
     * @param index        当前的K线索引
     */
    void updateValue(Ticker ticker, double currentPrice, BarSeries series, int index);

    /**
     * 获取当前可用现金。
     * @return 现金余额
     */
    double getCash();

    /**
     * 获取投资组合的最终总价值（现金 + 持仓市值）。
     * @return 总价值
     */
    double getTotalValue();

    /**
     * 获取初始资金
     * @return 初始资金
     */
    double getInitialCash();

    /**
     * 【新增】获取账户净值的历史序列。
     * 这个序列是计算最大回撤、夏普比率等高级指标的关键。
     * @return 一个包含账户每日净值的 BarSeries
     */
    BarSeries getValueHistory();

    /**
     * 获取简单的、基于交易的统计摘要。
     * @return 摘要字符串
     */
    String getSummary();
}