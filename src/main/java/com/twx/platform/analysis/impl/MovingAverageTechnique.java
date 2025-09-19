package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.ArrayList;
import java.util.List;

public class MovingAverageTechnique implements AnalysisTechnique {

    private final int shortPeriod;
    private final int longPeriod;

    public MovingAverageTechnique(int shortPeriod, int longPeriod) {
        this.shortPeriod = shortPeriod;
        this.longPeriod = longPeriod;
    }

    @Override
    public List<XYChart.Series<String, Number>> calculate(BarSeries series) {
        List<XYChart.Series<String, Number>> maSeriesList = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // 短期均线
        SMAIndicator shortSma = new SMAIndicator(closePrice, shortPeriod);
        XYChart.Series<String, Number> shortMaSeries = new XYChart.Series<>();
        shortMaSeries.setName("SMA(" + shortPeriod + ")");
        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            shortMaSeries.getData().add(new XYChart.Data<>(date, shortSma.getValue(i).doubleValue()));
        }
        maSeriesList.add(shortMaSeries);

        // 长期均线
        SMAIndicator longSma = new SMAIndicator(closePrice, longPeriod);
        XYChart.Series<String, Number> longMaSeries = new XYChart.Series<>();
        longMaSeries.setName("SMA(" + longPeriod + ")");
        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            longMaSeries.getData().add(new XYChart.Data<>(date, longSma.getValue(i).doubleValue()));
        }
        maSeriesList.add(longMaSeries);

        return maSeriesList;
    }

    @Override
    public String getName() {
        return "Moving Averages";
    }

}