package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

public class MacdTechnique implements AnalysisTechnique {
    private final int shortPeriod;
    private final int longPeriod;
    private final int signalPeriod;

    public MacdTechnique(int shortPeriod, int longPeriod, int signalPeriod) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
        this.signalPeriod = signalPeriod;
    }

    @Override
    public List<XYDataset> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, shortPeriod, longPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);

        // 为每个指标创建一个 JFreeChart 的 XYSeries
        XYSeries macdLine = createSeries("MACD(" + shortPeriod + "," + longPeriod + ")", series, macd);
        XYSeries signalLine = createSeries("Signal(" + signalPeriod + ")", series, signal);

        // 将所有相关的 XYSeries 添加到一个数据集中
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(macdLine);
        dataset.addSeries(signalLine);

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
        return "MACD";
    }
}