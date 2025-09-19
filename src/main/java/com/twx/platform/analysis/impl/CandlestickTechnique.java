package com.twx.platform.analysis.impl;

import com.twx.platform.analysis.AnalysisTechnique;
import javafx.scene.chart.XYChart;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.util.ArrayList;
import java.util.List;

public class CandlestickTechnique implements AnalysisTechnique {

    @Override
    public List<XYChart.Series<String, Number>> calculate(BarSeries series) {
        List<XYChart.Series<String, Number>> candleSeriesList = new ArrayList<>();

        // 注意：这是用LineChart模拟K线图，并非真正的蜡烛图。
        // 它会绘制4条独立的线：开盘、收盘、最高、最低。

        XYChart.Series<String, Number> openSeries = new XYChart.Series<>();
        openSeries.setName("开盘价");
        XYChart.Series<String, Number> highSeries = new XYChart.Series<>();
        highSeries.setName("最高价");
        XYChart.Series<String, Number> lowSeries = new XYChart.Series<>();
        lowSeries.setName("最低价");
        XYChart.Series<String, Number> closeSeries = new XYChart.Series<>();
        closeSeries.setName("收盘价");

        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            String date = bar.getEndTime().toLocalDate().toString();

            openSeries.getData().add(new XYChart.Data<>(date, bar.getOpenPrice().doubleValue()));
            highSeries.getData().add(new XYChart.Data<>(date, bar.getHighPrice().doubleValue()));
            lowSeries.getData().add(new XYChart.Data<>(date, bar.getLowPrice().doubleValue()));
            closeSeries.getData().add(new XYChart.Data<>(date, bar.getClosePrice().doubleValue()));
        }

        // 如果选择显示K线，我们只显示这四条，收盘价系列由这个类提供
        // candleSeriesList.add(openSeries);
        candleSeriesList.add(highSeries);
        candleSeriesList.add(lowSeries);
        candleSeriesList.add(closeSeries);

        return candleSeriesList;
    }

    @Override
    public String getName() {
        return "K线图(模拟)";
    }

}