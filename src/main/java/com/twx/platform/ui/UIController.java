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
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
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

    // ★★★ 交易日志表格及其列的 FXML 注入 ★★★
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
        // ★★★ 初始化表格列的配置 ★★★
        initializeTable();
    }

    /**
     * 配置交易日志表格的每一列如何从 Order 对象中获取数据。
     * 这个方法只在程序启动时调用一次。
     */
    private void initializeTable() {
        // dateColumn: 使用 lambda 表达式进行自定义渲染，将 ZonedDateTime 格式化为 yyyy-MM-dd 字符串。
        dateColumn.setCellValueFactory(cellData -> {
            String formattedDate = cellData.getValue().timestamp().format(DATE_FORMATTER);
            return new SimpleStringProperty(formattedDate);
        });

// signalColumn: 直接从 record 的 signal() 方法获取 TradeSignal 对象
        signalColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().signal()).asString());

// priceColumn: 直接从 record 的 price() 方法获取 double 值，并包装成 JavaFX 需要的类型
        priceColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price()).asObject());

// quantityColumn: 同上
        quantityColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().quantity()).asObject());

        // valueColumn: "成交金额" 是一个计算列，需要用 lambda 表达式特别处理。
        valueColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            double value = order.price() * order.quantity();
            return new SimpleStringProperty(String.format("%,.2f", value)); // 格式化为带千分位和两位小数的字符串
        });
    }


    @FXML
    private void handleRunBacktest() {
        // (此方法中的回测逻辑保持不变)
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");
        tradeLogTable.getItems().clear(); // ★★★ 运行前回滚清空表格

        new Thread(() -> {
            try {
                // (组装回测组件的逻辑保持不变)
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate =endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                double commissionRate = 0.0003;

                DataProvider dataProvider = new SinaDataProvider();
                Ticker ticker = new Ticker(tickerSymbol);
                Portfolio portfolio = new BasicPortfolio(initialCash, commissionRate);
                PositionSizer positionSizer = new CashPercentagePositionSizer(0.10);
                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);

                BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, TimeFrame.DAILY);
                if (series.isEmpty()) {
                    Platform.runLater(() -> summaryArea.setText("无法获取数据，请检查股票代码或网络连接。"));
                    return;
                }
                Strategy strategy = new MovingAverageCrossStrategy(series, 10, 30);
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                // 回到 UI 线程更新所有界面元素
                Platform.runLater(() -> {
                    updateChart(result);
                    updateSummary(result);
                    // ★★★ 调用方法更新交易日志表格 ★★★
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
        tradeLogTable.getItems().clear(); // 先清空旧数据，确保万无一失
        tradeLogTable.getItems().addAll(result.executedOrders()); // 添加所有新交易记录
    }

    /**
     * 【已修改】更新总结区域，现在只显示业绩总结，不再包含字符串日志。
     */
    private void updateSummary(BacktestResult result) {
        if (result.finalPortfolio() instanceof BasicPortfolio bp) {
            summaryArea.setText(bp.getSummary());
        }
    }

    // (updateChart 和其他图表相关的辅助方法保持不变)
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
}