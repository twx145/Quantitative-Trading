package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

public class BollingerBandsTechnique implements AnalysisTechnique {

    private final int period;
    private final double k;

    public BollingerBandsTechnique(int period, double k) {
        this.period = period;
        this.k = k;
    }

    @Override
    public List<XYChart.Series<Number, Number>> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, period);

        BollingerBandsMiddleIndicator middleBand = new BollingerBandsMiddleIndicator(sma);
        Num kNum = series.numOf(k);
        BollingerBandsLowerIndicator lowerBand = new BollingerBandsLowerIndicator(middleBand, sd, kNum);
        BollingerBandsUpperIndicator upperBand = new BollingerBandsUpperIndicator(middleBand, sd, kNum);

        return List.of(
                createSeries("BB Middle", series, middleBand),
                createSeries("BB Upper", series, upperBand),
                createSeries("BB Lower", series, lowerBand)
        );
    }

    private XYChart.Series<Number, Number> createSeries(String name, BarSeries barSeries, Indicator<Num> indicator) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            long epochDay = barSeries.getBar(i).getEndTime().toLocalDate().toEpochDay();
            series.getData().add(new XYChart.Data<>(epochDay, indicator.getValue(i).doubleValue()));
        }
        return series;
    }

    @Override
    public String getName() {
        return "Bollinger Bands";
    }
}