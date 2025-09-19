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
import com.twx.platform.position.impl.FixedQuantityPositionSizer;
import com.twx.platform.position.impl.FixedCashQuantityPositionSizer;
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

    @FXML private TextField tickerField;
    @FXML private TextField initialCashField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button runButton;
    @FXML private LineChart<String, Number> priceChart;
    @FXML private TextArea summaryArea;
    @FXML private TableView<Order> tradeLogTable;
    @FXML private TableColumn<Order, String> dateColumn;
    @FXML private TableColumn<Order, String> signalColumn;
    @FXML private TableColumn<Order, Double> priceColumn;
    @FXML private TableColumn<Order, Double> quantityColumn;
    @FXML private TableColumn<Order, String> valueColumn;
    @FXML private TextField shortMaField;
    @FXML private TextField longMaField;
    @FXML private ComboBox<String> positionSizerComboBox;
    @FXML private Label sizerParamLabel;
    @FXML private TextField sizerParamField;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CASH_PERCENTAGE_SIZER = "按资金百分比";
    private static final String FIXED_CASH_QUANTITY_SIZER = "按固定资金";
    private static final String FIXED_QUANTITY_SIZER = "按固定股数";



    @FXML
    public void initialize() {
        // 初始化默认日期
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());
        // 初始化表格列
        initializeTable();
        // ★★★ 初始化新增的策略选择控件 ★★★
        initializePositionSizerControls();
    }

    /**
     * ★★★ 新增方法：初始化投资策略选择相关的UI控件 ★★★
     */
    private void initializePositionSizerControls() {
        // 1. 填充 ComboBox 的选项
        positionSizerComboBox.getItems().addAll(CASH_PERCENTAGE_SIZER, FIXED_QUANTITY_SIZER,FIXED_CASH_QUANTITY_SIZER);

        // 2. 添加监听器，当选择变化时，更新标签和默认参数
        positionSizerComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            switch (newValue) {
                case CASH_PERCENTAGE_SIZER -> {
                    sizerParamLabel.setText("资金比例(%):");
                    sizerParamField.setText("15.0"); // 默认使用15%的资金
                }
                case FIXED_QUANTITY_SIZER -> {
                    sizerParamLabel.setText("固定股数:");
                    sizerParamField.setText("100"); // 默认每次交易100股
                }
                case FIXED_CASH_QUANTITY_SIZER -> {
                    sizerParamLabel.setText("固定资金:");
                    sizerParamField.setText("1000"); // 默认每次交易1000元
                }
            }
        });

        // 3. 设置默认选中项
        positionSizerComboBox.getSelectionModel().selectFirst();
    }


    @FXML
    private void handleRunBacktest() {
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");
        tradeLogTable.getItems().clear();

        new Thread(() -> {
            try {
                // --- 1. 从UI获取所有参数 ---
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                double commissionRate = 0.0003;

                // ★★★ 获取均线周期参数 ★★★
                int shortMa = Integer.parseInt(shortMaField.getText());
                int longMa = Integer.parseInt(longMaField.getText());

                // ★★★ 获取并创建仓位管理器 ★★★
                PositionSizer positionSizer = createPositionSizerFromUI();
                if (positionSizer == null) {
                    Platform.runLater(() -> summaryArea.setText("错误: 无效的投资策略参数。"));
                    return;
                }

                // --- 2. 组装回测引擎和组件 ---
                DataProvider dataProvider = new SinaDataProvider();
                Ticker ticker = new Ticker(tickerSymbol);
                Portfolio portfolio = new BasicPortfolio(initialCash, commissionRate);
                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);

                BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, TimeFrame.DAILY);
                if (series.isEmpty()) {
                    Platform.runLater(() -> summaryArea.setText("无法获取数据，请检查股票代码或网络连接。"));
                    return;
                }

                // ★★★ 使用从UI获取的周期创建策略 ★★★
                Strategy strategy = new MovingAverageCrossStrategy(series, shortMa, longMa);

                // --- 3. 运行回测 ---
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                // --- 4. 在UI线程更新结果 ---
                Platform.runLater(() -> {
                    updateChart(result);
                    updateSummary(result);
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
     * ★★★ 新增辅助方法：根据UI选择创建对应的 PositionSizer 实例 ★★★
     */
    private PositionSizer createPositionSizerFromUI() {
        String selectedSizer = positionSizerComboBox.getSelectionModel().getSelectedItem();
        double param = Double.parseDouble(sizerParamField.getText());

        if (CASH_PERCENTAGE_SIZER.equals(selectedSizer)) {
            return new CashPercentagePositionSizer(param / 100.0);
        } else if (FIXED_QUANTITY_SIZER.equals(selectedSizer)) {
            return new FixedQuantityPositionSizer((int) param);
        } else if (FIXED_CASH_QUANTITY_SIZER.equals(selectedSizer)) {
            return new FixedCashQuantityPositionSizer((int) param);
        }
        return null; // 如果没有匹配的选项，返回null
    }

    // ... (initializeTable, updateTradeLog, updateSummary, updateChart 等其他方法保持不变) ...
    private void initializeTable() {
        dateColumn.setCellValueFactory(cellData -> {
            String formattedDate = cellData.getValue().timestamp().format(DATE_FORMATTER);
            return new SimpleStringProperty(formattedDate);
        });
        signalColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().signal()).asString());
        priceColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price()).asObject());
        quantityColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().quantity()).asObject());
        valueColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            double value = order.price() * order.quantity();
            return new SimpleStringProperty(String.format("%,.2f", value));
        });
    }
    private void updateTradeLog(BacktestResult result) {
        tradeLogTable.getItems().clear();
        tradeLogTable.getItems().addAll(result.executedOrders());
    }
    private void updateSummary(BacktestResult result) {
        if (result.finalPortfolio() instanceof BasicPortfolio bp) {
            summaryArea.setText(bp.getSummary());
        }
    }
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