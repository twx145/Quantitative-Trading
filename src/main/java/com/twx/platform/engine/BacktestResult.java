package com.twx.platform.engine;

import com.twx.platform.common.Order;
import com.twx.platform.portfolio.Portfolio;
import org.ta4j.core.BarSeries;
import java.util.List;
import java.util.Map;

/**
 * 封装一次完整回测的所有结果。
 * 优化后，它将直接携带计算好的指标数据，供UI使用。
 *
 * @param series          K线数据序列
 * @param executedOrders  所有已执行的交易订单列表
 * @param finalPortfolio  最终的投资组合状态
 * @param indicatorsData  一个Map，Key是指标名称(e.g., "SMA10"), Value是该指标在每个时间点的值
 */
public record BacktestResult(
        BarSeries series,
        List<Order> executedOrders,
        Portfolio finalPortfolio,
        Map<String, Map<Integer, Double>> indicatorsData
) {}