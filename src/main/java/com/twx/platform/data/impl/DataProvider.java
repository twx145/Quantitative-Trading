package com.twx.platform.data.impl;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import org.jetbrains.annotations.Nullable;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DoubleNum;

import java.io.*;
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

public class DataProvider implements com.twx.platform.data.DataProvider {

    private static final String KLINE_API_URL_FORMAT = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=%s,%s,%s,%s,640,qfq";
    private static final String SUGGEST_API_URL_FORMAT = "https://suggest3.sinajs.cn/suggest/key=%s";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // 修改正则：精准匹配腾讯返回的 ["日期", "开盘", "收盘", "最高", "最低", "成交量"] 格式
    private static final Pattern TENCENT_JSON_PATTERN = Pattern.compile(
            "\\[\"(?<day>\\d{4}-\\d{2}-\\d{2})\",\"(?<open>[\\d\\.]+)\",\"(?<close>[\\d\\.]+)\",\"(?<high>[\\d\\.]+)\",\"(?<low>[\\d\\.]+)\",\"(?<volume>[\\d\\.]+)\""
    );

    private final HttpClient httpClient;

    public DataProvider() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        // 1. 乱码设置
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // 2. 股票代码处理
        String rawSymbol = ticker.symbol().trim().toLowerCase();
        if (rawSymbol.contains(".")) rawSymbol = rawSymbol.split("\\.")[0];
        String symbol = rawSymbol;
        if (rawSymbol.matches("^\\d+$")) {
            if (rawSymbol.startsWith("6") || rawSymbol.startsWith("9") || rawSymbol.startsWith("5")) symbol = "sh" + rawSymbol;
            else symbol = "sz" + rawSymbol;
        }

        // 3. 构造 URL
        // 修正日期范围（防止未来日期导致空数据）
        if (endDate.isAfter(LocalDate.now())) endDate = LocalDate.now();
        if (startDate.isAfter(endDate)) startDate = endDate.minusDays(1);

        DateTimeFormatter apiDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedUrl = String.format(KLINE_API_URL_FORMAT,
                symbol, "day", startDate.format(apiDateFormatter), endDate.format(apiDateFormatter));

        // 4. 发送请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(formattedUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // 错误检查
            if (response.statusCode() != 200) {
                System.err.println("HTTP错误: " + response.statusCode());
                return new BaseBarSeries(ticker.symbol());
            }

            // 【关键修改】删除了 body.contains("data:[]") 的检查，因为它会误判！
            // 只要不是 "param error"，我们就尝试解析
            if (body.contains("\"msg\":\"param error\"")) {
                System.err.println("API参数错误，请检查代码格式: " + symbol);
                return new BaseBarSeries(ticker.symbol());
            }

            // 5. 直接进入解析
            return parseResponseWithRegex(body, ticker.symbol(), startDate, endDate);

        } catch (Exception e) {
            e.printStackTrace();
            return new BaseBarSeries(ticker.symbol());
        }
    }

    // 4. 解析方法（保持你刚才更新的逻辑，确保字段顺序正确）
    // 解析腾讯 JSON 数据的正则版本
    private BarSeries parseResponseWithRegex(String responseBody, String tickerSymbol, LocalDate startDate, LocalDate endDate) {
        BarSeries series = new BaseBarSeries(tickerSymbol, DoubleNum::valueOf);
        Matcher matcher = TENCENT_JSON_PATTERN.matcher(responseBody);

        int count = 0;
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.parse(matcher.group("day"), DATE_FORMATTER);

                // 简单的日期过滤
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    ZonedDateTime endTime = date.atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1);

                    // 提取数据
                    double open = Double.parseDouble(matcher.group("open"));
                    double close = Double.parseDouble(matcher.group("close"));
                    double high = Double.parseDouble(matcher.group("high"));
                    double low = Double.parseDouble(matcher.group("low"));
                    double volume = Double.parseDouble(matcher.group("volume"));

                    // 【关键】BaseBar 的构造参数顺序通常是: time, open, high, low, close, volume
                    // 请务必确认你的 BaseBar 构造函数参数顺序！下面是常用顺序：
                    series.addBar(new BaseBar(Duration.ofDays(1), endTime, open, high, low, close, volume));
                    count++;
                }
            } catch (Exception e) {
                // 忽略解析错误的行（比如 JSON 中混杂的除权信息）
            }
        }

        System.out.println("成功解析条数: " + count);

        // 如果一条都没解析出来，再打印警告
        if (count == 0) {
            System.err.println("警告：未解析到任何K线数据，请检查正则匹配。");
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