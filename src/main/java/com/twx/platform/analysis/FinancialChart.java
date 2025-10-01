package com.twx.platform.analysis;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.fx.ChartViewer;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYDataset;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.text.SimpleDateFormat;

public class FinancialChart {

    // --- STYLE CONSTANTS (from CSS) ---
    // Light Theme Colors
    private static final Color LT_BG = Color.decode("#f4f5f7");
    private static final Color LT_PLOT_BG = Color.decode("#ffffff");
    private static final Color LT_TEXT_PRIMARY = Color.decode("#2c3e50");
    private static final Color LT_TEXT_SECONDARY = Color.decode("#7f8c8d");
    private static final Color LT_GRID = Color.decode("#dcdfe6").brighter();

    // Dark Theme Colors
    private static final Color DT_BG = Color.decode("#233140");
    private static final Color DT_PLOT_BG = Color.decode("#2c3e50");
    private static final Color DT_TEXT_PRIMARY = Color.decode("#ecf0f1");
    private static final Color DT_TEXT_SECONDARY = Color.decode("#95a5a6");
    private static final Color DT_GRID = Color.decode("#3e5166");

    // Indicator Colors (shared)
    private static final Color COLOR_BULL = Color.decode("#26a69a"); // Positive
    private static final Color COLOR_BEAR = Color.decode("#ef5350"); // Negative
    private static final Color[] MA_COLORS = { new Color(255, 165, 0), new Color(0, 191, 255) };
    private static final Color[] BBANDS_COLORS = { Color.DARK_GRAY, new Color(138, 43, 226), Color.DARK_GRAY };
    private static final Color[] RSI_COLORS = { new Color(0, 139, 139) };
    private static final Color[] MACD_COLORS = { Color.BLUE, new Color(255, 100, 0) };
    private static final BasicStroke INDICATOR_STROKE = new BasicStroke(1.2f);

    private static final int CANDLESTICK_INDEX = 0, MA_INDEX = 1, BBANDS_INDEX = 2;
    private final ChartViewer chartViewer;
    private final CombinedDomainXYPlot combinedPlot;
    private final JFreeChart chart;
    private final XYPlot mainPlot, rsiPlot, macdPlot;

    public FinancialChart(String title, boolean isDark) {
        DateAxis domainAxis = new DateAxis("Date");
        domainAxis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd"));
        NumberAxis mainRangeAxis = new NumberAxis("Price");
        mainRangeAxis.setAutoRangeIncludesZero(false);

        mainPlot = new XYPlot(null, domainAxis, mainRangeAxis, null);
        CandlestickRenderer candlestickRenderer = new CandlestickRenderer();
        candlestickRenderer.setDrawVolume(false);
        candlestickRenderer.setUpPaint(COLOR_BULL);
        candlestickRenderer.setDownPaint(COLOR_BEAR);
        mainPlot.setRenderer(CANDLESTICK_INDEX, candlestickRenderer);
        mainPlot.setRenderer(MA_INDEX, createLineRenderer(MA_COLORS));
        mainPlot.setRenderer(BBANDS_INDEX, createLineRenderer(BBANDS_COLORS));
//        candlestickRenderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_INTERVALDATA);

        rsiPlot = createSubplot("RSI", createLineRenderer(RSI_COLORS));
        macdPlot = createSubplot("MACD", createLineRenderer(MACD_COLORS));

        mainPlot.setDomainPannable(true);
        mainPlot.setRangePannable(true);
        rsiPlot.setDomainPannable(true);
        macdPlot.setDomainPannable(true);

        combinedPlot = new CombinedDomainXYPlot(domainAxis);
        combinedPlot.add(mainPlot, 3);
        combinedPlot.setGap(10.0);

        chart = new JFreeChart(title, new Font("Microsoft YaHei UI", Font.BOLD, 14), combinedPlot, true);
        this.chartViewer = new ChartViewer(chart);

        // Apply initial theme
        applyTheme(isDark);
    }

    public ChartViewer getChartViewer() { return chartViewer; }
    public void setNotify(boolean notify) { this.chart.setNotify(notify); }

    public void applyTheme(boolean isDark) {
        Color bg = isDark ? DT_BG : LT_BG;
        Color plotBg = isDark ? DT_PLOT_BG : LT_PLOT_BG;
        Color textPrimary = isDark ? DT_TEXT_PRIMARY : LT_TEXT_PRIMARY;
        Color textSecondary = isDark ? DT_TEXT_SECONDARY : LT_TEXT_SECONDARY;
        Color grid = isDark ? DT_GRID : LT_GRID;

        chart.setBackgroundPaint(bg);

        TextTitle title = chart.getTitle();
        if (title != null) title.setPaint(textPrimary);

        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(bg);
            legend.setItemPaint(textPrimary);
        }

        combinedPlot.setBackgroundPaint(bg);
        combinedPlot.setDomainGridlinePaint(grid);

        // Apply to main plot and all subplots
        for (Object plotObj : combinedPlot.getSubplots()) {
            if (plotObj instanceof XYPlot) {
                XYPlot plot = (XYPlot) plotObj;
                plot.setBackgroundPaint(plotBg);
                plot.setRangeGridlinePaint(grid);

                // Apply to both domain and range axes
                applyAxisTheme(plot.getDomainAxis(), textPrimary, textSecondary);
                applyAxisTheme(plot.getRangeAxis(), textPrimary, textSecondary);
            }
        }
    }

    private void applyAxisTheme(Axis axis, Color primary, Color secondary) {
        if (axis == null) return;
        axis.setLabelPaint(primary);
        axis.setTickLabelPaint(secondary);
        axis.setAxisLinePaint(secondary);
    }

    public void restoreAutoBounds() {
        mainPlot.getDomainAxis().setAutoRange(true);
        mainPlot.getRangeAxis().setAutoRange(true);
        for (Object subplotObj : combinedPlot.getSubplots()) {
            if (subplotObj instanceof XYPlot) ((XYPlot) subplotObj).getRangeAxis().setAutoRange(true);
        }
    }

    // ... (其他所有 setDataset, clearAll 等方法保持不变) ...
    public void clearAll() { mainPlot.clearAnnotations(); mainPlot.setDataset(CANDLESTICK_INDEX, null); mainPlot.setDataset(MA_INDEX, null); mainPlot.setDataset(BBANDS_INDEX, null); combinedPlot.remove(rsiPlot); combinedPlot.remove(macdPlot); rsiPlot.setDataset(0, null); macdPlot.setDataset(0, null); }
    public void setCandlestickDataset(XYDataset dataset, boolean visible) { mainPlot.setDataset(CANDLESTICK_INDEX, dataset); ((CandlestickRenderer)mainPlot.getRenderer(CANDLESTICK_INDEX)).setSeriesVisible(0, visible, false); }
    public void setMaDataset(XYDataset dataset) { mainPlot.setDataset(MA_INDEX, dataset); }
    public void setBollingerBandsDataset(XYDataset dataset) { mainPlot.setDataset(BBANDS_INDEX, dataset); }
    public void setRsiDataset(XYDataset dataset) { if (dataset != null) { rsiPlot.setDataset(0, dataset); combinedPlot.add(rsiPlot, 1); } }
    public void setMacdDataset(XYDataset dataset) { if (dataset != null) { macdPlot.setDataset(0, dataset); combinedPlot.add(macdPlot, 1); } }
    public void addTradeSignalAnnotation(XYPointerAnnotation annotation) { mainPlot.addAnnotation(annotation, false); }
    private XYLineAndShapeRenderer createLineRenderer(Color[] colors) { XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false); for (int i = 0; i < colors.length; i++) { renderer.setSeriesPaint(i, colors[i]); renderer.setSeriesStroke(i, INDICATOR_STROKE); } return renderer; }
    private XYPlot createSubplot(String yAxisLabel, XYLineAndShapeRenderer renderer) { NumberAxis rangeAxis = new NumberAxis(yAxisLabel); rangeAxis.setAutoRangeIncludesZero(false); return new XYPlot(null, null, rangeAxis, renderer); }
}