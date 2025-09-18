package com.twx.platform.data.impl;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import com.twx.platform.data.DataProvider;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;

// 从雅虎财经获取数据的具体实现
public class YahooDataProvider implements DataProvider {

    @Override
    public BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame) {
        // 创建一个ta4j的BarSeries实例
        BarSeries series = new BaseBarSeries(ticker.symbol() + "_SERIES");

        // --- 新增的重试逻辑 ---
        int maxRetries = 3; // 最多重试3次
        int retryCount = 0;
        long delay = 1000; // 初始延迟1秒 (1000毫秒)

        while (retryCount < maxRetries) {
            try {
                // 设置超时时间 (例如10秒)
                Stock stock = YahooFinance.get(ticker.symbol());

                // 设置时间范围
                Calendar from = Calendar.getInstance();
                from.setTime(java.sql.Date.valueOf(startDate));
                Calendar to = Calendar.getInstance();
                to.setTime(java.sql.Date.valueOf(endDate));

                // 获取历史数据
                List<HistoricalQuote> history = stock.getHistory(from, to, Interval.DAILY);

                // 将雅虎财经的数据格式转换为ta4j的Bar格式
                for (HistoricalQuote quote : history) {
                    if (quote.getDate() == null || quote.getOpen() == null || quote.getHigh() == null || quote.getLow() == null || quote.getClose() == null || quote.getVolume() == null) {
                        // 跳过任何包含空数据的行，增加程序的健壮性
                        continue;
                    }
                    series.addBar(
                            quote.getDate().toInstant().atZone(ZoneId.systemDefault()),
                            quote.getOpen(),
                            quote.getHigh(),
                            quote.getLow(),
                            quote.getClose(),
                            quote.getVolume()
                    );
                }

                // 如果代码能成功运行到这里，说明数据获取成功，我们跳出循环
                System.out.println("成功获取股票数据: " + ticker.symbol());
                return series;

            } catch (Exception e) {
                retryCount++;
                System.err.printf("获取股票数据失败 (第 %d/%d 次尝试): %s\n", retryCount, maxRetries, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        System.err.printf("将在 %.1f 秒后重试...\n", delay / 1000.0);
                        Thread.sleep(delay); // 等待一段时间再重试
                        delay *= 2; // 每次重试后，将等待时间加倍
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // 恢复中断状态
                    }
                }
            }
        }

        // 如果重试3次后仍然失败，打印最终错误并返回空数据
        System.err.println("重试多次后，仍然无法获取股票数据: " + ticker.symbol());
        return series;
    }
}