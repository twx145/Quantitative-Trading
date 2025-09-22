package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.ArrayList;
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
    public List<XYChart.Series<Number, Number>> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, shortPeriod, longPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);

        XYChart.Series<Number, Number> macdLine = new XYChart.Series<>();
        macdLine.setName("MACD(" + shortPeriod + "," + longPeriod + ")");
        XYChart.Series<Number, Number> signalLine = new XYChart.Series<>();
        signalLine.setName("Signal(" + signalPeriod + ")");

        for (int i = 0; i < series.getBarCount(); i++) {
            long epochDay = series.getBar(i).getEndTime().toLocalDate().toEpochDay();
            macdLine.getData().add(new XYChart.Data<>(epochDay, macd.getValue(i).doubleValue()));
            signalLine.getData().add(new XYChart.Data<>(epochDay, signal.getValue(i).doubleValue()));
        }

        return List.of(macdLine, signalLine);
    }

    @Override
    public String getName() {
        return "MACD";
    }
}