package com.twx.platform.ai;

import com.twx.platform.common.ConfigurationManager;
import com.twx.platform.common.Order;
import com.twx.platform.engine.BacktestResult;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import com.twx.platform.ui.CustomDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能助手面板，与 Kimi 大模型进行交互。
 */
public class AIAssistant extends VBox {

    // --- 常量 ---
    private static final String KIMI_API_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String KIMI_MODEL = "moonshot-v1-8k";

    // --- UI 组件 ---
    private final VBox chatHistory;
    private final TextArea inputArea;
    private final Button sendButton;
    private final CheckBox analyzeDataCheck;

    // --- 状态与服务 ---
    private final HttpClient httpClient;
    private BacktestResult currentBacktestResult;
    private Strategy currentStrategy;

    public AIAssistant() {
        super(10);
        this.setPadding(new Insets(10));
        this.getStyleClass().add("side-panel");
        this.setPrefWidth(300);

        this.httpClient = HttpClient.newHttpClient();

        // 1. 初始化UI组件
        Label title = new Label("AI 智能助手");
        title.getStyleClass().add("panel-title");

        chatHistory = new VBox(5);
        chatHistory.setPadding(new Insets(5));
        ScrollPane scrollPane = new ScrollPane(chatHistory);
        scrollPane.setFitToWidth(true);
        scrollPane.vvalueProperty().bind(chatHistory.heightProperty());
        scrollPane.getStyleClass().add("ai-chat-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        inputArea = new TextArea();
        inputArea.setPromptText("在此输入您的问题...");
        inputArea.setPrefHeight(80);
        inputArea.setWrapText(true);

        sendButton = new Button("发送");
        sendButton.setMaxWidth(Double.MAX_VALUE);
        sendButton.setOnAction(e -> handleSendMessage());

        analyzeDataCheck = new CheckBox("附带当前回测数据进行分析");
        analyzeDataCheck.setDisable(true);

        // 2. 布局
        VBox inputGroup = new VBox(5, analyzeDataCheck, inputArea);
        this.getChildren().addAll(title, scrollPane, inputGroup, sendButton);

        // 3. 设置初始化逻辑
        initializeWelcomeMessage();
    }

    // =================================================================================
    // Public API
    // =================================================================================

    /**
     * 更新AI分析所需的上下文数据。
     * 此方法由 UIController 在每次回测成功后调用。
     *
     * @param result   最新的回测结果
     * @param strategy 使用的策略实例
     */
    public void updateAnalysisContext(BacktestResult result, Strategy strategy) {
        this.currentBacktestResult = result;
        this.currentStrategy = strategy;

        boolean hasData = result != null && result.executedOrders() != null && !result.executedOrders().isEmpty();
        analyzeDataCheck.setDisable(!hasData);
        if (hasData) {
            analyzeDataCheck.setSelected(true);
            Platform.runLater(() -> addMessage("回测数据已更新，您可以开始提问分析了。", "ai"));
        } else {
            analyzeDataCheck.setSelected(false);
        }
    }

    // =================================================================================
    // 事件处理
    // =================================================================================

    /**
     * 处理发送按钮的点击事件。
     */
    private void handleSendMessage() {
        String userMessage = inputArea.getText().trim();
        if (userMessage.isEmpty()) return;

        addMessage(userMessage, "user");
        inputArea.clear();
        setUiLoading(true);

        String fullPrompt = buildContextualPrompt(userMessage);

        // 在后台线程中执行网络请求
        new Thread(() -> {
            try {
                String aiResponse = getKimiResponse(fullPrompt);
                Platform.runLater(() -> {
                    addMessage(aiResponse, "ai");
                    setUiLoading(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showErrorDialog(e);
                    setUiLoading(false);
                });
            }
        }).start();
    }

    // =================================================================================
    // UI 更新
    // =================================================================================

    /**
     * 监听组件添加到场景，安全地显示初始欢迎语。
     */
    private void initializeWelcomeMessage() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && chatHistory.getChildren().isEmpty()) {
                addMessage("你好！我是您的量化交易助手。", "ai");
            }
        });
    }

    /**
     * 向聊天记录UI中添加一条消息。
     *
     * @param message 消息内容
     * @param sender  发送者 ("user" 或 "ai")
     */
    private void addMessage(String message, String sender) {
        BorderPane wrapper = new BorderPane();

        if ("user".equals(sender)) {
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.setPadding(new Insets(8));
            messageLabel.getStyleClass().addAll("chat-bubble", "user-bubble");
            wrapper.setRight(messageLabel);
        } else {
            boolean isDarkMode = false;
            if (getScene() != null && getScene().getRoot() != null) {
                isDarkMode = getScene().getRoot().getStyleClass().contains("theme-dark");
            }
            MarkdownView markdownView = new MarkdownView(isDarkMode);
            markdownView.updateContent(message);
            markdownView.getStyleClass().addAll("chat-bubble", "ai-bubble");
            wrapper.setLeft(markdownView);
        }
        chatHistory.getChildren().add(wrapper);
    }

    /**
     * 控制输入区和发送按钮的加载状态。
     *
     * @param isLoading 是否正在加载
     */
    private void setUiLoading(boolean isLoading) {
        inputArea.setDisable(isLoading);
        sendButton.setDisable(isLoading);
        sendButton.setText(isLoading ? "思考中..." : "发送");
    }

    /**
     * 显示一个自定义的错误对话框。
     *
     * @param e 捕获到的异常
     */
    private void showErrorDialog(Exception e) {
        String errorMessage = (e instanceof IllegalStateException) ? e.getMessage() : "请求AI服务时发生网络错误。";
        Stage owner = (Stage) this.getScene().getWindow();
        if (owner != null) {
            boolean isDarkMode = owner.getScene().getRoot().getStyleClass().contains("theme-dark");
            CustomDialog.show(owner, CustomDialog.DialogType.ERROR, "操作失败", errorMessage, isDarkMode);
        }
    }

    // =================================================================================
    // 后台逻辑
    // =================================================================================

    /**
     * 构建包含上下文数据的完整提示词。
     *
     * @param userMessage 用户的原始问题
     * @return 格式化后的完整提示词
     */
    private String buildContextualPrompt(String userMessage) {
        if (!analyzeDataCheck.isSelected() || currentBacktestResult == null || currentStrategy == null) {
            return userMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- 以下是当前的量化回测上下文数据 ---\n\n");

        // 1. 策略信息
        sb.append("## 1. 当前策略与参数\n");
        sb.append("- **策略名称:** ").append(currentStrategy.getClass().getSimpleName()).append("\n");
        if (currentStrategy instanceof MovingAverageCrossStrategy macs) {
            sb.append("- **参数:** 短周期 = ").append(macs.getShortBarCount())
                    .append(", 长周期 = ").append(macs.getLongBarCount()).append("\n");
        }
        sb.append("\n");

        // 2. 总体回测表现
        sb.append("## 2. 总体回测表现\n");
        if (currentBacktestResult.finalPortfolio() != null) {
            sb.append(currentBacktestResult.finalPortfolio().getSummary()).append("\n");
        }

        // 3. 最新交易记录 (只展示最近5条)
        sb.append("## 3. 最新交易记录\n");
        List<Order> orders = currentBacktestResult.executedOrders();
        sb.append("| 交易信号 | 价格 | 数量 |\n");
        sb.append("|---|---|---|\n");
        orders.stream().skip(Math.max(0, orders.size() - 5)).forEach(order ->
                sb.append(String.format("| %s | %.2f | %.0f |\n", order.signal(), order.price(), order.quantity()))
        );
        sb.append("\n");

        // 4. 最新指标数据
        sb.append("## 4. 最新K线收盘时的指标数据\n");
        BarSeries series = currentBacktestResult.series();
        if (series != null && !series.isEmpty()) {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
            if (currentStrategy instanceof MovingAverageCrossStrategy macs) {
                EMAIndicator shortEma = new EMAIndicator(closePrice, macs.getShortBarCount());
                EMAIndicator longEma = new EMAIndicator(closePrice, macs.getLongBarCount());
                sb.append(String.format("- **最新收盘价:** %.2f\n", series.getLastBar().getClosePrice().doubleValue()));
                sb.append(String.format("- **短周期均线 (%d):** %.2f\n", macs.getShortBarCount(), shortEma.getValue(series.getEndIndex()).doubleValue()));
                sb.append(String.format("- **长周期均线 (%d):** %.2f\n", macs.getLongBarCount(), longEma.getValue(series.getEndIndex()).doubleValue()));
            }
        }
        sb.append("\n---\n\n");

        // 5. 附上用户的问题
        sb.append("**请基于以上数据，分析以下问题：**\n");
        sb.append(userMessage);

        return sb.toString();
    }

    /**
     * 调用 Kimi API 并获取响应。
     *
     * @param fullPrompt 完整的提示词
     * @return AI 的响应字符串
     * @throws Exception 网络请求或API错误
     */
    private String getKimiResponse(String fullPrompt) throws Exception {
        String apiKey = ConfigurationManager.getInstance().getKimiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Kimi API Key 未设置。请通过主菜单设置。");
        }

        List<JSONObject> messages = new ArrayList<>();
        messages.add(new JSONObject().put("role", "system").put("content", "你是一个专业的量化交易助手。请基于用户提供的上下文数据，用简洁、专业的语言回答问题。"));
        messages.add(new JSONObject().put("role", "user").put("content", fullPrompt));

        JSONObject payload = new JSONObject();
        payload.put("model", KIMI_MODEL);
        payload.put("messages", new JSONArray(messages));
        payload.put("temperature", 0.3);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KIMI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JSONObject responseBody = new JSONObject(response.body());
            return responseBody.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        } else {
            throw new RuntimeException("请求失败，状态码: " + response.statusCode() + ", 响应: " + response.body());
        }
    }
}