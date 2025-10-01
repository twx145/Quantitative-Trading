package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

public class MovingAverageTechnique implements AnalysisTechnique {

    private final int shortPeriod;
    private final int longPeriod;

    public MovingAverageTechnique(int shortPeriod, int longPeriod) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public List<XYDataset> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 计算指标
        SMAIndicator shortSma = new SMAIndicator(closePrice, shortPeriod);
        SMAIndicator longSma = new SMAIndicator(closePrice, longPeriod);

        // 创建 JFreeChart 序列
        XYSeries shortMaSeries = createSeries("SMA(" + shortPeriod + ")", series, shortSma);
        XYSeries longMaSeries = createSeries("SMA(" + longPeriod + ")", series, longSma);

        // 将序列添加到数据集中
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(shortMaSeries);
        dataset.addSeries(longMaSeries);

        return List.of(dataset);
    }

    /**
     * 辅助方法，将 ta4j 的 Indicator 转换为 JFreeChart 的 XYSeries。
     * @param name       序列名称
     * @param barSeries  时间序列数据
     * @param indicator  ta4j 指标
     * @return XYSeries   JFreeChart 的序列
     */
    private XYSeries createSeries(String name, BarSeries barSeries, Indicator<Num> indicator) {
        XYSeries series = new XYSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            // JFreeChart 的 DateAxis 需要毫秒级时间戳
            long timestamp = barSeries.getBar(i).getEndTime().toInstant().toEpochMilli();
            series.add(timestamp, indicator.getValue(i).doubleValue());
        }
        return series;
    }


    @Override
    public String getName() {
        return "Moving Averages";
    }
}