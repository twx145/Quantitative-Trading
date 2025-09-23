// src/main/java/com/twx/platform/ai/MarkdownView.java
package com.twx.platform.ai;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 一个用于解析和显示 Markdown 文本的自定义 JavaFX 组件。
 * 它实现了高度自适应，并能正确处理亮色/暗色主题。
 * 内部使用 WebView 来渲染内容。
 */
public class MarkdownView extends StackPane {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    private final WebView webView;
    private final boolean isDarkMode;

    // 使用静态初始化块，高效地创建 Markdown 解析器和渲染器的单例
    static {
        MutableDataSet options = new MutableDataSet();
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    public MarkdownView(boolean isDarkMode) {
        super();
        this.isDarkMode = isDarkMode;
        this.webView = new WebView();

        // --- 1. 尺寸与样式初始化 ---
        // 初始尺寸约束，防止组件在自适应前无限扩张
        this.webView.setMinSize(50, 20);
        this.webView.setPrefSize(240, 50); // 初始首选宽度与AI面板宽度匹配
        this.webView.setMaxWidth(240);
        this.webView.setContextMenuEnabled(false); // 禁用右键菜单

        // 强制设置 WebView 节点背景为透明
        this.webView.setStyle("-fx-background-color: transparent;");

        this.getChildren().add(this.webView);

        // --- 2. 设置监听器，实现高度自适应和背景修复 ---
        setupWebViewListeners();
    }

    /**
     * 设置 WebView 的核心监听器。
     * 监听加载状态，在加载完成后执行 JS 回调以实现高度自适应和样式修复。
     */
    private void setupWebViewListeners() {
        final WebEngine engine = webView.getEngine();

        engine.getLoadWorker().stateProperty().addListener(
                (obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        // --- A. 修复暗色模式下的白色背景 ---
                        // 强制将HTML内部背景也设为透明
                        engine.executeScript(
                                "document.documentElement.style.backgroundColor = 'transparent'; " +
                                        "document.body.style.backgroundColor = 'transparent';"
                        );

                        // --- B. 实现高度自适应 ---
                        // 创建一个 Java 回调对象
                        JavaCallback javaCallback = new JavaCallback(height -> {
                            // 收到 JS 返回的高度后，应用到 WebView 和 StackPane
                            double newHeight = height + 5; // 加一点 buffer 作为边距
                            double finalHeight = Math.min(newHeight, 400.0); // 限制最大高度为400px

                            webView.setPrefHeight(finalHeight);
                            this.setPrefHeight(finalHeight);
                        });

                        // 将 Java 回调对象暴露给 JavaScript
                        JSObject window = (JSObject) engine.executeScript("window");
                        window.setMember("javaCallback", javaCallback);

                        // 使用 requestAnimationFrame 在下一个渲染帧获取最准确的高度
                        String script = """
                                    function getHeight() {
                                      // 使用 scrollHeight 来获取内容的总高度
                                      return document.body.scrollHeight;
                                    }
                                    requestAnimationFrame(function() {
                                      javaCallback.onHeightReceived(getHeight());
                                    });
                                    """;
                        engine.executeScript(script);
                    }
                }
        );
    }

    /**
     * 对外暴露的核心方法：更新显示的内容。
     * @param markdownContent Markdown 格式的字符串
     */
    public void updateContent(String markdownContent) {
        Node document = PARSER.parse(markdownContent);
        String contentHtml = RENDERER.render(document);
        String fullHtml = buildFullHtml(contentHtml);
        this.webView.getEngine().loadContent(fullHtml, "text/html");
    }

    /**
     * 使用 Java 文本块构建一个包含主题样式和内容的完整 HTML 页面。
     * @param contentHtml Markdown 解析后的 HTML 内容
     * @return 完整的 HTML 字符串
     */
    private String buildFullHtml(String contentHtml) {
        String mainCssPath = Objects.requireNonNull(getClass().getResource("/com/twx/platform/ui/style.css")).toExternalForm();
        String bodyClass = isDarkMode ? "theme-dark" : "";

        return """
               <!DOCTYPE html>
               <html>
                 <head>
                   <meta charset="UTF-8">
                   <link rel="stylesheet" type="text/css" href="%s">
                   <style>
                     html, body { margin: 0; padding: 0; height: auto; font-size: 12px; }
                     .content-wrapper { padding: 4px 8px; color: -fx-theme-text-primary; }
                     p { line-height: 1.6; }
                     h1, h2, h3 { border-bottom: 1px solid -fx-theme-border; padding-bottom: 4px; }
                     pre { background-color: -fx-theme-base-bg; padding: 10px; border-radius: 4px; border: 1px solid -fx-theme-border; overflow-x: auto; }
                     code { font-family: Consolas, 'Courier New', monospace; background-color: -fx-theme-accent-grey; padding: 2px 5px; border-radius: 3px; color: -fx-theme-text-primary; }
                     pre > code { background-color: transparent; padding: 0; }
                     blockquote { border-left: 3px solid -fx-theme-border; padding-left: 12px; color: -fx-theme-text-secondary; margin-left: 0; }
                     ul, ol { padding-left: 20px; }
                   </style>
                 </head>
                 <body class="%s">
                   <div class="content-wrapper">
                     %s
                   </div>
                 </body>
               </html>
               """.formatted(mainCssPath, bodyClass, contentHtml);
    }

    /**
         * 一个内部类，专门用于从 JavaScript 进行回调。
         * 其 public 方法可以被 JavaScript 环境安全调用。
         */
        public record JavaCallback(Consumer<Double> heightConsumer) {
    }
}