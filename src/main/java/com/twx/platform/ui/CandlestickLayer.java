package com.twx.platform.ui;

import javafx.animation.FadeTransition;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * 一个独立的图层，专门负责在 LineChart 上绘制和管理K线图。
 * 这种分层设计能解决 Tooltip 和布局问题。
 */
public class CandlestickLayer extends Group {

    public CandlestickLayer(BarSeries series, CategoryAxis xAxis, NumberAxis yAxis) {
        // 确保这个图层不会自动调整大小，我们自己控制
        this.setAutoSizeChildren(false);

        // 监听坐标轴的变化，当图表缩放或数据更新时，重新绘制K线
        xAxis.widthProperty().addListener((obs, old, V) -> draw(series, xAxis, yAxis));
        yAxis.heightProperty().addListener((obs, old, v) -> draw(series, xAxis, yAxis));
    }

    /**
     * 核心绘制方法。
     */
    private void draw(BarSeries series, CategoryAxis xAxis, NumberAxis yAxis) {
        // 先清空所有旧的蜡烛
        getChildren().clear();

        // 计算每根蜡烛的宽度，通常是类目间距的80%
        double categoryWidth = xAxis.getCategorySpacing() * 0.8;

        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            String category = bar.getEndTime().toLocalDate().toString();

            // 使用坐标轴的 getDisplayPosition 方法，将数据值（如日期、价格）转换为屏幕上的像素坐标
            double x = xAxis.getDisplayPosition(category);
            double high = yAxis.getDisplayPosition(bar.getHighPrice().doubleValue());
            double low = yAxis.getDisplayPosition(bar.getLowPrice().doubleValue());
            double open = yAxis.getDisplayPosition(bar.getOpenPrice().doubleValue());
            double close = yAxis.getDisplayPosition(bar.getClosePrice().doubleValue());

            // 创建代表蜡烛的UI节点
            Node candle = createCandleNode(bar, categoryWidth, high, low, open, close);

            // 设置蜡烛在图层中的位置
            candle.setLayoutX(x);

            getChildren().add(candle);
        }
    }

    /**
     * 创建单根蜡烛的UI节点，并为其安装Tooltip。
     */
    private Node createCandleNode(Bar bar, double width, double high, double low, double open, double close) {
        CandlestickNode candle = new CandlestickNode(bar, high, low);
        candle.setLayoutY(0); // Y 轴位置由内部组件决定
        candle.barBody.setWidth(width);
        candle.setTranslateX(-width / 2); // 将蜡烛中心对准X轴刻度

        // 为整个蜡烛节点安装Tooltip
        Tooltip.install(candle, createTooltip(bar));

        // 添加一个简单的淡入动画，提升体验
        FadeTransition ft = new FadeTransition(Duration.millis(300), candle);
        ft.setFromValue(0.2);
        ft.setToValue(1.0);
        ft.play();

        return candle;
    }

    /**
     * 创建一个格式化的Tooltip。
     */
    private Tooltip createTooltip(Bar bar) {
        String tooltipText = String.format(
                "日期: %s\n开: %.2f\n高: %.2f\n低: %.2f\n收: %.2f",
                bar.getEndTime().toLocalDate(),
                bar.getOpenPrice().doubleValue(),
                bar.getHighPrice().doubleValue(),
                bar.getLowPrice().doubleValue(),
                bar.getClosePrice().doubleValue()
        );
        return new Tooltip(tooltipText);
    }
}