package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

import java.util.ArrayList;
import java.util.List;

public class BollingerBandsTechnique implements AnalysisTechnique {

    private final int period; // 通常是20
    private final double k;   // 通常是2

    public BollingerBandsTechnique(int period, double k) {
        this.period = period;
        this.k = k;
    }

    @Override
    public List<XYChart.Series<String, Number>> calculate(BarSeries series) {
        List<XYChart.Series<String, Number>> bbSeriesList = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, period);
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, period);

        BollingerBandsMiddleIndicator middleBand = new BollingerBandsMiddleIndicator(sma);
        BollingerBandsLowerIndicator lowerBand = new BollingerBandsLowerIndicator(middleBand, sd, series.numOf(k));
        BollingerBandsUpperIndicator upperBand = new BollingerBandsUpperIndicator(middleBand, sd, series.numOf(k));

        XYChart.Series<String, Number> middleSeries = createSeries("BB Middle", series, middleBand);
        XYChart.Series<String, Number> lowerSeries = createSeries("BB Lower", series, lowerBand);
        XYChart.Series<String, Number> upperSeries = createSeries("BB Upper", series, upperBand);

        bbSeriesList.add(middleSeries);
        bbSeriesList.add(lowerSeries);
        bbSeriesList.add(upperSeries);

        return bbSeriesList;
    }

    private XYChart.Series<String, Number> createSeries(String name, BarSeries barSeries, org.ta4j.core.Indicator<org.ta4j.core.num.Num> indicator) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (int i = 0; i < barSeries.getBarCount(); i++) {
            String date = barSeries.getBar(i).getEndTime().toLocalDate().toString();
            series.getData().add(new XYChart.Data<>(date, indicator.getValue(i).doubleValue()));
        }
        return series;
    }

    @Override
    public String getName() {
        return "Bollinger Bands";
    }

}