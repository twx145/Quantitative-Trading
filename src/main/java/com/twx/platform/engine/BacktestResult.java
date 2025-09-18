package com.twx.platform.engine;

import com.twx.platform.common.Order;
import com.twx.platform.portfolio.Portfolio;
import org.ta4j.core.BarSeries;
import java.util.List;

/**
 * 封装一次完整回测的所有结果。
 * 这个对象将从回测引擎传递给UI控制器，用于更新界面。
 *
 * @param series         用于回测的K线数据序列
 * @param executedOrders 所有被执行的交易订单列表
 * @param finalPortfolio 包含最终状态（资金、持仓、业绩）的投资组合
 */
public record BacktestResult(
        BarSeries series,
        List<Order> executedOrders,
        Portfolio finalPortfolio
) {}