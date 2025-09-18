package com.twx.platform.ui;

import com.twx.platform.common.Order;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.common.TradeSignal;
import com.twx.platform.data.DataProvider;
import com.twx.platform.data.impl.SinaDataProvider;
import com.twx.platform.engine.BacktestEngine;
import com.twx.platform.engine.BacktestResult;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.portfolio.impl.BasicPortfolio;
import com.twx.platform.position.PositionSizer;
import com.twx.platform.position.impl.CashPercentagePositionSizer;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.util.Map;

/**
 * UI控制器，经过极大简化。
 * - 移除了所有复杂的K线图绘制逻辑。
 * - UI只负责展示由后端计算好的数据。
 */
public class UIController {

    // FXML 控件的注入
    @FXML private TextField tickerField;
    @FXML private TextField initialCashField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button runButton;
    @FXML private LineChart<String, Number> priceChart;
    @FXML private TextArea summaryArea;

    @FXML
    public void initialize() {
        // 设置默认日期
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());
    }

    /**
     * "运行回测"按钮的事件处理器。
     */
    @FXML
    private void handleRunBacktest() {
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");

        // 将耗时的回测任务放到后台线程，防止UI卡顿
        new Thread(() -> {
            try {
                // 1. 从UI获取参数
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                double commissionRate = 0.0003;

                // 2. 组装回测组件
                DataProvider dataProvider = new SinaDataProvider();
                Ticker ticker = new Ticker(tickerSymbol);
                Portfolio portfolio = new BasicPortfolio(initialCash, commissionRate);
                PositionSizer positionSizer = new CashPercentagePositionSizer(0.95);
                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);

                BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, TimeFrame.DAILY);
                if (series.isEmpty()) {
                    Platform.runLater(() -> summaryArea.setText("无法获取数据，请检查股票代码或网络连接。"));
                    return;
                }
                Strategy strategy = new MovingAverageCrossStrategy(series, 10, 30);

                // 3. 运行回测并获取包含所有预计算数据的结果
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                // 4. 回到UI线程，用结果更新界面
                Platform.runLater(() -> {
                    updateChart(result);
                    updateSummary(result);
                });

            } catch (Exception e) {
                Platform.runLater(() -> summaryArea.setText("发生错误: \n" + e.getMessage()));
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> runButton.setDisable(false));
            }
        }).start();
    }

    /**
     * 【极大简化】更新图表的方法。
     * 它只负责将传入的数据“画”出来。
     */
    private void updateChart(BacktestResult result) {
        priceChart.getData().clear();
        String cssPath = getClass().getResource("style.css").toExternalForm();
        if (!priceChart.getStylesheets().contains(cssPath)) {
            priceChart.getStylesheets().add(cssPath);
        }

        BarSeries series = result.series();
        if (series.isEmpty()) return;

        // --- 创建各个数据系列 ---
        XYChart.Series<String, Number> closePriceSeries = createClosePriceSeries(series);
        XYChart.Series<String, Number> buySignalsSeries = createSignalSeries("买入点", result.executedOrders(), TradeSignal.BUY);
        XYChart.Series<String, Number> sellSignalsSeries = createSignalSeries("卖出点", result.executedOrders(), TradeSignal.SELL);

        // --- 将所有系列添加到图表中 ---
        priceChart.getData().add(closePriceSeries);

        // 动态添加指标线
        for (Map.Entry<String, Map<Integer, Double>> entry : result.indicatorsData().entrySet()) {
            priceChart.getData().add(createIndicatorSeries(entry.getKey(), series, entry.getValue()));
        }

        priceChart.getData().addAll(buySignalsSeries, sellSignalsSeries);

        // --- 为买卖点应用CSS样式 ---
        Platform.runLater(() -> {
            applyCssToSeries(buySignalsSeries, "buy-signal-symbol");
            applyCssToSeries(sellSignalsSeries, "sell-signal-symbol");
        });
    }

    // --- 以下是将UI更新逻辑拆分成的辅助方法，使代码更清晰 ---

    private XYChart.Series<String, Number> createClosePriceSeries(BarSeries series) {
        XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
        dataSeries.setName("收盘价");
        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            double closePrice = series.getBar(i).getClosePrice().doubleValue();
            dataSeries.getData().add(new XYChart.Data<>(date, closePrice));
        }
        return dataSeries;
    }

    private XYChart.Series<String, Number> createIndicatorSeries(String name, BarSeries series, Map<Integer, Double> indicatorData) {
        XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
        dataSeries.setName(name);
        for (int i = 0; i < series.getBarCount(); i++) {
            if (indicatorData.containsKey(i)) {
                String date = series.getBar(i).getEndTime().toLocalDate().toString();
                dataSeries.getData().add(new XYChart.Data<>(date, indicatorData.get(i)));
            }
        }
        return dataSeries;
    }

    private XYChart.Series<String, Number> createSignalSeries(String name, Iterable<Order> orders, TradeSignal signalType) {
        XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
        dataSeries.setName(name);
        for (Order order : orders) {
            if (order.signal() == signalType) {
                String date = order.timestamp().toLocalDate().toString();
                dataSeries.getData().add(new XYChart.Data<>(date, order.price()));
            }
        }
        return dataSeries;
    }

    private void applyCssToSeries(XYChart.Series<String, Number> series, String cssClass) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() != null) {
                data.getNode().getStyleClass().add(cssClass);
            }
        }
    }

    private void updateSummary(BacktestResult result) {
        if (result.finalPortfolio() instanceof BasicPortfolio bp) {
            summaryArea.setText(bp.getSummary());
        }
    }
}