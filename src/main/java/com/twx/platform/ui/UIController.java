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
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class UIController {

    // --- FXML 控件注入 ---
    @FXML private TextField tickerField;
    @FXML private TextField initialCashField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button runButton;
    @FXML private LineChart<String, Number> priceChart;
    @FXML private TextArea summaryArea;

    // ★★★ 新增：交易日志表格及其列的 FXML 注入 ★★★
    @FXML private TableView<Order> tradeLogTable;
    @FXML private TableColumn<Order, String> dateColumn;
    @FXML private TableColumn<Order, String> signalColumn;
    @FXML private TableColumn<Order, Double> priceColumn;
    @FXML private TableColumn<Order, Double> quantityColumn;
    @FXML private TableColumn<Order, String> valueColumn;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    @FXML
    public void initialize() {
        // 初始化默认日期
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());
        // ★★★ 新增：初始化表格列的配置 ★★★
        initializeTable();
    }

    /**
     * 【新增】配置交易日志表格的每一列如何从 Order 对象中获取数据。
     *  这个方法只在程序启动时调用一次。
     */
    private void initializeTable() {
        // PropertyValueFactory 会自动寻找 Order record 中对应名称的方法 (如 timestamp(), signal() 等)
        dateColumn.setCellValueFactory(cellData -> {
            // 对日期进行格式化，使其更美观
            String formattedDate = cellData.getValue().timestamp().format(DATE_FORMATTER);
            return new SimpleStringProperty(formattedDate);
        });
        signalColumn.setCellValueFactory(new PropertyValueFactory<>("signal"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        // "成交金额" 是一个计算列，需要特别处理
        valueColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            double value = order.price() * order.quantity();
            // 格式化为带两位小数的字符串
            return new SimpleStringProperty(String.format("%,.2f", value));
        });
    }


    @FXML
    private void handleRunBacktest() {
        // (此方法中的回测逻辑保持不变)
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");

        new Thread(() -> {
            try {
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                double commissionRate = 0.0003;

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
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                Platform.runLater(() -> {
                    updateChart(result);
                    updateSummary(result);
                    // ★★★ 新增：调用方法更新交易日志表格 ★★★
                    updateTradeLog(result);
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
     * 【新增】用回测结果中的交易列表填充表格。
     */
    private void updateTradeLog(BacktestResult result) {
        tradeLogTable.getItems().clear(); // 先清空旧数据
        tradeLogTable.getItems().addAll(result.executedOrders()); // 添加所有新交易
    }

    // (updateChart, updateSummary 和其他辅助方法保持不变)
    private void updateChart(BacktestResult result) {
        priceChart.getData().clear();
        String cssPath = getClass().getResource("style.css").toExternalForm();
        if (!priceChart.getStylesheets().contains(cssPath)) {
            priceChart.getStylesheets().add(cssPath);
        }

        BarSeries series = result.series();
        if (series.isEmpty()) return;

        XYChart.Series<String, Number> closePriceSeries = createClosePriceSeries(series);
        XYChart.Series<String, Number> buySignalsSeries = createSignalSeries("买入点", result.executedOrders(), TradeSignal.BUY);
        XYChart.Series<String, Number> sellSignalsSeries = createSignalSeries("卖出点", result.executedOrders(), TradeSignal.SELL);

        priceChart.getData().add(closePriceSeries);

        for (Map.Entry<String, Map<Integer, Double>> entry : result.indicatorsData().entrySet()) {
            priceChart.getData().add(createIndicatorSeries(entry.getKey(), series, entry.getValue()));
        }

        priceChart.getData().addAll(buySignalsSeries, sellSignalsSeries);

        Platform.runLater(() -> {
            applyCssToSeries(buySignalsSeries, "buy-signal-symbol");
            applyCssToSeries(sellSignalsSeries, "sell-signal-symbol");
        });
    }

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