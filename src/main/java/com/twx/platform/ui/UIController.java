package com.twx.platform.ui;

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
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UIController {

    // --- 窗口控制与交互 ---
    private Stage stage;
    private double xOffset, yOffset;
    private double startX, startY, startStageX, startStageY, startWidth, startHeight;
    private boolean isResizing = false;
    private ResizeMode resizeMode = ResizeMode.NONE;
    private Rectangle2D backupWindowBounds = null;
    private enum ResizeMode { NONE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    // --- FXML UI Elements ---
    @FXML private BorderPane rootPane;
    @FXML private TextField tickerField, initialCashField, shortMaField, longMaField, sizerParamField, rsiPeriodField, bbandsPeriodField;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private ComboBox<String> positionSizerComboBox;
    @FXML private Label sizerParamLabel;
    @FXML private Button runButton;
    @FXML private ToggleButton themeToggleButton;
    @FXML private LineChart<String, Number> priceChart;
    @FXML private CheckBox showCandlestickCheck, showMaCheck, showMacdCheck, showRsiCheck, showBbCheck;
    @FXML private TextArea summaryArea;
    @FXML private TableView<Order> tradeLogTable;
    @FXML private TableColumn<Order, String> dateColumn, signalColumn, valueColumn;
    @FXML private TableColumn<Order, Double> priceColumn, quantityColumn;
    @FXML private HBox customTitleBar;
    @FXML private SVGPath maximizeIcon, restoreIcon;

    // --- 常量与数据缓存 ---
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String CASH_PERCENTAGE_SIZER = "按资金百分比", FIXED_CASH_QUANTITY_SIZER = "按固定资金", FIXED_QUANTITY_SIZER = "按固定股数";
    private BacktestResult lastBacktestResult;
    private final Map<String, List<XYChart.Series<String, Number>>> indicatorSeriesMap = new HashMap<>();
    private final List<XYChart.Series<String, Number>> candlestickSeries = new ArrayList<>();
    // ★ 性能优化：专门存储交易信号系列，方便移除
    private XYChart.Series<String, Number> tradeSignalSeries = null;


    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());
        initializeTable();
        initializePositionSizerControls();
        setupIndicatorListeners();
        setupWindowControls();
    }

    // --- 主业务逻辑 ---
    @FXML
    private void handleRunBacktest() {
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...");
        tradeLogTable.getItems().clear();
        new Thread(() -> {
            try {
                // ... [数据获取和回测引擎运行部分保持不变] ...
                String tickerSymbol = tickerField.getText();
                LocalDate startDate = startDatePicker.getValue();
                LocalDate endDate = endDatePicker.getValue();
                double initialCash = Double.parseDouble(initialCashField.getText());
                int shortMa = Integer.parseInt(shortMaField.getText());
                int longMa = Integer.parseInt(longMaField.getText());
                int rsiPeriod = Integer.parseInt(rsiPeriodField.getText());
                int bbPeriod = Integer.parseInt(bbandsPeriodField.getText());
                PositionSizer positionSizer = createPositionSizerFromUI();
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

                cacheAllChartData(series, shortMa, longMa, rsiPeriod, bbPeriod);

                Platform.runLater(() -> {
                    // ★ 性能优化：不是调用redrawChart()，而是调用一个全新的、高效的首次绘制方法
                    populateChartFirstTime();
                    updateSummary(this.lastBacktestResult);
                    updateTradeLog(this.lastBacktestResult);
                });
            } catch (Exception e) {
                Platform.runLater(() -> summaryArea.setText("发生错误: \n" + e.getClass().getSimpleName() + "\n" + e.getMessage()));
            } finally {
                Platform.runLater(() -> runButton.setDisable(false));
            }
        }).start();
    }

    /**
     * ★ 性能优化：全新的、只在首次运行回测时调用的方法
     * 它会清空并完全重建图表。
     */
    private void populateChartFirstTime() {
        priceChart.getData().clear();
        if (lastBacktestResult == null) return;

        // 1. 添加基础价格图
        if (showCandlestickCheck.isSelected()) {
            priceChart.getData().addAll(candlestickSeries);
        } else {
            candlestickSeries.stream().filter(s -> "收盘价".equals(s.getName())).findFirst().ifPresent(priceChart.getData()::add);
        }

        // 2. 根据复选框状态添加所有选中的指标
        if (showMaCheck.isSelected()) priceChart.getData().addAll(indicatorSeriesMap.get("MA"));
        if (showMacdCheck.isSelected()) priceChart.getData().addAll(indicatorSeriesMap.get("MACD"));
        if (showRsiCheck.isSelected()) priceChart.getData().addAll(indicatorSeriesMap.get("RSI"));
        if (showBbCheck.isSelected()) priceChart.getData().addAll(indicatorSeriesMap.get("BB"));

        // 3. 添加交易信号
        updateTradeSignalsOnChart();

        adjustYAxisRange();
    }

    /**
     * ★ 性能优化：重写 redrawChart()，现在它只处理复选框的点击事件，进行增量更新。
     */
    private void redrawChart() {
        if (lastBacktestResult == null) return; // 确保有数据才能重绘

        // --- 1. 更新基础价格图 ---
        // 找出当前图表上所有K线/收盘价系列
        List<XYChart.Series<String, Number>> currentBaseSeries = priceChart.getData().stream()
                .filter(s -> "收盘价".equals(s.getName()) || "开盘价".equals(s.getName()) || "最高价".equals(s.getName()) || "最低价".equals(s.getName()))
                .collect(Collectors.toList());

        priceChart.getData().removeAll(currentBaseSeries); // 先移除旧的

        if (showCandlestickCheck.isSelected()) { // 添加新的
            priceChart.getData().addAll(0, candlestickSeries);
        } else {
            candlestickSeries.stream().filter(s -> "收盘价".equals(s.getName())).findFirst().ifPresent(s -> priceChart.getData().add(0, s));
        }

        // --- 2. 增量更新技术指标 ---
        updateSeriesVisibility("MA", showMaCheck.isSelected());
        updateSeriesVisibility("MACD", showMacdCheck.isSelected());
        updateSeriesVisibility("RSI", showRsiCheck.isSelected());
        updateSeriesVisibility("BB", showBbCheck.isSelected());
    }

    /**
     * ★ 性能优化：辅助方法，用于精确地添加或移除一个指标系列
     */
    private void updateSeriesVisibility(String key, boolean shouldBeVisible) {
        List<XYChart.Series<String, Number>> seriesToAddOrRemove = indicatorSeriesMap.get(key);
        if (seriesToAddOrRemove == null || seriesToAddOrRemove.isEmpty()) return;

        if (shouldBeVisible) {
            // 只有当图表中不存在该系列时才添加
            for(XYChart.Series<String, Number> series : seriesToAddOrRemove) {
                if (!priceChart.getData().contains(series)) {
                    priceChart.getData().add(series);
                }
            }
        } else {
            // 如果图表中存在该系列，则移除
            priceChart.getData().removeAll(seriesToAddOrRemove);
        }
    }

    /**
     * ★ 性能优化：专门用于更新交易信号的方法
     */
    private void updateTradeSignalsOnChart() {
        // 先移除旧的交易信号系列（如果存在）
        if (tradeSignalSeries != null) {
            priceChart.getData().remove(tradeSignalSeries);
        }

        if (lastBacktestResult.executedOrders() != null && !lastBacktestResult.executedOrders().isEmpty()) {
            tradeSignalSeries = createTradeSignalSeries("交易点连线", lastBacktestResult.executedOrders());
            priceChart.getData().add(tradeSignalSeries);

            Platform.runLater(() -> {
                for (XYChart.Data<String, Number> data : tradeSignalSeries.getData()) {
                    if (data.getNode() != null && data.getExtraValue() instanceof Order order) {
                        String symbolCssClass = (order.signal() == TradeSignal.BUY) ? "buy-signal-symbol" : "sell-signal-symbol";
                        data.getNode().getStyleClass().add(symbolCssClass);
                        data.getNode().toFront();
                    }
                }
            });
        }
    }


    // --- 以下是窗口控制、初始化等无需修改的代码 ---
    // ... [从 setupWindowControls() 到结尾的所有方法，除了上面已修改的，其他都保持不变] ...
    private void setupWindowControls() {
        customTitleBar.setOnMousePressed(event -> {
            if (event.getTarget() instanceof SVGPath || event.getTarget() instanceof StackPane || event.getTarget() instanceof ToggleButton) return;
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        customTitleBar.setOnMouseDragged(event -> {
            if (!isResizing && yOffset != 0) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
        customTitleBar.setOnMouseReleased(event -> yOffset = 0);
        customTitleBar.setOnMouseClicked(event -> { if (event.getClickCount() == 2) handleMaximize(); });
        rootPane.setOnMouseMoved(this::handleMouseMovementForResize);
        rootPane.setOnMousePressed(this::handleMousePressedForResize);
        rootPane.setOnMouseDragged(this::handleMouseDraggedForResize);
        rootPane.setOnMouseReleased(event -> { isResizing = false; rootPane.setCursor(Cursor.DEFAULT); });
    }
    @FXML private void handleMinimize() { createFadeOutAnimation(() -> stage.setIconified(true)); }
    @FXML private void handleMaximize() {
        if (isMaximized()) {
            if (backupWindowBounds != null) {
                stage.setX(backupWindowBounds.getMinX());
                stage.setY(backupWindowBounds.getMinY());
                stage.setWidth(backupWindowBounds.getWidth());
                stage.setHeight(backupWindowBounds.getHeight());
            }
        } else {
            backupWindowBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
        }
        updateMaximizeIcon();
    }
    @FXML private void handleClose() { createFadeOutAnimation(Platform::exit); }
    private void createFadeOutAnimation(Runnable onFinishedAction) {
        FadeTransition ft = new FadeTransition(Duration.millis(200), rootPane);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            onFinishedAction.run();
            if (stage.isIconified()) rootPane.setOpacity(1.0);
        });
        ft.play();
    }
    private boolean isMaximized() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return stage.getX() == bounds.getMinX() && stage.getY() == bounds.getMinY() &&
                stage.getWidth() == bounds.getWidth() && stage.getHeight() == bounds.getHeight();
    }
    private void updateMaximizeIcon() {
        boolean maximized = isMaximized();
        maximizeIcon.setVisible(!maximized);
        maximizeIcon.setManaged(!maximized);
        restoreIcon.setVisible(maximized);
        restoreIcon.setManaged(maximized);
    }
    private void handleMouseMovementForResize(MouseEvent event) {
        if (isMaximized() || isResizing) return;
        final int border = 8;
        double x = event.getX(), y = event.getY(), width = stage.getWidth(), height = stage.getHeight();
        if (x < border && y < border) resizeMode = ResizeMode.TOP_LEFT;
        else if (x > width - border && y < border) resizeMode = ResizeMode.TOP_RIGHT;
        else if (x < border && y > height - border) resizeMode = ResizeMode.BOTTOM_LEFT;
        else if (x > width - border && y > height - border) resizeMode = ResizeMode.BOTTOM_RIGHT;
        else if (x < border) resizeMode = ResizeMode.LEFT;
        else if (x > width - border) resizeMode = ResizeMode.RIGHT;
        else if (y < border) resizeMode = ResizeMode.TOP;
        else if (y > height - border) resizeMode = ResizeMode.BOTTOM;
        else { resizeMode = ResizeMode.NONE; rootPane.setCursor(Cursor.DEFAULT); }
        if (resizeMode != ResizeMode.NONE) {
            switch (resizeMode) {
                case TOP, BOTTOM -> rootPane.setCursor(Cursor.V_RESIZE);
                case LEFT, RIGHT -> rootPane.setCursor(Cursor.H_RESIZE);
                case TOP_LEFT, BOTTOM_RIGHT -> rootPane.setCursor(Cursor.NW_RESIZE);
                case TOP_RIGHT, BOTTOM_LEFT -> rootPane.setCursor(Cursor.NE_RESIZE);
            }
        }
    }
    private void handleMousePressedForResize(MouseEvent event) {
        if (resizeMode != ResizeMode.NONE) {
            isResizing = true;
            startX = event.getScreenX(); startY = event.getScreenY();
            startStageX = stage.getX(); startStageY = stage.getY();
            startWidth = stage.getWidth(); startHeight = stage.getHeight();
        }
    }
    private void handleMouseDraggedForResize(MouseEvent event) {
        if (!isResizing) return;
        double minWidth = 400, minHeight = 300;
        double deltaX = event.getScreenX() - startX, deltaY = event.getScreenY() - startY;
        if (resizeMode == ResizeMode.RIGHT || resizeMode == ResizeMode.TOP_RIGHT || resizeMode == ResizeMode.BOTTOM_RIGHT) {
            if (startWidth + deltaX > minWidth) stage.setWidth(startWidth + deltaX);
        }
        if (resizeMode == ResizeMode.LEFT || resizeMode == ResizeMode.TOP_LEFT || resizeMode == ResizeMode.BOTTOM_LEFT) {
            if (startWidth - deltaX > minWidth) { stage.setX(startStageX + deltaX); stage.setWidth(startWidth - deltaX); }
        }
        if (resizeMode == ResizeMode.BOTTOM || resizeMode == ResizeMode.BOTTOM_LEFT || resizeMode == ResizeMode.BOTTOM_RIGHT) {
            if (startHeight + deltaY > minHeight) stage.setHeight(startHeight + deltaY);
        }
        if (resizeMode == ResizeMode.TOP || resizeMode == ResizeMode.TOP_LEFT || resizeMode == ResizeMode.TOP_RIGHT) {
            if (startHeight - deltaY > minHeight) { stage.setY(startStageY + deltaY); stage.setHeight(startHeight - deltaY); }
        }
    }
    private void setupIndicatorListeners() {
        showCandlestickCheck.setOnAction(event -> redrawChart());
        showMaCheck.setOnAction(event -> redrawChart());
        showMacdCheck.setOnAction(event -> redrawChart());
        showRsiCheck.setOnAction(event -> redrawChart());
        showBbCheck.setOnAction(event -> redrawChart());
    }
    private void initializeTable() {
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().timestamp().format(DATE_FORMATTER)));
        signalColumn.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().signal()).asString());
        priceColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price()).asObject());
        quantityColumn.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().quantity()).asObject());
        valueColumn.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%,.2f", cellData.getValue().price() * cellData.getValue().quantity())));
    }
    private void initializePositionSizerControls() {
        positionSizerComboBox.getItems().addAll(CASH_PERCENTAGE_SIZER, FIXED_QUANTITY_SIZER, FIXED_CASH_QUANTITY_SIZER);
        positionSizerComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            switch (newV) {
                case CASH_PERCENTAGE_SIZER -> { sizerParamLabel.setText("资金比例(%):"); sizerParamField.setText("15.0"); }
                case FIXED_QUANTITY_SIZER -> { sizerParamLabel.setText("固定股数:"); sizerParamField.setText("100"); }
                case FIXED_CASH_QUANTITY_SIZER -> { sizerParamLabel.setText("固定资金:"); sizerParamField.setText("1000"); }
            }
        });
        positionSizerComboBox.getSelectionModel().selectFirst();
    }
    @FXML private void handleThemeToggle() {
        ObservableList<String> styleClasses = rootPane.getStyleClass();
        if (themeToggleButton.isSelected()) {
            styleClasses.add("theme-dark");
            themeToggleButton.setText("☀");
        } else {
            styleClasses.remove("theme-dark");
            themeToggleButton.setText("🌙");
        }
    }
    private void cacheAllChartData(BarSeries series, int shortMa, int longMa, int rsiPeriod, int bbPeriod) {
        candlestickSeries.clear();
        candlestickSeries.addAll(createCandlestickSeries(series));
        indicatorSeriesMap.clear();
        indicatorSeriesMap.put("MA", new MovingAverageTechnique(shortMa, longMa).calculate(series));
        indicatorSeriesMap.put("MACD", new MacdTechnique(12, 26, 9).calculate(series));
        indicatorSeriesMap.put("RSI", new RsiTechnique(rsiPeriod).calculate(series));
        indicatorSeriesMap.put("BB", new BollingerBandsTechnique(bbPeriod, 2.0).calculate(series));
    }
    private XYChart.Series<String, Number> createTradeSignalSeries(String name, List<Order> executedOrders) {
        XYChart.Series<String, Number> dataSeries = new XYChart.Series<>();
        dataSeries.setName(name);
        dataSeries.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) newNode.getStyleClass().add("trade-signal-series");
        });
        for (Order order : executedOrders) {
            String date = order.timestamp().toLocalDate().toString();
            XYChart.Data<String, Number> data = new XYChart.Data<>(date, order.price());
            data.setExtraValue(order);
            dataSeries.getData().add(data);
        }
        return dataSeries;
    }
    private List<XYChart.Series<String, Number>> createCandlestickSeries(BarSeries series) {
        XYChart.Series<String, Number> high = new XYChart.Series<>(), open = new XYChart.Series<>(), close = new XYChart.Series<>(), low = new XYChart.Series<>();
        high.setName("最高价"); open.setName("开盘价"); close.setName("收盘价"); low.setName("最低价");
        for (Bar bar : series.getBarData()) {
            String date = bar.getEndTime().toLocalDate().toString();
            high.getData().add(new XYChart.Data<>(date, bar.getHighPrice().doubleValue()));
            open.getData().add(new XYChart.Data<>(date, bar.getOpenPrice().doubleValue()));
            close.getData().add(new XYChart.Data<>(date, bar.getClosePrice().doubleValue()));
            low.getData().add(new XYChart.Data<>(date, bar.getLowPrice().doubleValue()));
        }
        return List.of(close, open, high, low);
    }
    private void adjustYAxisRange() {
        Platform.runLater(() -> {
            double minPrice = Double.MAX_VALUE, maxPrice = Double.MIN_VALUE;
            for (XYChart.Series<String, Number> s : priceChart.getData()) {
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
            double param = Double.parseDouble(sizerParamField.getText());
            return switch (positionSizerComboBox.getSelectionModel().getSelectedItem()) {
                case CASH_PERCENTAGE_SIZER -> new CashPercentagePositionSizer(param / 100.0);
                case FIXED_QUANTITY_SIZER -> new FixedQuantityPositionSizer((int) param);
                case FIXED_CASH_QUANTITY_SIZER -> new FixedCashQuantityPositionSizer(param);
                default -> new FixedQuantityPositionSizer(100);
            };
        } catch (Exception e) { return new FixedQuantityPositionSizer(100); }
    }
    private void updateSummary(BacktestResult result) { if (result.finalPortfolio() instanceof BasicPortfolio bp) summaryArea.setText(bp.getSummary()); }
    private void updateTradeLog(BacktestResult result) { tradeLogTable.getItems().setAll(result.executedOrders()); }
}