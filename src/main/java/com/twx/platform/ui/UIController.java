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
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.net.URL;
import java.time.LocalDate;
import java.util.Map;

/**
 * JavaFX界面的主控制器。
 * 负责处理用户输入、调用回测引擎、并将结果可视化地呈现在UI上。
 *
 * @version 2.0 (重构版)
 * @features
 * - 使用独立的 CandlestickLayer 绘制K线图，解决了布局和Tooltip问题。
 * - UI响应式：回测在后台线程运行，防止界面卡顿。
 * - 多Tab信息展示：清晰地区分了回测总结、交易记录和日志。
 */
public class UIController {

    private static final Logger log = LoggerFactory.getLogger(UIController.class);

    // --- FXML 控件注入 ---
    @FXML private TextField tickerField;
    @FXML private TextField initialCashField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button runButton;
    @FXML private SplitPane mainSplitPane;
    @FXML private StackPane chartPane; // K线图和LineChart的容器
    @FXML private LineChart<String, Number> priceChart; // 用于绘制均线和买卖点
    @FXML private TableView<PortfolioSummaryItem> summaryTable;
    @FXML private TableView<TradeLogItem> tradeLogTable;
    @FXML private TextArea summaryArea; // 用于显示日志和状态信息

    /**
     * FXML 初始化方法。在UI界面加载完成后，JavaFX会自动调用此方法。
     */
    @FXML
    public void initialize() {
        // 设置默认日期
        startDatePicker.setValue(LocalDate.of(2023, 1, 1));
        endDatePicker.setValue(LocalDate.now());

        // 使用 Platform.runLater 确保在UI渲染完成后再设置分割线位置
        Platform.runLater(() -> mainSplitPane.setDividerPositions(0.75));

        // 初始化“回测总结”表格的列与数据模型的绑定
        TableColumn<PortfolioSummaryItem, String> itemCol = (TableColumn<PortfolioSummaryItem, String>) summaryTable.getColumns().get(0);
        itemCol.setCellValueFactory(new PropertyValueFactory<>("item"));
        TableColumn<PortfolioSummaryItem, String> valueCol = (TableColumn<PortfolioSummaryItem, String>) summaryTable.getColumns().get(1);
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));

        // 初始化“交易记录”表格的列与数据模型的绑定
        initializeTradeLogTable();
    }

    /**
     * "运行回测" 按钮的点击事件处理方法。
     */
    @FXML
    private void handleRunBacktest() {
        runButton.setDisable(true);
        summaryArea.setText("正在运行回测，请稍候...\n");

        // 在后台线程中执行耗时任务，保持UI流畅
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
                    Platform.runLater(() -> summaryArea.appendText("无法获取数据，请检查股票代码或网络连接。\n"));
                    return;
                }
                Strategy strategy = new MovingAverageCrossStrategy(series, 10, 30);
                BacktestResult result = engine.run(strategy, portfolio, positionSizer);

                // 回测完成后，切换回JavaFX应用线程更新UI
                Platform.runLater(() -> {
                    updateChart(result);
                    updateSummary(result.finalPortfolio());
                    updateTradeLog(result);
                    summaryArea.appendText("回测完成！\n");
                });

            } catch (Exception e) {
                log.error("回测过程中发生未捕获的异常", e);
                Platform.runLater(() -> summaryArea.appendText("发生错误: " + e.getMessage() + "\n"));
            } finally {
                Platform.runLater(() -> runButton.setDisable(false));
            }
        }).start();
    }

    /**
     * 核心方法：根据回测结果更新整个图表区域。
     * @param result 包含所有回测数据的封装对象
     */
    @SuppressWarnings("unchecked")
    private void updateChart(BacktestResult result) {
        // 清理旧数据
        priceChart.getData().clear();
        chartPane.getChildren().removeIf(node -> node instanceof CandlestickLayer);

        // 加载样式表
        URL cssUrl = getClass().getResource("style.css");
        if (cssUrl != null) {
            String cssPath = cssUrl.toExternalForm();
            if (!chartPane.getStylesheets().contains(cssPath)) {
                chartPane.getStylesheets().add(cssPath);
            }
        } else {
            log.warn("无法找到样式文件 style.css");
        }

        BarSeries series = result.series();
        if (series.isEmpty()) return;

        // 1. 创建并添加独立的K线图层
        CategoryAxis xAxis = (CategoryAxis) priceChart.getXAxis();
        NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
        CandlestickLayer candlestickLayer = new CandlestickLayer(series, xAxis, yAxis);
        chartPane.getChildren().add(candlestickLayer);

        // 2. 创建并添加均线序列
        XYChart.Series<String, Number> shortSmaSeries = createIndicatorSeries(series, 10, "SMA(10)");
        XYChart.Series<String, Number> longSmaSeries = createIndicatorSeries(series, 30, "SMA(30)");

        // 3. 创建并添加买卖点序列
        XYChart.Series<String, Number> buySignalsSeries = new XYChart.Series<>();
        buySignalsSeries.setName("买入点");
        XYChart.Series<String, Number> sellSignalsSeries = new XYChart.Series<>();
        sellSignalsSeries.setName("卖出点");
        for (Order order : result.executedOrders()) {
            String date = order.timestamp().toLocalDate().toString();
            double price = order.price();
            XYChart.Data<String, Number> data = new XYChart.Data<>(date, price);
            if (order.signal() == TradeSignal.BUY) {
                buySignalsSeries.getData().add(data);
            } else if (order.signal() == TradeSignal.SELL) {
                sellSignalsSeries.getData().add(data);
            }
        }

        // 4. 将均线和买卖点序列添加到 LineChart 中
        priceChart.getData().addAll(shortSmaSeries, longSmaSeries, buySignalsSeries, sellSignalsSeries);

        // 5. 应用CSS样式
        Platform.runLater(() -> {
            applySeriesStyles(shortSmaSeries, "short-sma-series");
            applySeriesStyles(longSmaSeries, "long-sma-series");
            applyNodeStyles(buySignalsSeries, "buy-signal-symbol");
            applyNodeStyles(sellSignalsSeries, "sell-signal-symbol");
        });
    }

    /**
     * 更新“回测总结”表格。
     * @param portfolio 最终的投资组合状态
     */
    private void updateSummary(Portfolio portfolio) {
        summaryTable.getItems().clear();
        Map<String, String> summaryMap = portfolio.getSummaryMap();
        for (Map.Entry<String, String> entry : summaryMap.entrySet()) {
            summaryTable.getItems().add(new PortfolioSummaryItem(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * 更新“交易记录”表格。
     * @param result 包含所有已执行订单的结果对象
     */
    private void updateTradeLog(BacktestResult result) {
        tradeLogTable.getItems().clear();
        for (Order order : result.executedOrders()) {
            tradeLogTable.getItems().add(new TradeLogItem(order));
        }
    }

    /**
     * 辅助方法：初始化交易记录表格的列与数据模型的绑定关系。
     */
    private void initializeTradeLogTable() {
        TableColumn<TradeLogItem, String> dateCol = (TableColumn<TradeLogItem, String>) tradeLogTable.getColumns().get(0);
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        TableColumn<TradeLogItem, String> directionCol = (TableColumn<TradeLogItem, String>) tradeLogTable.getColumns().get(1);
        directionCol.setCellValueFactory(new PropertyValueFactory<>("direction"));

        TableColumn<TradeLogItem, Double> quantityCol = (TableColumn<TradeLogItem, Double>) tradeLogTable.getColumns().get(2);
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<TradeLogItem, Double> priceCol = (TableColumn<TradeLogItem, Double>) tradeLogTable.getColumns().get(3);
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<TradeLogItem, Double> commissionCol = (TableColumn<TradeLogItem, Double>) tradeLogTable.getColumns().get(4);
        commissionCol.setCellValueFactory(new PropertyValueFactory<>("commission"));
    }

    /**
     * 辅助方法：创建指标（如SMA）的图表序列。
     * @return 一个可直接添加到图表的数据序列
     */
    private XYChart.Series<String, Number> createIndicatorSeries(BarSeries series, int period, String name) {
        XYChart.Series<String, Number> seriesData = new XYChart.Series<>();
        seriesData.setName(name);
        SMAIndicator indicator = new SMAIndicator(new ClosePriceIndicator(series), period);
        for (int i = 0; i < series.getBarCount(); i++) {
            String date = series.getBar(i).getEndTime().toLocalDate().toString();
            seriesData.getData().add(new XYChart.Data<>(date, indicator.getValue(i).doubleValue()));
        }
        return seriesData;
    }

    /**
     * 辅助方法：为整个序列的线条应用CSS样式。
     */
    private void applySeriesStyles(XYChart.Series<String, Number> series, String styleClass) {
        // 在图表渲染完成后，查找代表线条的Node并应用样式
        Node line = series.getNode().lookup(".chart-series-line");
        if (line != null) {
            line.getStyleClass().add(styleClass);
        }
    }

    /**
     * 辅助方法：为序列中每个数据点的符号应用CSS样式。
     */
    private void applyNodeStyles(XYChart.Series<String, Number> series, String styleClass) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() != null) {
                data.getNode().getStyleClass().add(styleClass);
            }
        }
    }
}