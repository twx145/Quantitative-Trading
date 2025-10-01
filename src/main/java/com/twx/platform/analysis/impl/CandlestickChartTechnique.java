package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.XYDataset;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.Date;
import java.util.List;

public class CandlestickChartTechnique implements AnalysisTechnique {

    @Override
    public List<XYDataset> calculate(BarSeries series) {
        final int barCount = series.getBarCount();
        Date[] dates = new Date[barCount];
        double[] highs = new double[barCount];
        double[] lows = new double[barCount];
        double[] opens = new double[barCount];
        double[] closes = new double[barCount];
        double[] volumes = new double[barCount]; // 虽然K线图不需要，但数据集支持

        for (int i = 0; i < barCount; i++) {
            Bar bar = series.getBar(i);
            dates[i] = Date.from(bar.getEndTime().toInstant());
            highs[i] = bar.getHighPrice().doubleValue();
            lows[i] = bar.getLowPrice().doubleValue();
            opens[i] = bar.getOpenPrice().doubleValue();
            closes[i] = bar.getClosePrice().doubleValue();
            volumes[i] = bar.getVolume().doubleValue();
        }

        // JFreeChart 的原生 OHLC 数据集
        DefaultHighLowDataset dataset = new DefaultHighLowDataset(
                "OHLC", dates, highs, lows, opens, closes, volumes
        );

        // 返回只包含这一个数据集的列表
        return List.of(dataset);
    }

    @Override
    public String getName() {
        return "Candlestick Chart";
    }
}