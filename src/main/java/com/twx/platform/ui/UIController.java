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
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIController {

    // --- FXML UI Elements ---
    @FXML
    private BorderPane rootPane;
    @FXML
    private TextField tickerField;
    @FXML
    private TextField initialCashField;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TextField shortMaField;
    @FXML
    private TextField longMaField;
    @FXML
    private ComboBox<String> positionSizerComboBox;
    @FXML
    private Label sizerParamLabel;
    @FXML
    private TextField sizerParamField;
    @FXML
    private Button runButton;
    @FXML
    private ToggleButton themeToggleButton;

    // Chart and Analysis Options
    @FXML
    private LineChart<String, Number> priceChart;
    @FXML
    private CheckBox showCandlestickCheck;
    @FXML
    private CheckBox showMaCheck;
    @FXML
    private CheckBox showMacdCheck;
    @FXML
    private CheckBox showRsiCheck;
    @FXML
    private CheckBox showBbCheck;
    @FXML
    private TextField rsiPeriodField;
    @FXML
    private TextField bbandsPeriodField;

    // Result Display
    @FXML
    private TextArea summaryArea;
    @FXML
    private TableView<Order> tradeLogTable;
    @FXML
    private TableColumn<Order, String> dateColumn;
    @FXML
    private TableColumn<Order, String> signalColumn;
    @FXML
    private TableColumn<Order, Double> priceColumn;
    @FXML
    private TableColumn<Order, Double> quantityColumn;
    @FXML
    private TableColumn<Order, String> valueColumn;

    // --- Constants ---
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CASH_PERCENTAGE_SIZER = "按资金百分比";
    private static final String FIXED_CASH_QUANTITY_SIZER = "按固定资金";
    private static final String FIXED_QUANTITY_SIZER = "按固定股数";

    // --- Data Caching ---
    private BacktestResult lastBacktestResult;
    private final Map<String, List<XYChart.Series<String, Number>>> indicatorSeriesMap = new HashMap<>();
    // ★ 新增：专门用于缓存K线图相关的系列数据
    private final List<XYChart.Series<String, Number>> candlestickSeries = new ArrayList<>();


    @FXML
    public void initialize() {
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());
        initializeTable();
        initializePositionSizerControls();
        setupIndicatorListeners();
    }

    private void setupIndicatorListeners() {
        showCandlestickCheck.setOnAction(event -> redrawChart());
        showMaCheck.setOnAction(event -> redrawChart());
        showMacdCheck.setOnAction(event -> redrawChart());
        showRsiCheck.setOnAction(event -> redrawChart());
        showBbCheck.setOnAction(event -> redrawChart());
    }

    // ... (initializeTable, initializePositionSizerControls, handleThemeToggle 无需修改)
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
                // ... (获取参数部分与之前相同)
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                int shortMa = Integer.parseInt(shortMaField.getText());
                int longMa = Integer.parseInt(longMaField.getText());
                int rsiPeriod = Integer.parseInt(rsiPeriodField.getText());
                int bbPeriod = Integer.parseInt(bbandsPeriodField.getText());
                PositionSizer positionSizer = createPositionSizerFromUI();

                // ... (运行回测部分与之前相同)
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
                this.lastBacktestResult = engine.run(strategy, portfolio, positionSizer);

                // --- ★ 关键修改：一次性计算所有图表数据并缓存 ---
                cacheAllChartData(series, shortMa, longMa, rsiPeriod, bbPeriod);

                // --- 在UI线程更新 ---
                Platform.runLater(() -> {
                    updateSummary(this.lastBacktestResult);
                    updateTradeLog(this.lastBacktestResult);
                    redrawChart(); // 首次绘制
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
     * ★ 新增辅助方法：一次性计算并缓存所有可能用到的图表系列数据
     */
    private void cacheAllChartData(BarSeries series, int shortMa, int longMa, int rsiPeriod, int bbPeriod) {
        // 1. 缓存K线图数据
        candlestickSeries.clear();
        candlestickSeries.addAll(createCandlestickSeries(series));

        // 2. 缓存所有技术指标
        indicatorSeriesMap.clear();
        indicatorSeriesMap.put("MA", new MovingAverageTechnique(shortMa, longMa).calculate(series));
        indicatorSeriesMap.put("MACD", new MacdTechnique(12, 26, 9).calculate(series));
        indicatorSeriesMap.put("RSI", new RsiTechnique(rsiPeriod).calculate(series));
        indicatorSeriesMap.put("BB", new BollingerBandsTechnique(bbPeriod, 2.0).calculate(series));
    }

    /**
     * ★ 重构的核心：根据复选框状态和缓存数据重绘图表
     */
    private void redrawChart() {
        priceChart.getData().clear();

        if (lastBacktestResult == null || lastBacktestResult.series() == null) {
            return;
        }

        // 1. 根据复选框决定是绘制K线图还是收盘价线
        if (showCandlestickCheck.isSelected()) {
            // 添加缓存的K线系列数据
            if (!candlestickSeries.isEmpty()) {
                priceChart.getData().addAll(candlestickSeries);
            }
        } else {
            // 绘制传统的收盘价线 (从缓存的K线数据中提取)
            // 我们从candlestickSeries的收盘价系列中提取数据，确保数据源一致
            for (XYChart.Series<String, Number> series : candlestickSeries) {
                if ("收盘价".equals(series.getName())) {
                    priceChart.getData().add(series);
                    break;
                }
            }
        }

        // 2. 添加选中的技术指标
        if (showMaCheck.isSelected() && indicatorSeriesMap.containsKey("MA")) {
            priceChart.getData().addAll(indicatorSeriesMap.get("MA"));
        }
        if (showMacdCheck.isSelected() && indicatorSeriesMap.containsKey("MACD")) {
            priceChart.getData().addAll(indicatorSeriesMap.get("MACD"));
        }
        if (showRsiCheck.isSelected() && indicatorSeriesMap.containsKey("RSI")) {
            priceChart.getData().addAll(indicatorSeriesMap.get("RSI"));
        }
        if (showBbCheck.isSelected() && indicatorSeriesMap.containsKey("BB")) {
            priceChart.getData().addAll(indicatorSeriesMap.get("BB"));
        }

        // 3. 总是添加交易点连线
        if (lastBacktestResult.executedOrders() != null && !lastBacktestResult.executedOrders().isEmpty()) {
            XYChart.Series<String, Number> tradeSeries = createTradeSignalSeries("交易点连线", lastBacktestResult.executedOrders());
            priceChart.getData().add(tradeSeries);

            // ★★★ 最终修复逻辑 ★★★
            // 在UI线程的下一个布局周期中，当LineChart已经创建并设置好默认节点和样式后，
            // 我们再来应用我们自定义的样式。
            Platform.runLater(() -> {
                for (XYChart.Data<String, Number> data : tradeSeries.getData()) {
                    if (data.getNode() != null) {
                        // 从数据点中恢复 Order 对象
                        Object extraValue = data.getExtraValue();
                        if (extraValue instanceof Order) {
                            Order order = (Order) extraValue;

                            // 确定应该应用哪个CSS类
                            String symbolCssClass = (order.signal() == TradeSignal.BUY)
                                    ? "buy-signal-symbol"
                                    : "sell-signal-symbol";

                            // ★ 核心操作：在默认样式的基础上，添加我们的自定义样式类
                            // 我们不再移除 chart-line-symbol，而是与之共存
                            data.getNode().getStyleClass().add(symbolCssClass);

                            // 将节点置于顶层，防止被线条遮挡
                            data.getNode().toFront();
                        }
                    }
                }
            });
        }

        // 4. 动态调整Y轴
        adjustYAxisRange();
    }

    /**
     * ★ 全新方法：创建模拟K线图的数据系列
     * 返回一个包含高、开、收、低四条线的列表
     */
    private List<XYChart.Series<String, Number>> createCandlestickSeries(BarSeries series) {
        XYChart.Series<String, Number> highSeries = new XYChart.Series<>();
        highSeries.setName("最高价");
        XYChart.Series<String, Number> openSeries = new XYChart.Series<>();
        openSeries.setName("开盘价");
        XYChart.Series<String, Number> closeSeries = new XYChart.Series<>();
        closeSeries.setName("收盘价");
        XYChart.Series<String, Number> lowSeries = new XYChart.Series<>();
        lowSeries.setName("最低价");

        // 为每个系列添加CSS类，以便在样式表中定义不同颜色
        // 我们利用默认颜色序列来简化CSS
        Platform.runLater(() -> {
            if (highSeries.getNode() != null) highSeries.getNode().getStyleClass().add("default-color0");
            if (openSeries.getNode() != null) openSeries.getNode().getStyleClass().add("default-color1");
            if (closeSeries.getNode() != null) closeSeries.getNode().getStyleClass().add("default-color2");
            if (lowSeries.getNode() != null) lowSeries.getNode().getStyleClass().add("default-color3");
        });


        for (int i = 0; i < series.getBarCount(); i++) {
            Bar bar = series.getBar(i);
            String date = bar.getEndTime().toLocalDate().toString();

            highSeries.getData().add(new XYChart.Data<>(date, bar.getHighPrice().doubleValue()));
            openSeries.getData().add(new XYChart.Data<>(date, bar.getOpenPrice().doubleValue()));
            closeSeries.getData().add(new XYChart.Data<>(date, bar.getClosePrice().doubleValue()));
            lowSeries.getData().add(new XYChart.Data<>(date, bar.getLowPrice().doubleValue()));
        }

        return List.of(highSeries, openSeries, closeSeries, lowSeries);
    }

    private void adjustYAxisRange() {
        Platform.runLater(() -> {
            double minPrice = Double.MAX_VALUE;
            double maxPrice = Double.MIN_VALUE;

            for (XYChart.Series<String, Number> s : priceChart.getData()) {
                // 排除交易点连线，因为它可能包含非价格数据或拉低范围
                if ("交易点连线".equals(s.getName())) continue;
                for (XYChart.Data<String, Number> d : s.getData()) {
                    double yValue = d.getYValue().doubleValue();
                    if (yValue < minPrice) minPrice = yValue;
                    if (yValue > maxPrice) maxPrice = yValue;
                }
            }

            if (minPrice != Double.MAX_VALUE) {
                NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
                yAxis.setAutoRanging(false);
                double padding = (maxPrice - minPrice) * 0.05;
                yAxis.setLowerBound(Math.floor(minPrice - padding));
                yAxis.setUpperBound(Math.ceil(maxPrice + padding));
            }
        });
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

    // 在 UIController.java 中
    private XYChart.Series<String, Number> createTradeSignalSeries(String name, List<Order> executedOrders) {
        XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
        dataSeries.setName(name);

        // 应用虚线样式到连接线上
        dataSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.getStyleClass().add("trade-signal-series");
            }
        });

        for (Order order : executedOrders) {
            String date = order.timestamp().toLocalDate().toString();
            XYChart.Data<String, Number> data = new XYChart.Data<>(date, order.price());

            // ★ 关键修改：将整个 Order 对象附加到数据点上
            data.setExtraValue(order);

            dataSeries.getData().add(data);
        }
        return dataSeries;
    }
}