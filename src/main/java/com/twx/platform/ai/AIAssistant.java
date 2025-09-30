// src/main/java/com/twx/platform/ai/AIAssistant.java
package com.twx.platform.ai;

import com.twx.platform.common.ConfigurationManager;
import com.twx.platform.common.Order;
import com.twx.platform.engine.BacktestResult;
import com.twx.platform.strategy.Strategy;
import com.twx.platform.strategy.impl.MovingAverageCrossStrategy;
import com.twx.platform.ui.CustomDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONException;
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
import java.util.function.Consumer; // >>> 新增导入 <<<
import java.net.CookieManager;

public class AIAssistant extends VBox {

    private static final String KIMI_API_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String KIMI_MODEL = "moonshot-v1-8k";

    private final VBox chatHistory;
    private final TextArea inputArea;
    private final Button sendButton;
    private final CheckBox analyzeDataCheck;
    private final CheckBox enableSearchCheck;

    private final HttpClient httpClient;
    private final SearchService searchService;
    private BacktestResult currentBacktestResult;
    private Strategy currentStrategy;

    public AIAssistant() {
        super(10);
        this.setPadding(new Insets(10));
        this.getStyleClass().add("side-panel");
        this.setPrefWidth(300);

        // 修改后的代码
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(new CookieManager()) // <<< 核心改动：为HttpClient提供一个独立的Cookie处理器
                .build();

        this.searchService = new SearchService();

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

        analyzeDataCheck = new CheckBox("附带当前回测数据");
        analyzeDataCheck.setDisable(true);
        enableSearchCheck = new CheckBox("启用网络搜索");
        enableSearchCheck.setTooltip(new Tooltip("勾选后，AI将先搜索网络获取最新信息，再回答您的问题。"));

        HBox optionsBox = new HBox(10, analyzeDataCheck, enableSearchCheck);
        VBox inputGroup = new VBox(5, optionsBox, inputArea);
        this.getChildren().addAll(title, scrollPane, inputGroup, sendButton);

        initializeWelcomeMessage();
    }

    // ... [其它未改变的方法, 如 updateAnalysisContext, initializeWelcomeMessage] ...
    public void updateAnalysisContext(BacktestResult result, Strategy strategy) {
        this.currentBacktestResult = result;
        this.currentStrategy = strategy;
        boolean hasData = result != null && result.executedOrders() != null && !result.executedOrders().isEmpty();
        analyzeDataCheck.setDisable(!hasData);
        if (hasData) {
            analyzeDataCheck.setSelected(true);
            Platform.runLater(() -> addMessage("回测数据已更新，您可以开始提问分析了。", "ai-static")); // Use a different type for static messages
        } else {
            analyzeDataCheck.setSelected(false);
        }
    }

    private void initializeWelcomeMessage() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && chatHistory.getChildren().isEmpty()) {
                addMessage("你好！我是您的量化交易助手。", "ai-static");
            }
        });
    }


    // =================================================================================
    // >>> 核心修改区域：事件处理与后台逻辑 <<<
    // =================================================================================

    private void handleSendMessage() {
        String userMessage = inputArea.getText().trim();
        if (userMessage.isEmpty()) return;

        addMessage(userMessage, "user");
        inputArea.clear();
        setUiLoading(true);

        // 1. 为即将到来的 AI 流式响应创建一个占位符
        MarkdownView aiResponseView = (MarkdownView) addMessage("", "ai-streaming");
        StringBuilder fullResponseContent = new StringBuilder();

        // 2. 在后台线程中执行所有耗时操作
        new Thread(() -> {
            try {
                // 2.1 (可选) 进行网络搜索
                String searchResults = null;
                if (enableSearchCheck.isSelected()) {
                    Platform.runLater(() -> sendButton.setText("搜索网络中..."));
                    try {
                        searchResults = searchService.search(userMessage);
                    } catch (Exception e) {
                        System.err.println("网络搜索失败: " + e.getMessage());
                        searchResults = "（网络搜索失败）";
                    }
                }

                Platform.runLater(() -> sendButton.setText("思考中..."));

                // 2.2 构建最终的提示词
                String fullPrompt = buildContextualPrompt(userMessage, searchResults);

                // 2.3 调用流式 API
                getKimiResponseStream(fullPrompt,
                        // onChunkReceived: 每收到一个文本块时执行
                        (chunk) -> {
                            fullResponseContent.append(chunk);
                            // 在 UI 线程上更新 MarkdownView
                            Platform.runLater(() -> aiResponseView.updateContent(fullResponseContent.toString()));
                        },
                        // onComplete: 流结束时执行
                        () -> Platform.runLater(() -> setUiLoading(false)),
                        // onError: 发生错误时执行
                        (error) -> Platform.runLater(() -> {
                            showErrorDialog(error);
                            setUiLoading(false);
                        })
                );

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showErrorDialog(e);
                    setUiLoading(false);
                });
            }
        }).start();
    }

    /**
     * 向聊天记录UI中添加一条消息。
     * @param message 消息内容
     * @param type    消息类型 ("user", "ai-static", "ai-streaming")
     * @return 如果是流式消息，返回创建的 MarkdownView 实例，否则返回 null
     */
    private Node addMessage(String message, String type) {
        BorderPane wrapper = new BorderPane();
        Node contentNode = null;

        if ("user".equals(type)) {
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.setPadding(new Insets(8));
            messageLabel.getStyleClass().addAll("chat-bubble", "user-bubble");
            wrapper.setRight(messageLabel);
            contentNode = messageLabel;
        } else { // "ai-static" or "ai-streaming"
            boolean isDarkMode = getScene() != null && getScene().getRoot().getStyleClass().contains("theme-dark");
            MarkdownView markdownView = new MarkdownView(isDarkMode);
            if (!message.isEmpty()) {
                markdownView.updateContent(message);
            }
            markdownView.getStyleClass().addAll("chat-bubble", "ai-bubble");
            wrapper.setLeft(markdownView);
            contentNode = markdownView;
        }
        chatHistory.getChildren().add(wrapper);
        return contentNode;
    }

    private void setUiLoading(boolean isLoading) {
        inputArea.setDisable(isLoading);
        sendButton.setDisable(isLoading);
        enableSearchCheck.setDisable(isLoading);
        if (!isLoading) {
            sendButton.setText("发送");
        }
    }

    private void showErrorDialog(Exception e) {
        String errorMessage = (e instanceof IllegalStateException) ? e.getMessage() : "请求AI服务时发生错误。";
        Stage owner = (Stage) this.getScene().getWindow();
        if (owner != null) {
            boolean isDarkMode = owner.getScene().getRoot().getStyleClass().contains("theme-dark");
            CustomDialog.show(owner, CustomDialog.DialogType.ERROR, "操作失败", errorMessage, isDarkMode);
        }
    }

    private String buildContextualPrompt(String userMessage, String searchResults) {
        // ... [这个方法保持不变] ...
        StringBuilder sb = new StringBuilder();
        boolean hasContext = false;

        if (analyzeDataCheck.isSelected() && currentBacktestResult != null && currentStrategy != null) {
            sb.append("--- 以下是当前的量化回测上下文数据 ---\n\n");
            sb.append("## 1. 当前策略与参数\n");
            sb.append("- **策略名称:** ").append(currentStrategy.getClass().getSimpleName()).append("\n");
            if (currentStrategy instanceof MovingAverageCrossStrategy macs) {
                sb.append("- **参数:** 短周期 = ").append(macs.getShortBarCount())
                        .append(", 长周期 = ").append(macs.getLongBarCount()).append("\n");
            }
            sb.append("\n");
            sb.append("## 2. 总体回测表现\n");
            if (currentBacktestResult.finalPortfolio() != null) {
                sb.append(currentBacktestResult.finalPortfolio().getSummary()).append("\n");
            }
            sb.append("## 3. 最新交易记录\n");
            List<Order> orders = currentBacktestResult.executedOrders();
            sb.append("| 交易信号 | 价格 | 数量 |\n");
            sb.append("|---|---|---|\n");
            orders.stream().skip(Math.max(0, orders.size() - 5)).forEach(order ->
                    sb.append(String.format("| %s | %.2f | %.0f |\n", order.signal(), order.price(), order.quantity()))
            );
            sb.append("\n");
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
            hasContext = true;
        }

        if (searchResults != null && !searchResults.isEmpty()) {
            sb.append("--- 以下是针对用户问题的实时网络搜索结果（这些内容是由AIagent查询的，也就是应用后台查询到的） ---\n\n");
            sb.append(searchResults).append("\n");
            sb.append("\n---\n\n");
            hasContext = true;
        }

        if(hasContext){
            sb.append("**请结合以上提供的上下文信息（如果有），回答以下问题：**\n");
        } else {
            return userMessage;
        }

        sb.append(userMessage);
        return sb.toString();
    }


    /**
     * [新] 调用 Kimi API 并以流式方式获取响应。
     *
     * @param fullPrompt       完整的提示词
     * @param onChunkReceived  每当收到新的文本块时调用的回调
     * @param onComplete       流结束时调用的回调
     * @param onError          发生错误时调用的回调
     */
    private void getKimiResponseStream(String fullPrompt, Consumer<String> onChunkReceived, Runnable onComplete, Consumer<Exception> onError) {
        try {
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
            payload.put("stream", true); // <<< 开启流式响应

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KIMI_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            response.body().forEach(line -> {
                                if (line.startsWith("data: ")) {
                                    String jsonPart = line.substring(6);
                                    if ("[DONE]".equals(jsonPart)) {
                                        return; // 流结束
                                    }
                                    try {
                                        JSONObject json = new JSONObject(jsonPart);
                                        String chunk = json.getJSONArray("choices").getJSONObject(0).getJSONObject("delta").getString("content");
                                        onChunkReceived.accept(chunk);
                                    } catch (JSONException e) {
                                        // 忽略无法解析的行
                                    }
                                }
                            });
                            onComplete.run();
                        } else {
                            String errorBody = "请求失败，状态码: " + response.statusCode();
                            // 异步读取响应体可能比较复杂，这里简化处理
                            onError.accept(new RuntimeException(errorBody));
                        }
                    })
                    .exceptionally(ex -> {
                        onError.accept(new Exception("请求AI服务时发生网络错误。", ex));
                        return null;
                    });

        } catch (Exception e) {
            onError.accept(e);
        }
    }
}