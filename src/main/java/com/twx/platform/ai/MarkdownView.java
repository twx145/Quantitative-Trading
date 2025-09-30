// src/main/java/com/twx/platform/ai/MarkdownView.java
// 【交互升级版】请用这份代码完整替换您现有的同名文件

package com.twx.platform.ai;

import com.twx.platform.ui.CustomDialog; // <<< 新增导入
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import javafx.stage.Stage; // <<< 新增导入

import java.util.Objects;

/**
 * 【交互升级版 v2.2】一个稳定、可靠且美观的 Markdown 显示组件。
 * 交互方式升级：使用右键菜单调用统一样式的对话框来复制文本。
 */
public class MarkdownView extends StackPane {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    private final WebView webView;
    private final boolean isDarkMode;
    private String rawMarkdownContent = "";

    private static final Text heightCalculator = new Text();
    private static final double FIXED_WIDTH = 240.0;
    private static final double PADDING = 12.0 * 2;
    private static final double LINE_SPACING = 5;

    static {
        MutableDataSet options = new MutableDataSet();
        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();

        heightCalculator.setFont(Font.font("System", 13));
        heightCalculator.setWrappingWidth(FIXED_WIDTH - PADDING);
        heightCalculator.setLineSpacing(LINE_SPACING);
    }

    public MarkdownView(boolean isDarkMode) {
        super();
        this.isDarkMode = isDarkMode;
        this.webView = new WebView();

        this.setPrefWidth(FIXED_WIDTH);
        this.setMaxWidth(FIXED_WIDTH);

        this.webView.setPrefWidth(FIXED_WIDTH);
        this.webView.setMaxWidth(FIXED_WIDTH);
        this.webView.setContextMenuEnabled(false);
        this.webView.setStyle("-fx-background-color: transparent;");
        this.webView.setMouseTransparent(true);

        this.getChildren().add(this.webView);

        // =================================================================================
        // >>> 核心修改区域：从左键单击改为右键请求，并调用新的CustomDialog <<<
        // =================================================================================
        this.setOnContextMenuRequested(event -> {
            if (!rawMarkdownContent.isEmpty()) {
                Stage owner = (Stage) this.getScene().getWindow();
                if (owner != null) {
                    CustomDialog.showCopyableText(
                            owner,
                            "复制内容",
                            "您可以从下面的文本框中复制AI回复的全部内容。",
                            rawMarkdownContent,
                            isDarkMode
                    );
                }
                event.consume(); // 消费事件，防止系统默认的上下文菜单出现
            }
        });
    }

    public void updateContent(String markdownContent) {
        // ... [此方法保持不变] ...
        this.rawMarkdownContent = markdownContent;

        if (markdownContent == null || markdownContent.isBlank()) {
            Platform.runLater(() -> {
                webView.getEngine().loadContent("");
                this.setPrefHeight(0);
            });
            return;
        }

        String plainText = markdownContent.replaceAll("`{1,3}[^`]*`{1,3}", " code ")
                .replaceAll("[*#_>-]", "");
        heightCalculator.setText(plainText);
        double requiredHeight = heightCalculator.getLayoutBounds().getHeight() + 30;

        Node document = PARSER.parse(markdownContent);
        String contentHtml = RENDERER.render(document);
        String fullHtml = buildFullHtml(contentHtml);

        Platform.runLater(() -> {
            this.setPrefHeight(requiredHeight);
            webView.getEngine().loadContent(fullHtml);
        });
    }

    private String buildFullHtml(String contentHtml) {
        // ... [此方法保持不变] ...
        String mainCssPath = Objects.requireNonNull(getClass().getResource("/com/twx/platform/ui/style.css")).toExternalForm();
        String bodyClass = isDarkMode ? "theme-dark" : "";

        return """
               <!DOCTYPE html>
               <html>
                 <head>
                   <meta charset="UTF-8">
                   <link rel="stylesheet" type="text/css" href="%s">
                   <style>
                     :root {
                       --font-family-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                       --font-family-mono: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace;
                       --font-size-base: 13px;
                       --line-height-base: 1.6;
                       --border-radius: 6px;
                       --text-primary: -fx-theme-text-primary;
                       --text-secondary: -fx-theme-text-secondary;
                       --text-link: -fx-theme-accent-primary;
                       --bg-primary: transparent;
                       --bg-secondary: -fx-theme-base-bg;
                       --bg-code: -fx-theme-accent-grey;
                       --border-color: -fx-theme-border;
                     }
                     html, body { margin: 0; padding: 0; font-family: var(--font-family-sans); font-size: var(--font-size-base); line-height: var(--line-height-base); color: var(--text-primary); background-color: var(--bg-primary); word-wrap: break-word; overflow-wrap: break-word; overflow: hidden; }
                     .content-wrapper { padding: 8px 12px; }
                     p { margin-top: 0; margin-bottom: 12px; }
                     a { color: var(--text-link); text-decoration: none; }
                     a:hover { text-decoration: underline; }
                     h1, h2, h3, h4, h5, h6 { margin: 20px 0 10px 0; font-weight: 600; line-height: 1.3; }
                     h2 { font-size: 1.4em; }
                     h3 { font-size: 1.2em; }
                     pre { background-color: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: var(--border-radius); padding: 12px; font-family: var(--font-family-mono); font-size: 0.95em; overflow-x: auto; white-space: pre-wrap; word-break: break-all; margin-bottom: 12px; }
                     code { font-family: var(--font-family-mono); background-color: var(--bg-code); color: var(--text-primary); padding: 0.2em 0.4em; border-radius: 4px; font-size: 0.9em; }
                     pre > code { background-color: transparent; padding: 0; border-radius: 0; font-size: inherit; }
                     blockquote { margin: 0 0 12px 0; padding: 4px 12px; border-left: 3px solid var(--border-color); color: var(--text-secondary); }
                     ul, ol { margin: 0 0 12px 0; padding-left: 24px; }
                     li { margin-bottom: 4px; }
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
}