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

    public RsiTechnique(int period) { // 通常是14
        this.period = period;
    }

    @Override
    public List<XYChart.Series<String, Number>> calculate(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);

        XYChart.Series<String, Number> rsiSeries = new XYChart.Series<>();
        rsiSeries.setName("RSI(" + period + ")");

        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            rsiSeries.getData().add(new XYChart.Data<>(date, rsi.getValue(i).doubleValue()));
        }

        // 注意：RSI指标的值域是0-100，直接在股价图上绘制可能不直观，通常也绘制在子图中。
        // 为了演示，我们暂时将其绘制在主图。
        return Collections.singletonList(rsiSeries);
    }

    @Override
    public String getName() {
        return "RSI";
    }

}