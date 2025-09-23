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
import com.twx.platform.strategy.impl.BollingerBandsStrategy;
import com.twx.platform.strategy.impl.MACDStrategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import com.twx.platform.strategy.impl.RsiStrategy;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class UIController {

    private Stage stage;

    // --- FXML 控件 ---
    @FXML private BorderPane rootPane;
    @FXML private MenuBar menuBar;
    @FXML private TextField tickerField, initialCashField, sizerParamField;
    @FXML private DatePicker startDatePicker, endDatePicker;
    @FXML private ComboBox<String> positionSizerComboBox;
    @FXML private Label sizerParamLabel;
    @FXML private Button runButton;
    @FXML private LineChart<Number, Number> priceChart;
    @FXML private ToggleButton aiAssistantToggle, chartSettingsToggle, resultsToggle;
    @FXML private ToggleButton strategySettingsToggle; // 新增: 策略面板切换按钮
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;

    // --- 动态加载的面板及其内部控件 ---
    private BorderPane mainArea;
    private AIAssistant aiAssistantPanel;
    private Node chartSettingsPanel, resultsPanel, strategySettingsPanel;

    // 图表显示相关的 CheckBox
    private CheckBox showCandlestickCheck, showMaCheck, showMacdCheck, showRsiCheck, showBbCheck;

    // 结果面板控件
    private TextArea summaryArea;
    private TableView<Order> tradeLogTable;

    // 【新增】策略面板中的控件
    private RadioButton strategyRadioMA, strategyRadioRSI, strategyRadioBB, strategyRadioMACD;
    private TextField strategyShortMaField, strategyLongMaField, strategyRsiPeriodField, strategyBbandsPeriodField;
    private Map<String, Node> strategyParamsPanes;

    // --- 内部状态和常量 ---
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private BacktestResult lastBacktestResult;
    private final Map<String, List<XYChart.Series<Number, Number>>> indicatorSeriesMap = new HashMap<>();
    private final List<XYChart.Series<Number, Number>> candlestickSeries = new ArrayList<>();
    private XYChart.Series<Number, Number> tradeSignalSeries = null;

    private static final String SIZER_CASH_PERCENT = "按资金百分比";
    private static final String SIZER_FIXED_QTY = "按固定股数";
    private static final String SIZER_FIXED_CASH = "按固定资金";
    private static final int TARGET_Y_AXIS_TICK_COUNT = 12;
    private static final int TARGET_X_AXIS_LABEL_COUNT = 15;

    private boolean isChartPopulated = false;
    private String selectedStrategy = "MovingAverageCrossStrategy";

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        Node coreContent = rootPane.getCenter();
        mainArea = new BorderPane(coreContent);
        rootPane.setCenter(mainArea);

        startDatePicker.setValue(LocalDate.of(2024, 1, 1));
        endDatePicker.setValue(LocalDate.now());

        setupXAxisLabelFormatter();
        loadDynamicPanels();
        createMenuBar();
        setupPanelToggles();
        initializePositionSizerControls();
    }

    private void createMenuBar() {
        // --- 文件 (F) ---
        Menu fileMenu = new Menu("文件(_F)");
        fileMenu.getItems().addAll(
                createMenuItem("保存工作区", e -> System.out.println("TODO: Implement Save Workspace")),
                createMenuItem("加载工作区", e -> System.out.println("TODO: Implement Load Workspace")),
                new SeparatorMenuItem(),
                createMenuItem("导出回测报告...", e -> System.out.println("TODO: Implement Export Results")),
                new SeparatorMenuItem(),
                createMenuItem("退出", e -> Platform.exit())
        );

        // --- 数据 (D) ---
        Menu dataMenu = new Menu("数据(_D)");
        dataMenu.getItems().addAll(
                createMenuItem("查询股票代码...", e -> showStockCodeSearchDialog())
        );

        // --- 视图 (V) ---
        Menu viewMenu = new Menu("视图(_V)");
        Menu indicatorsMenu = new Menu("技术指标");
        CheckMenuItem showCandlestickMenuItem = new CheckMenuItem("显示K线图");
        CheckMenuItem showMaMenuItem = new CheckMenuItem("显示MA");
        CheckMenuItem showMacdMenuItem = new CheckMenuItem("显示MACD");
        CheckMenuItem showRsiMenuItem = new CheckMenuItem("显示RSI");
        CheckMenuItem showBbMenuItem = new CheckMenuItem("显示BBands");
        showCandlestickMenuItem.selectedProperty().bindBidirectional(showCandlestickCheck.selectedProperty());
        showMaMenuItem.selectedProperty().bindBidirectional(showMaCheck.selectedProperty());
        showMacdMenuItem.selectedProperty().bindBidirectional(showMacdCheck.selectedProperty());
        showRsiMenuItem.selectedProperty().bindBidirectional(showRsiCheck.selectedProperty());
        showBbMenuItem.selectedProperty().bindBidirectional(showBbCheck.selectedProperty());
        indicatorsMenu.getItems().addAll(showCandlestickMenuItem, showMaMenuItem, showMacdMenuItem, showRsiMenuItem, showBbMenuItem);
        viewMenu.getItems().add(indicatorsMenu);

        // --- 策略 (S) ---
        Menu strategyMenu = new Menu("策略(_S)");
        ToggleGroup menuStrategyGroup = new ToggleGroup();

        menuStrategyGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String strategyId = (String) newVal.getUserData();
                this.selectedStrategy = strategyId;

                // 【双向绑定】当菜单栏变化时，同步更新右侧策略面板的选中状态
                if (strategyRadioMA != null) { // 确保面板已加载
                    for(Toggle panelToggle : strategyRadioMA.getToggleGroup().getToggles()){
                        if(strategyId.equals(panelToggle.getUserData()) && !panelToggle.isSelected()){
                            panelToggle.setSelected(true);
                            break;
                        }
                    }
                }
            }
        });
        RadioMenuItem maCrossStrategyItem = createRadioMenuItem("均线交叉策略", "MovingAverageCrossStrategy", menuStrategyGroup, true);
        RadioMenuItem rsiStrategyItem = createRadioMenuItem("RSI策略", "RsiStrategy", menuStrategyGroup, false);
        RadioMenuItem bollingerStrategyItem = createRadioMenuItem("布林带策略", "BollingerBandsStrategy", menuStrategyGroup, false);
        RadioMenuItem macdStrategyItem = createRadioMenuItem("MACD策略", "MACDStrategy", menuStrategyGroup, false);

        strategyMenu.getItems().addAll(
                maCrossStrategyItem, rsiStrategyItem, bollingerStrategyItem, macdStrategyItem,
                new SeparatorMenuItem(),
                createMenuItem("配置策略参数...", e -> showStrategyParamsDialog())
        );

        // --- 回测 (B) ---
        Menu backtestMenu = new Menu("回测(_B)");
        backtestMenu.getItems().addAll(
                createMenuItem("运行单次回测", e -> handleRunBacktest()),
                createMenuItem("批量回测...", e -> System.out.println("TODO: Implement Batch Backtest")),
                createMenuItem("参数优化...", e -> System.out.println("TODO: Implement Parameter Optimization"))
        );

        // --- 窗口 (W) ---
        Menu windowMenu = new Menu("窗口(_W)");
        CheckMenuItem showAiPanelItem = new CheckMenuItem("显示AI助手");
        CheckMenuItem showChartSettingsItem = new CheckMenuItem("显示图表设置");
        CheckMenuItem showStrategySettingsItem = new CheckMenuItem("显示策略设置"); // 新增
        CheckMenuItem showResultsPanelItem = new CheckMenuItem("显示结果面板");
        showAiPanelItem.selectedProperty().bindBidirectional(aiAssistantToggle.selectedProperty());
        showChartSettingsItem.selectedProperty().bindBidirectional(chartSettingsToggle.selectedProperty());
        showStrategySettingsItem.selectedProperty().bindBidirectional(strategySettingsToggle.selectedProperty()); // 新增
        showResultsPanelItem.selectedProperty().bindBidirectional(resultsToggle.selectedProperty());
        windowMenu.getItems().addAll(showAiPanelItem, showChartSettingsItem, showStrategySettingsItem, showResultsPanelItem);

        // --- 工具 (T) ---
        Menu toolsMenu = new Menu("工具(_T)");
        CheckMenuItem themeToggle = new CheckMenuItem("切换暗色模式");
        themeToggle.selectedProperty().addListener((obs, ov, isDark) -> {
            if (isDark) rootPane.getStyleClass().add("theme-dark");
            else rootPane.getStyleClass().remove("theme-dark");
            if (this.stage != null) {
                WindowsTitleBar.setDarkMode(this.stage, isDark);
            }
        });

        toolsMenu.getItems().addAll(
                createMenuItem("设置 API Key...", e -> showApiKeyDialog()),
                themeToggle
        );

        // --- 帮助 (H) ---
        Menu helpMenu = new Menu("帮助(_H)");
        helpMenu.getItems().add(createMenuItem("关于", e -> showAboutDialog()));

        menuBar.getMenus().addAll(fileMenu, dataMenu, viewMenu, strategyMenu, backtestMenu, windowMenu, toolsMenu, helpMenu);
    }

    private boolean isDarkMode() {
        return rootPane.getStyleClass().contains("theme-dark");
    }

    private void setupPanelToggles() {
        // 左侧和底部面板
        aiAssistantToggle.selectedProperty().addListener((obs, ov, show) -> mainArea.setLeft(show ? aiAssistantPanel : null));
        resultsToggle.selectedProperty().addListener((obs, ov, show) -> mainArea.setBottom(show ? resultsPanel : null));

        // 【核心修改】为右侧的两个 ToggleButton 创建一个 ToggleGroup 实现互斥
        ToggleGroup rightToggleGroup = new ToggleGroup();
        chartSettingsToggle.setToggleGroup(rightToggleGroup);
        strategySettingsToggle.setToggleGroup(rightToggleGroup);

        // 监听这个组的变化来切换面板
        rightToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                mainArea.setRight(null);
            } else if (newToggle == chartSettingsToggle) {
                mainArea.setRight(chartSettingsPanel);
            } else if (newToggle == strategySettingsToggle) {
                mainArea.setRight(strategySettingsPanel);
            }
        });
    }

    private void loadDynamicPanels() {
        try {
            aiAssistantPanel = new AIAssistant();
            aiAssistantPanel.getStyleClass().add("ai-assistant-panel");

            // --- 加载图表设置面板 ---
            FXMLLoader chartSettingsLoader = new FXMLLoader(getClass().getResource("chart-settings-panel.fxml"));
            chartSettingsPanel = chartSettingsLoader.load();
            initializeChartSettingsControls(chartSettingsLoader.getNamespace());

            // --- 加载结果面板 ---
            FXMLLoader resultsLoader = new FXMLLoader(getClass().getResource("results-panel.fxml"));
            resultsPanel = resultsLoader.load();
            initializeResultsControls(resultsLoader.getNamespace());

            // --- 【新增】加载策略设置面板 ---
            FXMLLoader strategySettingsLoader = new FXMLLoader(getClass().getResource("strategy-settings-panel.fxml"));
            strategySettingsPanel = strategySettingsLoader.load();
            initializeStrategySettingsControls(strategySettingsLoader.getNamespace());

        } catch (IOException e) {
            e.printStackTrace();
            CustomDialog.show(stage, CustomDialog.DialogType.ERROR, "资源加载失败", "无法加载UI面板资源: " + e.getMessage(), isDarkMode());
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
                    Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("无法获取'" + ticker.symbol() + "'的数据。"); });
                    return;
                }

                Portfolio portfolio = new BasicPortfolio(Double.parseDouble(initialCashField.getText()), 0.0003);
                Strategy strategy = createStrategy(series);
                if (strategy == null) return;

                BacktestEngine engine = new BacktestEngine(dataProvider, ticker, startDate, endDate, TimeFrame.DAILY);
                lastBacktestResult = engine.run(strategy, portfolio, createPositionSizerFromUI());
                cacheAllChartData(lastBacktestResult.series());

                Platform.runLater(() -> {
                    populateChartFirstTime();
                    updateSummaryAndLog(lastBacktestResult);
                    if (aiAssistantPanel != null) {
                        aiAssistantPanel.updateAnalysisContext(lastBacktestResult, strategy);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("发生错误: \n" + e.getMessage()); });
            } finally {
                Platform.runLater(() -> runButton.setDisable(false));
            }
        }).start();
    }

    private Strategy createStrategy(BarSeries series) {
        try {
            return switch (selectedStrategy) {
                case "MovingAverageCrossStrategy" -> new MovingAverageCrossStrategy(series,
                        Integer.parseInt(strategyShortMaField.getText()),
                        Integer.parseInt(strategyLongMaField.getText()));
                case "RsiStrategy" -> new RsiStrategy(series,
                        Integer.parseInt(strategyRsiPeriodField.getText()),
                        30, 70);
                case "BollingerBandsStrategy" -> new BollingerBandsStrategy(series,
                        Integer.parseInt(strategyBbandsPeriodField.getText()),
                        2.0);
                case "MACDStrategy" -> new MACDStrategy(series, 12, 26, 9);
                default -> {
                    Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("未选择或不支持的策略。"); });
                    yield null;
                }
            };
        } catch (NumberFormatException e) {
            Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("策略参数无效，请输入数字。"); });
            return null;
        } catch (Exception e) {
            Platform.runLater(() -> { if (summaryArea != null) summaryArea.setText("创建策略时出错: " + e.getMessage()); });
            return null;
        }
    }

    private void cacheAllChartData(BarSeries series) {
        candlestickSeries.clear();
        candlestickSeries.addAll(createCandlestickSeries(series));
        indicatorSeriesMap.clear();

        // 【修改】从新的策略面板控件读取参数
        try {
            int shortMa = Integer.parseInt(strategyShortMaField.getText());
            int longMa = Integer.parseInt(strategyLongMaField.getText());
            int rsiPeriod = Integer.parseInt(strategyRsiPeriodField.getText());
            int bbandsPeriod = Integer.parseInt(strategyBbandsPeriodField.getText());

            List<XYChart.Series<Number, Number>> maSeries = new MovingAverageTechnique(shortMa, longMa).calculate(series);
            setStyleClassForSeries(maSeries.get(0), "ma-short-series");
            setStyleClassForSeries(maSeries.get(1), "ma-long-series");
            indicatorSeriesMap.put("MA", maSeries);

            List<XYChart.Series<Number, Number>> macdSeries = new MacdTechnique(12, 26, 9).calculate(series);
            setStyleClassForSeries(macdSeries.get(0), "macd-series");
            setStyleClassForSeries(macdSeries.get(1), "macd-signal-series");
            indicatorSeriesMap.put("MACD", macdSeries);

            List<XYChart.Series<Number, Number>> rsiSeries = new RsiTechnique(rsiPeriod).calculate(series);
            setStyleClassForSeries(rsiSeries.get(0), "rsi-series");
            indicatorSeriesMap.put("RSI", rsiSeries);

            List<XYChart.Series<Number, Number>> bbSeries = new BollingerBandsTechnique(bbandsPeriod, 2.0).calculate(series);
            setStyleClassForSeries(bbSeries.get(0), "bb-middle-series");
            setStyleClassForSeries(bbSeries.get(1), "bb-upper-series");
            setStyleClassForSeries(bbSeries.get(2), "bb-lower-series");
            indicatorSeriesMap.put("BB", bbSeries);

        } catch (NumberFormatException e) {
            Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, "图表指标参数无效，请检查策略设置面板。").showAndWait());
        }
    }

    private void initializeChartSettingsControls(Map<String, Object> namespace) {
        showCandlestickCheck = (CheckBox) namespace.get("showCandlestickCheck");
        showMaCheck = (CheckBox) namespace.get("showMaCheck");
        showMacdCheck = (CheckBox) namespace.get("showMacdCheck");
        showRsiCheck = (CheckBox) namespace.get("showRsiCheck");
        showBbCheck = (CheckBox) namespace.get("showBbCheck");
        Stream.of(showCandlestickCheck, showMaCheck, showMacdCheck, showRsiCheck, showBbCheck)
                .forEach(cb -> cb.selectedProperty().addListener((obs, ov, nv) -> redrawChart()));
    }

    private void initializeStrategySettingsControls(Map<String, Object> namespace) {
        // 获取策略面板中的所有控件
        strategyRadioMA = (RadioButton) namespace.get("strategyRadioMA");
        strategyRadioRSI = (RadioButton) namespace.get("strategyRadioRSI");
        strategyRadioBB = (RadioButton) namespace.get("strategyRadioBB");
        strategyRadioMACD = (RadioButton) namespace.get("strategyRadioMACD");

        strategyShortMaField = (TextField) namespace.get("strategyShortMaField");
        strategyLongMaField = (TextField) namespace.get("strategyLongMaField");
        strategyRsiPeriodField = (TextField) namespace.get("strategyRsiPeriodField");
        strategyBbandsPeriodField = (TextField) namespace.get("strategyBbandsPeriodField");

        // 设置默认值
        strategyShortMaField.setText("10");
        strategyLongMaField.setText("30");
        strategyRsiPeriodField.setText("13");
        strategyBbandsPeriodField.setText("26");

        strategyParamsPanes = new HashMap<>();
        strategyParamsPanes.put("MovingAverageCrossStrategy", (Node) namespace.get("maParamsPane"));
        strategyParamsPanes.put("RsiStrategy", (Node) namespace.get("rsiParamsPane"));
        strategyParamsPanes.put("BollingerBandsStrategy", (Node) namespace.get("bbandsParamsPane"));
        strategyParamsPanes.put("MACDStrategy", (Node) namespace.get("macdInfoPane"));

        ToggleGroup panelStrategyGroup = new ToggleGroup();
        strategyRadioMA.setToggleGroup(panelStrategyGroup);
        strategyRadioRSI.setToggleGroup(panelStrategyGroup);
        strategyRadioBB.setToggleGroup(panelStrategyGroup);
        strategyRadioMACD.setToggleGroup(panelStrategyGroup);

        strategyRadioMA.setUserData("MovingAverageCrossStrategy");
        strategyRadioRSI.setUserData("RsiStrategy");
        strategyRadioBB.setUserData("BollingerBandsStrategy");
        strategyRadioMACD.setUserData("MACDStrategy");

        panelStrategyGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            String strategyId = (String) newVal.getUserData();

            this.selectedStrategy = strategyId;
            updateMenuBarStrategySelection(strategyId);

            strategyParamsPanes.forEach((id, pane) -> {
                boolean isVisible = id.equals(strategyId);
                pane.setVisible(isVisible);
                pane.setManaged(isVisible);
            });
        });
        strategyRadioMA.setSelected(true);
    }

    private void updateMenuBarStrategySelection(String strategyId) {
        Menu strategyMenu = menuBar.getMenus().stream()
                .filter(menu -> "策略(_S)".equals(menu.getText()))
                .findFirst().orElse(null);
        if (strategyMenu == null) return;

        for (Toggle toggle : strategyMenu.getItems().stream()
                .filter(item -> item instanceof RadioMenuItem)
                .map(item -> (Toggle) item)
                .toList()) {
            if (strategyId.equals(toggle.getUserData())) {
                if (!toggle.isSelected()) {
                    toggle.setSelected(true);
                }
                break;
            }
        }
    }

    private void showStrategyParamsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        switch (selectedStrategy) {
            case "MovingAverageCrossStrategy":
                dialog.setTitle("配置策略参数");
                dialog.setHeaderText("均线交叉策略");
                TextField dialogShortMaField = new TextField();
                TextField dialogLongMaField = new TextField();
                dialogShortMaField.textProperty().bindBidirectional(this.strategyShortMaField.textProperty());
                dialogLongMaField.textProperty().bindBidirectional(this.strategyLongMaField.textProperty());
                grid.add(new Label("短期周期:"), 0, 0);
                grid.add(dialogShortMaField, 1, 0);
                grid.add(new Label("长期周期:"), 0, 1);
                grid.add(dialogLongMaField, 1, 1);
                break;
            case "RsiStrategy":
                dialog.setTitle("配置策略参数");
                dialog.setHeaderText("RSI策略");
                TextField dialogRsiPeriodField = new TextField();
                dialogRsiPeriodField.textProperty().bindBidirectional(this.strategyRsiPeriodField.textProperty());
                grid.add(new Label("RSI 周期:"), 0, 0);
                grid.add(dialogRsiPeriodField, 1, 0);
                grid.add(new Label("超卖阈值:"), 0, 1);
                grid.add(new Label("30 (固定)"), 1, 1);
                grid.add(new Label("超买阈值:"), 0, 2);
                grid.add(new Label("70 (固定)"), 1, 2);
                break;
            case "BollingerBandsStrategy":
                dialog.setTitle("配置策略参数");
                dialog.setHeaderText("布林带策略");
                TextField dialogBbandsPeriodField = new TextField();
                dialogBbandsPeriodField.textProperty().bindBidirectional(this.strategyBbandsPeriodField.textProperty());
                grid.add(new Label("BBands 周期:"), 0, 0);
                grid.add(dialogBbandsPeriodField, 1, 0);
                grid.add(new Label("标准差倍数:"), 0, 1);
                grid.add(new Label("2.0 (固定)"), 1, 1);
                break;
            case "MACDStrategy":
                dialog.setTitle("配置策略参数");
                dialog.setHeaderText("MACD 策略 (参数固定)");
                grid.add(new Label("快线周期:"), 0, 0);
                grid.add(new Label("12"), 1, 0);
                grid.add(new Label("慢线周期:"), 0, 1);
                grid.add(new Label("26"), 1, 1);
                grid.add(new Label("信号线周期:"), 0, 2);
                grid.add(new Label("9"), 1, 2);
                break;
            default:
                CustomDialog.show(stage, CustomDialog.DialogType.WARNING, "操作提示", "请先从'策略'菜单中选择一个策略。", isDarkMode());
                return;
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    // --- 以下是未改变的辅助方法 ---

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
            @Override public String toString(Number object) { return LocalDate.ofEpochDay(object.longValue()).format(DATE_FORMATTER); }
            @Override public Number fromString(String string) { return LocalDate.parse(string, DATE_FORMATTER).toEpochDay(); }
        });
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

    private void showApiKeyDialog() {
        Optional<String> result = CustomDialog.showTextInput(stage,
                "API Key 设置",
                "请输入您的 Kimi API Key:",
                ConfigurationManager.getInstance().getKimiApiKey(),
                isDarkMode());

        result.ifPresent(apiKey -> {
            ConfigurationManager.getInstance().setKimiApiKey(apiKey.trim());
            CustomDialog.show(stage, CustomDialog.DialogType.INFORMATION, "操作成功", "API Key 已保存。", isDarkMode());
        });
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
            if (newNode != null) newNode.getStyleClass().add(styleClass);
        });
    }

    private MenuItem createMenuItem(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(action);
        return item;
    }

    private RadioMenuItem createRadioMenuItem(String text, String userData, ToggleGroup group, boolean selected) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setUserData(userData);
        item.setToggleGroup(group);
        item.setSelected(selected);
        return item;
    }

    // 在 UIController.java 中

    private void showStockCodeSearchDialog() {
        Dialog<DataProvider.StockSuggestion> dialog = new Dialog<>();
        dialog.setTitle("查询股票代码");
        dialog.setHeaderText("请输入关键词并选择市场进行搜索");
        dialog.initOwner(stage);

        ButtonType okButtonType = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        // --- 界面布局 ---
        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(10, 10, 0, 10));

        // 顶部：筛选+搜索
        HBox topControls = new HBox(10);
        topControls.setAlignment(Pos.CENTER_LEFT);

        // 市场筛选 ComboBox
        ComboBox<DataProvider.MarketType> marketFilterBox = new ComboBox<>();
        marketFilterBox.getItems().setAll(DataProvider.MarketType.values());
        marketFilterBox.setValue(DataProvider.MarketType.A_SHARE); // 默认选中A股

        TextField searchField = new TextField();
        searchField.setPromptText("名称/拼音/代码");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Button searchButton = new Button("搜索");

        topControls.getChildren().addAll(new Label("市场:"), marketFilterBox, searchField, searchButton);
        borderPane.setTop(topControls);

        // 中间：结果列表
        ListView<DataProvider.StockSuggestion> resultsView = new ListView<>();
        resultsView.setPlaceholder(new Label("输入关键词后点击搜索"));
        BorderPane.setMargin(resultsView, new Insets(10, 0, 0, 0));
        borderPane.setCenter(resultsView);

        dialog.getDialogPane().setContent(borderPane);
        dialog.getDialogPane().setPrefWidth(450); // 设置一个合适的宽度

        // --- 核心逻辑 ---
        Runnable performSearch = () -> {
            String keyword = searchField.getText().trim();
            DataProvider.MarketType selectedMarket = marketFilterBox.getValue();
            if (keyword.isEmpty()) return;

            resultsView.getItems().clear();
            resultsView.setPlaceholder(new Label("正在搜索..."));

            new Thread(() -> {
                try {
                    DataProvider dataProvider = new SinaDataProvider();
                    List<DataProvider.StockSuggestion> results = dataProvider.searchStocks(keyword, selectedMarket);
                    Platform.runLater(() -> {
                        if (results.isEmpty()) {
                            resultsView.setPlaceholder(new Label("未找到匹配的结果"));
                        } else {
                            resultsView.getItems().setAll(results);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> resultsView.setPlaceholder(new Label("搜索出错: " + e.getMessage())));
                }
            }).start();
        };

        // --- 事件绑定 ---
        searchButton.setOnAction(e -> performSearch.run());
        searchField.setOnAction(e -> performSearch.run()); // 回车搜索
        // 当筛选条件改变时，如果搜索框有内容，则自动重新搜索
        marketFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!searchField.getText().trim().isEmpty()) {
                performSearch.run();
            }
        });

        // 控制"确定"按钮的可用状态
        Node okButton = dialog.getDialogPane().lookupButton(okButtonType);
        okButton.setDisable(true);
        resultsView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> okButton.setDisable(newVal == null));

        // 双击列表项直接确定
        resultsView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && resultsView.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(resultsView.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        // 设置对话框返回结果
        dialog.setResultConverter(dialogButton -> (dialogButton == okButtonType) ? resultsView.getSelectionModel().getSelectedItem() : null);

        // 显示对话框并处理结果
        Optional<DataProvider.StockSuggestion> result = dialog.showAndWait();
        result.ifPresent(stock -> tickerField.setText(stock.ticker()));
    }

    private void showAboutDialog() {
        String content = "版本: 1.2.0\n作者: twx\n\n一个基于JavaFX和ta4j的量化回测工具。";
        CustomDialog.show(stage, CustomDialog.DialogType.INFORMATION, "关于 金融量化分析平台", content, isDarkMode());
    }
}