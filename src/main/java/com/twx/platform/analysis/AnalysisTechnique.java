package com.twx.platform.analysis;

import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;

import java.util.List;

/**
 * 分析技术接口
 * 定义了一个标准的分析方法，它接收K线数据并返回一个或多个可用于图表绘制的序列。
 */
public interface AnalysisTechnique {

    /**
     * 根据输入的BarSeries计算分析指标。
     * @param series K线数据序列
     * @return 一个包含一个或多个图表序列的列表。例如，布林带会返回上、中、下三条线。
     */
    List<XYChart.Series<String, Number>> calculate(BarSeries series);

    /**
     * 获取此分析技术的名称，用于图例等。
     * @return 名称字符串
     */
    String getName();
}