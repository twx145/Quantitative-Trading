package com.twx.platform.data.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从新浪财经获取A股历史数据的实现。
 * 注意：新浪接口可能不稳定，且返回的数据量有限制。
 */
public class SinaDataProvider implements DataProvider {

    private static final String API_URL_FORMAT = "http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=%s&scale=240&ma=no&datalen=10000";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HttpClient httpClient;

    public SinaDataProvider() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        // 新浪接口主要用于日线数据，这里暂时忽略 timeFrame
        String formattedUrl = String.format(API_URL_FORMAT, ticker.symbol());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(formattedUrl))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().equalsIgnoreCase("null")) {
                System.err.println("从新浪财经获取数据失败，股票代码: " + ticker.symbol() + ", 状态码: " + response.statusCode());
                return new BaseBarSeries(ticker.symbol());
            }

            return parseJsonToBarSeries(response.body(), ticker.symbol(), startDate, endDate);

        } catch (IOException | InterruptedException e) {
            System.err.println("请求新浪财经API时发生错误: " + e.getMessage());
            Thread.currentThread().interrupt(); // 恢复中断状态
            return new BaseBarSeries(ticker.symbol());
        }
    }

    /**
     * 解析新浪财经返回的JSON字符串为BarSeries对象。
     */
    private BarSeries parseJsonToBarSeries(String jsonResponse, String tickerSymbol, LocalDate startDate, LocalDate endDate) {
        BarSeries series = new BaseBarSeries(tickerSymbol);
        JsonElement jsonElement = JsonParser.parseString(jsonResponse);

        if (!jsonElement.isJsonArray()) {
            System.err.println("无效的JSON格式，期望得到一个数组。");
            return series;
        }

        JsonArray jsonArray = jsonElement.getAsJsonArray();
        List<Bar> bars = new ArrayList<>();

        for (JsonElement element : jsonArray) {
            JsonObject barObject = element.getAsJsonObject();
            LocalDate date = LocalDate.parse(barObject.get("day").getAsString(), DATE_FORMATTER);

            // 筛选指定日期范围内的数据
            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                double open = barObject.get("open").getAsDouble();
                double high = barObject.get("high").getAsDouble();
                double low = barObject.get("low").getAsDouble();
                double close = barObject.get("close").getAsDouble();
                double volume = barObject.get("volume").getAsDouble();

                ZonedDateTime endTime = date.atStartOfDay(ZoneId.systemDefault()).plusDays(1).minusNanos(1);

                bars.add(new BaseBar(Duration.ofDays(1), endTime, open, high, low, close, volume));
            }
        }

        // 新浪接口返回的数据是按日期升序的，ta4j 需要的就是这个顺序
        bars.forEach(series::addBar);
        return series;
    }
}