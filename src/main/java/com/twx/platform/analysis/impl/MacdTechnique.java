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
        this.shortPeriod = shortPeriod; // 通常是12
        this.longPeriod = longPeriod;   // 通常是26
        this.signalPeriod = signalPeriod; // 通常是9
    }

    @Override
    public List<XYChart.Series<String, Number>> calculate(BarSeries series) {
        List<XYChart.Series<String, Number>> macdSeriesList = new ArrayList<>();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        MACDIndicator macd = new MACDIndicator(closePrice, shortPeriod, longPeriod);
        EMAIndicator signal = new EMAIndicator(macd, signalPeriod);

        XYChart.Series<String, Number> macdLine = new XYChart.Series<>();
        macdLine.setName("MACD(" + shortPeriod + "," + longPeriod + ")");
        XYChart.Series<String, Number> signalLine = new XYChart.Series<>();
        signalLine.setName("Signal(" + signalPeriod + ")");

        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            macdLine.getData().add(new XYChart.Data<>(date, macd.getValue(i).doubleValue()));
            signalLine.getData().add(new XYChart.Data<>(date, signal.getValue(i).doubleValue()));
        }

        macdSeriesList.add(macdLine);
        macdSeriesList.add(signalLine);

        // 注意：MACD柱状图(Histogram)通常在单独的子图中绘制，这里我们只在主图中绘制两条线。
        return macdSeriesList;
    }

    @Override
    public String getName() {
        return "MACD";
    }

}