// src/main/java/com/twx/platform/ai/SearchService.java
// 【国内网络优化版】请用这份代码完整替换您现有的同名文件

package com.twx.platform.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 网络搜索服务 v2.1 (基于 Jsoup, 优化国内网络环境)。
 * 通过直接抓取和解析必应搜索(cn.bing.com)的HTML结果，提供在国内稳定、免费且无API Key依赖的搜索功能。
 */
public class SearchService {

    private static final int MAX_RESULTS = 5; // 最多取前4条结果，以保证上下文简洁
    // 使用在国内访问稳定的必应搜索
    private static final String SEARCH_URL_TEMPLATE = "https://cn.bing.com/search?q=%s";
    // 模拟一个常见的浏览器User-Agent
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000; // 15秒超时，国内访问有时需要更长时间

    public SearchService() {
        // 构造函数为空
    }

    /**
     * 执行网络搜索。
     *
     * @param query 用户查询关键词
     * @return 格式化后的搜索结果摘要
     * @throws IOException 如果网络连接或HTML解析失败
     */
    public String search(String query) throws IOException {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String requestUrl = String.format(SEARCH_URL_TEMPLATE, encodedQuery);

            // 使用 Jsoup 连接并获取 HTML 文档
            Document doc = Jsoup.connect(requestUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // 解析HTML并提取结果
            return parseBingSearchResults(doc);

        } catch (Exception e) {
            // 将所有异常（包括超时、SSL问题等）统一包装为IOException
            throw new IOException("执行网络搜索时发生错误: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 Jsoup 从必应搜索获取的 Document 对象，并将其格式化为易读的文本。
     *
     * @param doc Jsoup 解析后的 HTML 文档对象
     * @return 格式化后的结果文本
     */
    private String parseBingSearchResults(Document doc) {
        // 必应搜索的结果项通常在 id="b_results" 下的 <li> 标签中，且带有 "b_algo" 类
        Elements results = doc.select("#b_results > li.b_algo");

        if (results.isEmpty()) {
            return "未找到相关的网络搜索结果。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**网络搜索结果摘要：**\n\n");

        int count = 0;
        for (Element result : results) {
            if (count >= MAX_RESULTS) break;

            // 提取标题和链接 (通常在 <h2> 下的 <a> 标签)
            Element titleElement = result.selectFirst("h2 > a");
            // 提取摘要 (通常在 class="b_caption" 的 <div> 下的 <p> 标签)
            Element snippetElement = result.selectFirst("div.b_caption > p");

            if (titleElement != null && snippetElement != null) {
                String title = titleElement.text();
                String url = titleElement.attr("href"); // 获取链接
                String snippet = snippetElement.text();

                // 过滤掉没有实际内容的项和广告
                if (!title.isEmpty() && !snippet.isEmpty() && !url.startsWith("https://go.microsoft.com")) {
                    sb.append(count + 1).append(". **").append(title).append("**\n");
                    sb.append("   ").append(snippet).append("\n");
                    if (!url.isEmpty()) {
                        sb.append("   来源: [链接](").append(url).append(")\n\n");
                    }
                    count++;
                }
            }
        }

        if (count == 0) {
            return "无法从搜索页面中解析出有效结果。(可能是页面结构已更新)";
        }

        return sb.toString();
    }
}