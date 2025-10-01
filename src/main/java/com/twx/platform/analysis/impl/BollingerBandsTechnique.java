package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.util.Date;
import java.util.List;

public class BollingerBandsTechnique implements AnalysisTechnique {

    private final int period;
    private final double k;

    public BollingerBandsTechnique(int period, double k) {
        this.period = period;
        this.k = k;
    }

    @Override
    public List<XYDataset> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, period);

        BollingerBandsMiddleIndicator middleBand = new BollingerBandsMiddleIndicator(sma);
        Num kNum = series.numOf(k);
        BollingerBandsLowerIndicator lowerBand = new BollingerBandsLowerIndicator(middleBand, sd, kNum);
        BollingerBandsUpperIndicator upperBand = new BollingerBandsUpperIndicator(middleBand, sd, kNum);

        // 为每个指标创建一个 JFreeChart 的 XYSeries
        XYSeries middleSeries = createSeries("BB Middle", series, middleBand);
        XYSeries upperSeries = createSeries("BB Upper", series, upperBand);
        XYSeries lowerSeries = createSeries("BB Lower", series, lowerBand);

        // 将所有 XYSeries 添加到一个数据集中
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(middleSeries);
        dataset.addSeries(upperSeries);
        dataset.addSeries(lowerSeries);

        return List.of(dataset);
    }

    private XYSeries createSeries(String name, BarSeries barSeries, Indicator<Num> indicator) {
        XYSeries series = new XYSeries(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            Date date = Date.from(barSeries.getBar(i).getEndTime().toInstant());
            series.add(date.getTime(), indicator.getValue(i).doubleValue());
        }
        return series;
    }

    @Override
    public String getName() {
        return "Bollinger Bands";
    }
}