package com.twx.platform.ui;

import javafx.scene.Group;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.ta4j.core.Bar;

/**
 * 代表K线图中一根蜡烛的自定义UI节点 (简化版)。
 * Tooltip 的逻辑已移至 CandlestickLayer。
 */
public class CandlestickNode extends Group {

    public final Line highLowLine = new Line();
    public final Rectangle barBody = new Rectangle();

    public CandlestickNode(Bar bar, double high, double low) {
        getStyleClass().add("candlestick-node");
        highLowLine.getStyleClass().add("candlestick-line");
        barBody.getStyleClass().add("candlestick-bar");

        double open = bar.getOpenPrice().doubleValue();
        double close = bar.getClosePrice().doubleValue();

        // 这里的 Y 坐标是相对于父节点 (CandlestickLayer) 的
        barBody.setY(Math.min(open, close));
        barBody.setHeight(Math.abs(open - close));

        highLowLine.setStartY(high);
        highLowLine.setEndY(low);

        if (close > open) {
            getStyleClass().add("candlestick-up");
        } else {
            getStyleClass().add("candlestick-down");
        }

        getChildren().addAll(highLowLine, barBody);
    }
}