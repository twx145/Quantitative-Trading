package com.twx.platform.data.impl;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 从新浪财经获取实时数据快照的实现。
 * 注意：此接口仅返回最新的单条行情数据，不提供历史范围查询。
 * 因此，传入的 startDate 和 endDate 参数将被忽略。
 * 返回的 BarSeries 将只包含一个 Bar。
 */
public class SinaDataProvider implements DataProvider {

    private final OkHttpClient client = new OkHttpClient();

    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        // 创建一个ta4j的BarSeries实例
        BarSeries series = new BaseBarSeries(ticker.symbol() + "_SERIES");

        // 新浪接口需要带前缀的股票代码, e.g., "sh600519"
        Request request = new Request.Builder()
                .url("http://hq.sinajs.cn/list=" + ticker.symbol())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("从新浪获取数据失败: " + response);
                return series; // 返回空的series
            }

            // 新浪接口使用GBK编码，必须正确转换
            String responseBody = new String(Objects.requireNonNull(response.body()).bytes(), "GBK");
            String data = responseBody.substring(responseBody.indexOf("\"") + 1, responseBody.lastIndexOf("\""));
            String[] parts = data.split(",");

            if (parts.length < 32) {
                System.err.println("数据格式不正确或股票代码无效: " + ticker.symbol());
                return series; // 返回空的series
            }

            // --- 解析新浪数据并转换为ta4j的Bar ---
            BigDecimal open = new BigDecimal(parts[1]);
            BigDecimal high = new BigDecimal(parts[4]);
            BigDecimal low = new BigDecimal(parts[5]);
            BigDecimal close = new BigDecimal(parts[3]); // 当前价作为收盘价
            BigDecimal volume = new BigDecimal(parts[8]);

            // 解析日期和时间
            LocalDate barDate = LocalDate.parse(parts[30], DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalTime barTime = LocalTime.parse(parts[31], DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalDateTime barDateTime = LocalDateTime.of(barDate, barTime);

            // 添加唯一的Bar到series中
            series.addBar(
                    barDateTime.atZone(ZoneId.systemDefault()),
                    open,
                    high,
                    low,
                    close,
                    volume
            );

        } catch (IOException | NumberFormatException e) {
            System.err.println("处理新浪数据时发生错误 for ticker " + ticker.symbol() + ": " + e.getMessage());
            e.printStackTrace();
        }

        return series;
    }
}