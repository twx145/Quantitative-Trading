package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.util.Collections;
import java.util.List;

public class RsiTechnique implements AnalysisTechnique {
    private final int period;

    public RsiTechnique(int period) {
        this.period = period;
    }

    @Override
    public List<XYChart.Series<Number, Number>> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);

        XYChart.Series<Number, Number> rsiSeries = new XYChart.Series<>();
        rsiSeries.setName("RSI(" + period + ")");

        for (int i = 0; i < series.getBarCount(); i++) {
            long epochDay = series.getBar(i).getEndTime().toLocalDate().toEpochDay();
            rsiSeries.getData().add(new XYChart.Data<>(epochDay, rsi.getValue(i).doubleValue()));
        }

        return Collections.singletonList(rsiSeries);
    }

    @Override
    public String getName() {
        return "RSI";
    }
}