package com.twx.platform.data.impl;

// 移除了所有 com.google.gson.* 的导入
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DoubleNum;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern; // 导入正则表达式库

/**
 * 从新浪财经获取A股历史数据的实现。
 * 此版本不使用Gson库，而是通过正则表达式手动解析数据。
 */
public class SinaDataProvider implements DataProvider {

    private static final String API_URL_FORMAT = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&ma=no&datalen=10000";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern SINA_BAR_PATTERN = Pattern.compile(
            "\\{\"day\":\"(?<day>[^\"]+)\",\"open\":\"(?<open>[^\"]+)\",\"high\":\"(?<high>[^\"]+)\",\"low\":\"(?<low>[^\"]+)\",\"close\":\"(?<close>[^\"]+)\",\"volume\":\"(?<volume>[^\"]+)\"}"
    );

    private final HttpClient httpClient;

    public SinaDataProvider() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 在 SinaDataProvider.java 中

    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        String formattedUrl = String.format(API_URL_FORMAT, ticker.symbol());
        System.out.println("正在从以下URL获取数据: " + formattedUrl); // <-- 新增日志：打印请求的URL

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(formattedUrl)).GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

//            System.out.println("API响应状态码: " + response.statusCode());
//            System.out.println("API响应内容: " + response.body()); // 如果数据太多，可以先注释掉这行

            if (response.statusCode() != 200 || response.body() == null || response.body().equalsIgnoreCase("null") || response.body().trim().isEmpty()) {
                System.err.println("从新浪财经获取数据失败，股票代码: " + ticker.symbol() + ", 状态码: " + response.statusCode());
                System.err.println("失败的响应内容是: " + response.body()); // <-- 新增日志：打印失败时的内容
                return new BaseBarSeries(ticker.symbol());
            }

            return parseResponseWithRegex(response.body(), ticker.symbol(), startDate, endDate);

        } catch (IOException | InterruptedException e) {
            System.err.println("请求新浪财经API时发生网络或线程错误: " + e.getMessage());
            e.printStackTrace(); // <-- 新增日志：打印详细的异常堆栈
            Thread.currentThread().interrupt();
            return new BaseBarSeries(ticker.symbol());
        }
    }

    /**
     * 【已修改】使用正则表达式解析响应字符串为BarSeries对象。
     */
    private BarSeries parseResponseWithRegex(String responseBody, String tickerSymbol, LocalDate startDate, LocalDate endDate) {
        BarSeries series = new BaseBarSeries(tickerSymbol, DoubleNum::valueOf);
        Matcher matcher = SINA_BAR_PATTERN.matcher(responseBody);

        // 循环查找所有匹配的K线数据
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group("day"), DATE_FORMATTER);

                // 筛选指定日期范围内的数据
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    double open = Double.parseDouble(matcher.group("open"));
                    double high = Double.parseDouble(matcher.group("high"));
                    double low = Double.parseDouble(matcher.group("low"));
                    double close = Double.parseDouble(matcher.group("close"));
                    double volume = Double.parseDouble(matcher.group("volume"));

                    ZonedDateTime endTime = date.atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1);
                    series.addBar(new BaseBar(Duration.ofDays(1), endTime, open, high, low, close, volume));
                }
            } catch (Exception e) {
                System.err.println("解析单条K线数据时出错: " + matcher.group(0) + " | 错误: " + e.getMessage());
            }
        }

        return series;
    }

    @Override
    public String getCompanyName(Ticker ticker) throws IOException {
        String urlString = "http://hq.sinajs.cn/list=" + ticker.toString();
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // ★ 核心修复：添加浏览器头信息来“伪装”自己
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");
        conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "GBK"))) {
            String inputLine = in.readLine();
            if (inputLine != null && !inputLine.contains("\"\"")) {
                String dataPart = inputLine.substring(inputLine.indexOf("\"") + 1, inputLine.lastIndexOf("\""));
                String[] parts = dataPart.split(",");
                return parts[0];
            } else {
                throw new IOException("无法找到该股票代码或返回数据为空: " + ticker.toString());
            }
        }
    }
}