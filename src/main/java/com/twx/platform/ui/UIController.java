package com.twx.platform.ui;

import com.twx.platform.ai.AIAssistant;
import com.twx.platform.analysis.impl.*;
import com.twx.platform.common.*;
import com.twx.platform.data.DataProvider;
import com.twx.platform.data.impl.SinaDataProvider;
import com.twx.platform.engine.BacktestEngine;
import com.twx.platform.engine.BacktestResult;
import com.twx.platform.portfolio.Portfolio;
import com.twx.platform.portfolio.impl.BasicPortfolio;
import com.twx.platform.position.PositionSizer;
import com.twx.platform.position.impl.*;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class UIController {

    private Stage stage;

    @FXML private BorderPane rootPane;
    @FXML private TextField tickerField, initialCashField, sizerParamField;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private ComboBox<String> positionSizerComboBox;
    @FXML private Label sizerParamLabel;
    @FXML private Button runButton;
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private StackPane menuButton;
    @FXML private ToggleButton aiAssistantToggle, chartSettingsToggle, resultsToggle;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;

    private Node coreContent;
    private BorderPane mainArea;
    private AIAssistant aiAssistantPanel;
    private Node chartSettingsPanel, resultsPanel;
    private CheckBox showCandlestickCheck, showMaCheck, showMacdCheck, showRsiCheck, showBbCheck;
    private TextField shortMaField, longMaField, rsiPeriodField, bbandsPeriodField;
    private TextArea summaryArea;
    private TableView<Order> tradeLogTable;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private BacktestResult lastBacktestResult;
    private final Map<String, List<XYChart.Series<Number, Number>>> indicatorSeriesMap = new HashMap<>();
    private final List<XYChart.Series<Number, Number>> candlestickSeries = new ArrayList<>();
    private XYChart.Series<Number, Number> tradeSignalSeries = null;
    private ContextMenu mainMenu;

    private static final String SIZER_CASH_PERCENT = "按资金百分比";
    private static final String SIZER_FIXED_QTY = "按固定股数";
    private static final String SIZER_FIXED_CASH = "按固定资金";
    private static final int TARGET_Y_AXIS_TICK_COUNT = 12;
    private static final int TARGET_X_AXIS_LABEL_COUNT = 15;

    private boolean isChartPopulated = false;

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        coreContent = rootPane.getCenter();
        mainArea = new BorderPane(coreContent);
        rootPane.setCenter(mainArea);

        startDatePicker.setValue(LocalDate.of(2024, 1, 1));
        endDatePicker.setValue(LocalDate.now());

        setupXAxisLabelFormatter();

        loadDynamicPanels();
        createMainMenu();
        setupPanelToggles();
        initializePositionSizerControls();
    }

    private void setupPanelToggles() {
        aiAssistantToggle.selectedProperty().addListener((obs, ov, show) -> mainArea.setLeft(show ? aiAssistantPanel : null));
        chartSettingsToggle.selectedProperty().addListener((obs, ov, show) -> mainArea.setRight(show ? chartSettingsPanel : null));
        resultsToggle.selectedProperty().addListener((obs, ov, show) -> mainArea.setBottom(show ? resultsPanel : null));
    }

    private void redrawChart() {
        if (!isChartPopulated) return;
        priceChart.getData().clear();
        if (showCandlestickCheck.isSelected()) {
            priceChart.getData().addAll(candlestickSeries);
        } else {
            candlestickSeries.stream()
                    .filter(s -> "收盘价".equals(s.getName()))
                    .findFirst()
                    .ifPresent(s -> priceChart.getData().add(s));
        }
        if (showMaCheck.isSelected()) {
            priceChart.getData().addAll(indicatorSeriesMap.get("MA"));
        }
        if (showMacdCheck.isSelected()) {
            priceChart.getData().addAll(indicatorSeriesMap.get("MACD"));
        }
        if (showRsiCheck.isSelected()) {
            priceChart.getData().addAll(indicatorSeriesMap.get("RSI"));
        }
        if (showBbCheck.isSelected()) {
            priceChart.getData().addAll(indicatorSeriesMap.get("BB"));
        }
        if (tradeSignalSeries != null) {
            priceChart.getData().add(tradeSignalSeries);
        }
        adjustAxisRanges();
    }

    private void populateChartFirstTime() {
        if (lastBacktestResult == null) return;
        priceChart.getData().clear();
        priceChart.getData().addAll(candlestickSeries);
        candlestickSeries.forEach(s -> { if(s.getNode() != null) s.getNode().setVisible(false); });
        for(List<XYChart.Series<Number, Number>> seriesList : indicatorSeriesMap.values()){
            priceChart.getData().addAll(seriesList);
            seriesList.forEach(s -> { if(s.getNode() != null) s.getNode().setVisible(false); });
        }
        updateTradeSignalsOnChart();
        isChartPopulated = true;
        redrawChart();
    }

    private void setupXAxisLabelFormatter() {
        xAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return LocalDate.ofEpochDay(object.longValue()).format(DATE_FORMATTER);
            }
            @Override
            public Number fromString(String string) {
                return LocalDate.parse(string, DATE_FORMATTER).toEpochDay();
            }
        });
    }

    private void loadDynamicPanels() {
        try {
            // --- 加载 AI 助手面板 ---
            aiAssistantPanel = new AIAssistant(); // 使用无参数构造函数
            aiAssistantPanel.getStyleClass().add("ai-assistant-panel");

            // --- 加载图表设置面板 ---
            FXMLLoader chartSettingsLoader = new FXMLLoader(getClass().getResource("chart-settings-panel.fxml"));
            // 【重要】不要调用 setController()
            chartSettingsPanel = chartSettingsLoader.load();
            // 通过 Namespace 获取控件，这是正确且安全的方式
            initializeChartSettingsControls(chartSettingsLoader.getNamespace());

            // --- 加载结果面板 ---
            FXMLLoader resultsLoader = new FXMLLoader(getClass().getResource("results-panel.fxml"));
            // 【重要】不要调用 setController()
            resultsPanel = resultsLoader.load();
            initializeResultsControls(resultsLoader.getNamespace());

        } catch (IOException e) {
            e.printStackTrace();
            // 在这里可以弹出一个错误对话框，提示用户UI资源加载失败
        }
    }

    @FXML
    private void handleRunBacktest() {
        rootPane.requestFocus();
        runButton.setDisable(true);
        isChartPopulated = false;
        if (summaryArea != null) summaryArea.setText("正在运行回测，请稍候...");
        if (tradeLogTable != null) tradeLogTable.getItems().clear();
        new Thread(() -> {
            try {
                DataProvider dataProvider = new SinaDataProvider();
                Ticker ticker = new Ticker(tickerField.getText());
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                BarSeries series = dataProvider.getHistoricalData(ticker, startDate, endDate, TimeFrame.DAILY);
                if (series == null || series.isEmpty()) {
                    Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("无法获取数据。"); });
                    return;
                }
                Portfolio portfolio = new BasicPortfolio(Double.parseDouble(initialCashField.getText()), 0.0003);
                Strategy strategy = new MovingAverageCrossStrategy(series, Integer.parseInt(shortMaField.getText()), Integer.parseInt(longMaField.getText()));
                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);
                lastBacktestResult = engine.run(strategy, portfolio, createPositionSizerFromUI());
                cacheAllChartData(lastBacktestResult.series());
                Platform.runLater(() -> {
                    populateChartFirstTime();
                    updateSummaryAndLog(lastBacktestResult);
                    if (aiAssistantPanel != null) {
                        aiAssistantPanel.updateAnalysisContext(lastBacktestResult, strategy);}
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("发生错误: \n" + e.getMessage()); });
            } finally {
                Platform.runLater(() -> runButton.setDisable(false));
            }
        }).start();
    }

    private void cacheAllChartData(BarSeries series) {
        candlestickSeries.clear();
        candlestickSeries.addAll(createCandlestickSeries(series));
        indicatorSeriesMap.clear();
        List<XYChart.Series<Number, Number>> maSeries = new MovingAverageTechnique(Integer.parseInt(shortMaField.getText()), Integer.parseInt(longMaField.getText())).calculate(series);
        setStyleClassForSeries(maSeries.get(0), "ma-short-series");
        setStyleClassForSeries(maSeries.get(1), "ma-long-series");
        indicatorSeriesMap.put("MA", maSeries);
        List<XYChart.Series<Number, Number>> macdSeries = new MacdTechnique(12, 26, 9).calculate(series);
        setStyleClassForSeries(macdSeries.get(0), "macd-series");
        setStyleClassForSeries(macdSeries.get(1), "macd-signal-series");
        indicatorSeriesMap.put("MACD", macdSeries);
        List<XYChart.Series<Number, Number>> rsiSeries = new RsiTechnique(Integer.parseInt(rsiPeriodField.getText())).calculate(series);
        setStyleClassForSeries(rsiSeries.get(0), "rsi-series");
        indicatorSeriesMap.put("RSI", rsiSeries);
        List<XYChart.Series<Number, Number>> bbSeries = new BollingerBandsTechnique(Integer.parseInt(bbandsPeriodField.getText()), 2.0).calculate(series);
        setStyleClassForSeries(bbSeries.get(0), "bb-middle-series");
        setStyleClassForSeries(bbSeries.get(1), "bb-upper-series");
        setStyleClassForSeries(bbSeries.get(2), "bb-lower-series");
        indicatorSeriesMap.put("BB", bbSeries);
    }

    private void adjustAxisRanges() {
        if (!isChartPopulated) return;
        double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
        double minDay = Double.MAX_VALUE, maxDay = Double.MIN_VALUE;
        for (XYChart.Series<Number, Number> series : priceChart.getData()) {
            if (series.getNode() == null || !series.getNode().isVisible() || series == tradeSignalSeries) continue;
            for (XYChart.Data<Number, Number> data : series.getData()) {
                double yValue = data.getYValue().doubleValue();
                if (yValue < minPrice) minPrice = yValue;
                if (yValue > maxPrice) maxPrice = yValue;
                double xValue = data.getXValue().doubleValue();
                if (xValue < minDay) minDay = xValue;
                if (xValue > maxDay) maxDay = xValue;
            }
        }
        if (minPrice == Double.MAX_VALUE) { minPrice = 0; maxPrice = 100; }
        yAxis.setAutoRanging(false);
        double paddingY = (maxPrice - minPrice) * 0.05;
        yAxis.setLowerBound(Math.floor(minPrice - paddingY));
        yAxis.setUpperBound(Math.ceil(maxPrice + paddingY));
        updateAxisTickUnit(yAxis, TARGET_Y_AXIS_TICK_COUNT);
        if (minDay == Double.MAX_VALUE) return;
        xAxis.setAutoRanging(false);
        double paddingX = (maxDay - minDay) * 0.01;
        xAxis.setLowerBound(minDay - paddingX);
        xAxis.setUpperBound(maxDay + paddingX);
        updateAxisTickUnit(xAxis, TARGET_X_AXIS_LABEL_COUNT);
    }

    private void updateAxisTickUnit(NumberAxis axis, int targetTickCount) {
        double range = axis.getUpperBound() - axis.getLowerBound();
        if (range <= 0) return;
        double rawTickUnit = range / targetTickCount;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawTickUnit)));
        double residual = rawTickUnit / magnitude;
        double niceTickUnit;
        if (residual > 5) niceTickUnit = 10 * magnitude;
        else if (residual > 2) niceTickUnit = 5 * magnitude;
        else if (residual > 1) niceTickUnit = 2 * magnitude;
        else niceTickUnit = magnitude;
        axis.setTickUnit(Math.max(niceTickUnit, 1.0));
    }

    private void updateTradeSignalsOnChart() {
        if (tradeSignalSeries != null) {
            priceChart.getData().remove(tradeSignalSeries);
        }
        if (lastBacktestResult != null && !lastBacktestResult.executedOrders().isEmpty()) {
            tradeSignalSeries = createTradeSignalSeries(lastBacktestResult.executedOrders());
            priceChart.getData().add(tradeSignalSeries);
            Platform.runLater(() -> {
                for (XYChart.Data<Number, Number> data : tradeSignalSeries.getData()) {
                    if (data.getNode() != null && data.getExtraValue() instanceof Order o) {
                        String styleClass = o.signal() == TradeSignal.BUY ? "buy-signal-symbol" : "sell-signal-symbol";
                        data.getNode().getStyleClass().add(styleClass);
                    }
                }
            });
        }
    }

    // 在 UIController.java 中

    private void createMainMenu() {
        mainMenu = new ContextMenu();
        mainMenu.getStyleClass().add("main-menu");
        MenuItem apiKeyMenuItem = createMenuItem("设置 API Key", e -> showApiKeyDialog());
        mainMenu.getItems().add(apiKeyMenuItem);
        CheckMenuItem themeToggle = new CheckMenuItem("切换暗色模式");
        themeToggle.selectedProperty().addListener((obs, ov, isDark) -> {
            if (isDark) rootPane.getStyleClass().add("theme-dark");
            else rootPane.getStyleClass().remove("theme-dark");
        });
        mainMenu.getItems().addAll(
                new SeparatorMenuItem(),
                themeToggle,
                new SeparatorMenuItem(),
                createMenuItem("退出", e -> Platform.exit())
        );
    }

    // 在 UIController.java 中

    private void showApiKeyDialog() {
        TextInputDialog dialog = new TextInputDialog(ConfigurationManager.getInstance().getKimiApiKey());
        dialog.setTitle("API Key 设置");
        dialog.setHeaderText("请输入您的 Kimi API Key");
        dialog.setContentText("API Key:");
        if (stage != null) {
            dialog.initOwner(stage);
        }
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(apiKey -> {
            ConfigurationManager.getInstance().setKimiApiKey(apiKey.trim());
             Alert alert = new Alert(Alert.AlertType.INFORMATION, "API Key 已保存。");
             alert.showAndWait();
        });
    }

    private void initializeChartSettingsControls(Map<String, Object> namespace) {
        showCandlestickCheck = (CheckBox) namespace.get("showCandlestickCheck");
        showMaCheck = (CheckBox) namespace.get("showMaCheck");
        showMacdCheck = (CheckBox) namespace.get("showMacdCheck");
        showRsiCheck = (CheckBox) namespace.get("showRsiCheck");
        showBbCheck = (CheckBox) namespace.get("showBbCheck");
        shortMaField = (TextField) namespace.get("shortMaField");
        longMaField = (TextField) namespace.get("longMaField");
        rsiPeriodField = (TextField) namespace.get("rsiPeriodField");
        bbandsPeriodField = (TextField) namespace.get("bbandsPeriodField");
        Stream.of(showCandlestickCheck, showMaCheck, showMacdCheck, showRsiCheck, showBbCheck)
                .forEach(cb -> cb.selectedProperty().addListener((obs, ov, nv) -> redrawChart()));
    }

    private void initializeResultsControls(Map<String, Object> namespace) {
        summaryArea = (TextArea) namespace.get("summaryArea");
        tradeLogTable = (TableView<Order>) namespace.get("tradeLogTable");
        TableColumn<Order, String> dateCol = (TableColumn<Order, String>) namespace.get("dateColumn");
        dateCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().timestamp().format(DATE_FORMATTER)));
        TableColumn<Order, String> signalCol = (TableColumn<Order, String>) namespace.get("signalColumn");
        signalCol.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue().signal()).asString());
        TableColumn<Order, Double> priceCol = (TableColumn<Order, Double>) namespace.get("priceColumn");
        priceCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().price()).asObject());
        TableColumn<Order, Double> quantityCol = (TableColumn<Order, Double>) namespace.get("quantityColumn");
        quantityCol.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().quantity()).asObject());
        TableColumn<Order, String> valueCol = (TableColumn<Order, String>) namespace.get("valueColumn");
        valueCol.setCellValueFactory(cell -> {
            Order order = cell.getValue();
            return new SimpleStringProperty(String.format("%,.2f", order.price() * order.quantity()));
        });
    }

    private void initializePositionSizerControls() {
        positionSizerComboBox.getItems().addAll(SIZER_CASH_PERCENT, SIZER_FIXED_QTY, SIZER_FIXED_CASH);
        positionSizerComboBox.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv == null) return;
            switch (nv) {
                case SIZER_CASH_PERCENT -> { sizerParamLabel.setText("资金比例(%):"); sizerParamField.setText("15.0"); }
                case SIZER_FIXED_QTY -> { sizerParamLabel.setText("固定股数:"); sizerParamField.setText("100"); }
                case SIZER_FIXED_CASH -> { sizerParamLabel.setText("固定资金:"); sizerParamField.setText("1000"); }
            }
        });
        positionSizerComboBox.getSelectionModel().selectFirst();
    }

    private PositionSizer createPositionSizerFromUI() {
        try {
            double param = Double.parseDouble(sizerParamField.getText());
            return switch (positionSizerComboBox.getSelectionModel().getSelectedItem()) {
                case SIZER_CASH_PERCENT -> new CashPercentagePositionSizer(param / 100.0);
                case SIZER_FIXED_QTY -> new FixedQuantityPositionSizer((int) param);
                case SIZER_FIXED_CASH -> new FixedCashQuantityPositionSizer(param);
                default -> new FixedQuantityPositionSizer(100);
            };
        } catch (NumberFormatException e) {
            Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("仓位参数无效。"); });
            return new FixedQuantityPositionSizer(100);
        }
    }

    private void updateSummaryAndLog(BacktestResult result) {
        if (result.finalPortfolio() instanceof BasicPortfolio bp) {
            summaryArea.setText(bp.getSummary());
        }
        tradeLogTable.getItems().setAll(result.executedOrders());
    }

    private List<XYChart.Series<Number, Number>> createCandlestickSeries(BarSeries series) {
        XYChart.Series<Number, Number> close = new XYChart.Series<>();
        close.setName("收盘价");
        setStyleClassForSeries(close, "close-price-series");
        XYChart.Series<Number, Number> open = new XYChart.Series<>(), high = new XYChart.Series<>(), low = new XYChart.Series<>();
        open.setName("开盘价"); high.setName("最高价"); low.setName("最低价");
        for (Bar bar : series.getBarData()) {
            long day = bar.getEndTime().toLocalDate().toEpochDay();
            close.getData().add(new XYChart.Data<>(day, bar.getClosePrice().doubleValue()));
            open.getData().add(new XYChart.Data<>(day, bar.getOpenPrice().doubleValue()));
            high.getData().add(new XYChart.Data<>(day, bar.getHighPrice().doubleValue()));
            low.getData().add(new XYChart.Data<>(day, bar.getLowPrice().doubleValue()));
        }
        return List.of(close, open, high, low);
    }

    private XYChart.Series<Number, Number> createTradeSignalSeries(List<Order> orders) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("交易点连线");
        setStyleClassForSeries(series, "trade-signal-series");
        for (Order order : orders) {
            long day = order.timestamp().toLocalDate().toEpochDay();
            XYChart.Data<Number, Number> data = new XYChart.Data<>(day, order.price());
            data.setExtraValue(order);
            series.getData().add(data);
        }
        return series;
    }

    private void setStyleClassForSeries(XYChart.Series<Number, Number> series, String styleClass) {
        series.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.getStyleClass().add(styleClass);
            }
        });
    }

    private MenuItem createMenuItem(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    @FXML
    private void handleMenuButtonClick(MouseEvent e) {
        mainMenu.show(menuButton, Side.RIGHT, 5, 0);
    }
}