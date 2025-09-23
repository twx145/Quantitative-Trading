package com.twx.platform.data.impl;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import org.jetbrains.annotations.Nullable;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SinaDataProvider implements DataProvider {

    private static final String KLINE_API_URL_FORMAT = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&ma=no&datalen=10000";
    private static final String SUGGEST_API_URL_FORMAT = "https://suggest3.sinajs.cn/suggest/key=%s";
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

    // ... getHistoricalData 和 getCompanyName 方法保持不变 ...
    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        String formattedUrl = String.format(KLINE_API_URL_FORMAT, ticker.symbol());
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(formattedUrl)).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().equalsIgnoreCase("null") || response.body().trim().isEmpty()) {
                System.err.println("获取数据失败，代码: " + ticker.symbol() + ", 状态码: " + response.statusCode());
                return new BaseBarSeries(ticker.symbol());
            }
            return parseResponseWithRegex(response.body(), ticker.symbol(), startDate, endDate);
        } catch (IOException | InterruptedException e) {
            System.err.println("请求API时发生错误: " + e.getMessage());
            Thread.currentThread().interrupt();
            return new BaseBarSeries(ticker.symbol());
        }
    }

    private BarSeries parseResponseWithRegex(String responseBody, String tickerSymbol, LocalDate startDate, LocalDate endDate) {
        BarSeries series = new BaseBarSeries(tickerSymbol, DoubleNum::valueOf);
        Matcher matcher = SINA_BAR_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group("day"), DATE_FORMATTER);
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    ZonedDateTime endTime = date.atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1);
                    series.addBar(new BaseBar(Duration.ofDays(1), endTime, Double.parseDouble(matcher.group("open")), Double.parseDouble(matcher.group("high")), Double.parseDouble(matcher.group("low")), Double.parseDouble(matcher.group("close")), Double.parseDouble(matcher.group("volume"))));
                }
            } catch (Exception e) {
                System.err.println("解析K线数据出错: " + matcher.group(0) + " | 错误: " + e.getMessage());
            }
        }
        return series;
    }

    @Override
    public String getCompanyName(Ticker ticker) throws IOException {
        String urlString = "https://hq.sinajs.cn/list=" + ticker.toString();
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://finance.sina.com.cn/");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "GBK"))) {
            String inputLine = in.readLine();
            if (inputLine != null && !inputLine.contains("\"\"")) {
                String dataPart = inputLine.substring(inputLine.indexOf("\"") + 1, inputLine.lastIndexOf("\""));
                String[] parts = dataPart.split(",");
                return parts[0];
            } else {
                throw new IOException("无法找到该股票代码: " + ticker);
            }
        }
    }

    /**
     * 【修改】实现带市场筛选的股票搜索功能
     */
    @Override
    public List<StockSuggestion> searchStocks(String keyword, MarketType marketType) throws IOException, InterruptedException {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String formattedUrl = String.format(SUGGEST_API_URL_FORMAT, encodedKeyword);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(formattedUrl)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        List<StockSuggestion> suggestions = new ArrayList<>();
        if (response.statusCode() != 200 || response.body() == null) {
            return suggestions;
        }

        String body = response.body();
        int start = body.indexOf('"');
        int end = body.lastIndexOf('"');

        if (start == -1 || end <= start) {
            return suggestions;
        }

        String data = body.substring(start + 1, end);
        String[] stocks = data.split(";");

        for (String stockInfo : stocks) {
            if (stockInfo == null || stockInfo.isEmpty()) continue;
            String[] parts = stockInfo.split(",");
            if (parts.length < 5) continue;

            StockSuggestion suggestion = getStockSuggestion(marketType, parts);

            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    private static @Nullable StockSuggestion getStockSuggestion(MarketType marketType, String[] parts) {
        String marketId = parts[0];
        String shortCode = parts[2];
        String fullTicker = parts[3];
        String name = parts[4];

        StockSuggestion suggestion = null;

        // 根据市场类型进行筛选
        if (marketType == MarketType.A_SHARE && (fullTicker.startsWith("sh") || fullTicker.startsWith("sz"))) {
            suggestion = new StockSuggestion(fullTicker, name);
        } else if (marketType == MarketType.HK_STOCK && marketId.equals("hk")) {
            suggestion = new StockSuggestion("hk" + shortCode, name);
        } else if (marketType == MarketType.US_STOCK && marketId.equals("us")) {
            // 美股代码需要加上 'gb_' 前缀并在实际使用时转为小写
            suggestion = new StockSuggestion("gb_" + shortCode.toLowerCase(), name);
        } else if (marketType == MarketType.FUND && marketId.equals("jj")) {
            suggestion = new StockSuggestion(fullTicker, name);
        } else if (marketType == MarketType.ALL) {
            // ALL 类型需要单独判断，以构造正确的 ticker
            if (fullTicker.startsWith("sh") || fullTicker.startsWith("sz")) {
                suggestion = new StockSuggestion(fullTicker, name);
            } else if (marketId.equals("hk")) {
                suggestion = new StockSuggestion("hk" + shortCode, name);
            } else if (marketId.equals("us")) {
                suggestion = new StockSuggestion("gb_" + shortCode.toLowerCase(), name);
            } else if (marketId.equals("jj")) {
                suggestion = new StockSuggestion(fullTicker, name);
            }
        }
        return suggestion;
    }
}