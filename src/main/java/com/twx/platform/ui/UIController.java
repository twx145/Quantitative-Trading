package com.twx.platform.ui;

import com.twx.platform.analysis.AnalysisTechnique;
import com.twx.platform.analysis.impl.*;
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
import com.twx.platform.position.impl.FixedCashQuantityPositionSizer;
import com.twx.platform.position.impl.FixedQuantityPositionSizer;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UIController {

    // --- FXML UI Elements ---
    @FXML private BorderPane rootPane; // Root pane for theme switching
    @FXML private TextField tickerField;
    @FXML private TextField initialCashField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField shortMaField;
    @FXML private TextField longMaField;
    @FXML private ComboBox<String> positionSizerComboBox;
    @FXML private Label sizerParamLabel;
    @FXML private TextField sizerParamField;
    @FXML private Button runButton;
    @FXML private ToggleButton themeToggleButton;

    // Chart and Analysis Options
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CheckBox showCandlestickCheck;
    @FXML private CheckBox showMaCheck;
    @FXML private CheckBox showMacdCheck;
    @FXML private CheckBox showRsiCheck;
    @FXML private CheckBox showBbCheck;
    @FXML private TextField rsiPeriodField;
    @FXML private TextField bbandsPeriodField;


    // Result Display
    @FXML private TextArea summaryArea;
    @FXML private TableView<Order> tradeLogTable;
    @FXML private TableColumn<Order, String> dateColumn;
    @FXML private TableColumn<Order, String> signalColumn;
    @FXML private TableColumn<Order, Double> priceColumn;
    @FXML private TableColumn<Order, Double> quantityColumn;
    @FXML private TableColumn<Order, String> valueColumn;

    // --- Constants ---
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CASH_PERCENTAGE_SIZER = "按资金百分比";
    private static final String FIXED_CASH_QUANTITY_SIZER = "按固定资金";
    private static final String FIXED_QUANTITY_SIZER = "按固定股数";

    @FXML
    public void initialize() {
        // Initialize default values for UI controls
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());

        initializeTable();
        initializePositionSizerControls();
    }

    private void initializeTable() {
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().timestamp().format(DATE_FORMATTER)));
        signalColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().signal()).asString());
        priceColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price()).asObject());
        quantityColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().quantity()).asObject());
        valueColumn.setCellValueFactory(cellData -> {
            Order order = cellData.getValue();
            double value = order.price() * order.quantity();
            return new SimpleStringProperty(String.format("%,.2f", value));
        });
    }

    private void initializePositionSizerControls() {
        positionSizerComboBox.getItems().addAll(CASH_PERCENTAGE_SIZER, FIXED_QUANTITY_SIZER, FIXED_CASH_QUANTITY_SIZER);
        positionSizerComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            switch (newValue) {
                case CASH_PERCENTAGE_SIZER -> {
                    sizerParamLabel.setText("资金比例(%):");
                    sizerParamField.setText("15.0");
                }
                case FIXED_QUANTITY_SIZER -> {
                    sizerParamLabel.setText("固定股数:");
                    sizerParamField.setText("100");
                }
                case FIXED_CASH_QUANTITY_SIZER -> {
                    sizerParamLabel.setText("固定资金:");
                    sizerParamField.setText("1000");
                }
            }
        });
        positionSizerComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleThemeToggle() {
        ObservableList<String> styleClasses = rootPane.getStyleClass();
        if (themeToggleButton.isSelected()) {
            styleClasses.add("theme-dark");
            themeToggleButton.setText("☀");
        } else {
            styleClasses.remove("theme-dark");
            themeToggleButton.setText("🌙");
        }
    }

    @FXML
    private void handleRunBacktest() {
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");
        tradeLogTable.getItems().clear();
        priceChart.getData().clear();

        new Thread(() -> {
            try {
                // --- 1. Get parameters from UI ---
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                int shortMa = Integer.parseInt(shortMaField.getText());
                int longMa = Integer.parseInt(longMaField.getText());
                PositionSizer positionSizer = createPositionSizerFromUI();
                if (positionSizer == null) {
                    Platform.runLater(() -> summaryArea.setText("错误: 无效的投资策略参数。"));
                    return;
                }

                // --- 2. Assemble and run backtest engine ---
                DataProvider dataProvider = new SinaDataProvider();
                Ticker ticker = new Ticker(tickerSymbol);
                BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, TimeFrame.DAILY);
                if (series == null || series.isEmpty()) {
                    Platform.runLater(() -> summaryArea.setText("无法获取数据，请检查股票代码或网络连接。"));
                    return;
                }
                Portfolio portfolio = new BasicPortfolio(initialCash, 0.0003);
                Strategy strategy = new MovingAverageCrossStrategy(series, shortMa, longMa);
                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                // --- 3. Prepare analysis techniques based on UI selections ---
                List<AnalysisTechnique> techniques = new ArrayList<>();
                if (showMaCheck.isSelected()) {
                    techniques.add(new MovingAverageTechnique(shortMa, longMa));
                }
                if (showMacdCheck.isSelected()) {
                    techniques.add(new MacdTechnique(12, 26, 9));
                }
                if (showRsiCheck.isSelected()) {
                    int rsiPeriod = Integer.parseInt(rsiPeriodField.getText());
                    techniques.add(new RsiTechnique(rsiPeriod));
                }
                if (showBbCheck.isSelected()) {
                    int bbPeriod = Integer.parseInt(bbandsPeriodField.getText());
                    techniques.add(new BollingerBandsTechnique(bbPeriod, 2.0));
                }

                // --- 4. Update UI on JavaFX Application Thread ---
                Platform.runLater(() -> {
                    updateChart(result, techniques);
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

    private PositionSizer createPositionSizerFromUI() {
        try {
            String selectedSizer = positionSizerComboBox.getSelectionModel().getSelectedItem();
            double param = Double.parseDouble(sizerParamField.getText());
            return switch (selectedSizer) {
                case CASH_PERCENTAGE_SIZER -> new CashPercentagePositionSizer(param / 100.0);
                case FIXED_QUANTITY_SIZER -> new FixedQuantityPositionSizer((int) param);
                case FIXED_CASH_QUANTITY_SIZER -> new FixedCashQuantityPositionSizer(param);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateSummary(BacktestResult result) {
        if (result.finalPortfolio() instanceof BasicPortfolio bp) {
            summaryArea.setText(bp.getSummary());
        }
    }

    private void updateTradeLog(BacktestResult result) {
        tradeLogTable.getItems().clear();
        tradeLogTable.getItems().addAll(result.executedOrders());
    }

    /**
     * Updates the chart with price, selected indicators, and trade signals.
     * This version relies on the CSS default color series (.default-colorN) for indicator styling,
     * which is simple and robust.
     */
    private void updateChart(BacktestResult result, List<AnalysisTechnique> techniques) {
        priceChart.getData().clear();
        BarSeries series = result.series();
        if (series.isEmpty()) return;

        // The order of adding series matters. CSS will apply .default-color0, .default-color1, etc.
        // in the order the series are added to the chart.

        // 1. Add the base price series. It will get the `.default-color0` style.
        if (showCandlestickCheck.isSelected()) {
            // If Candlestick is chosen, add its series
            priceChart.getData().addAll(new CandlestickTechnique().calculate(series));
        } else {
            // Otherwise, add the simple close price line
            priceChart.getData().add(createClosePriceSeries(series));
        }

        // 2. Add all selected technical analysis indicator series
        for (AnalysisTechnique technique : techniques) {
            priceChart.getData().addAll(technique.calculate(series));
        }

        // 3. Add buy and sell signal series. They have their own specific CSS classes.
        XYChart.Series<String, Number> buySignalsSeries = createSignalSeries("买入点", result.executedOrders(), TradeSignal.BUY);
        XYChart.Series<String, Number> sellSignalsSeries = createSignalSeries("卖出点", result.executedOrders(), TradeSignal.SELL);

        addCssListenerToSeries(buySignalsSeries, "buy-signal-symbol");
        addCssListenerToSeries(sellSignalsSeries, "sell-signal-symbol");

        priceChart.getData().addAll(buySignalsSeries, sellSignalsSeries);
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

    /**
     * Robustly applies a CSS class to a series' data points by listening for when their nodes are created.
     * This is the best practice for styling chart data points.
     */
    private void addCssListenerToSeries(XYChart.Series<String, Number> series, String cssClass) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.getStyleClass().add(cssClass);
                }
            });
        }
    }
}