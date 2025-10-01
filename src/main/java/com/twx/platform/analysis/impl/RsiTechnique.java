package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

public class RsiTechnique implements AnalysisTechnique {
    private final int period;

    public RsiTechnique(int period) {
        this.period = period;
    }

    @Override
    public List<XYDataset> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);

        // 创建 JFreeChart 序列
        XYSeries rsiSeries = createSeries("RSI(" + period + ")", series, rsi);

        // 将序列添加到数据集中
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(rsiSeries);

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
        return "RSI";
    }
}