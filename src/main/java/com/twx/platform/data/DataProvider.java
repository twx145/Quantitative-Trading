package com.twx.platform.data;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;

/**
 * 数据提供者接口，定义了获取金融市场数据的标准。
 * 任何数据源（如在线API、数据库、CSV文件）都应实现此接口。
 */
public interface DataProvider {

    /**
     * 获取指定时间范围内的历史K线数据。
     *
     * @param ticker    交易标的，如股票代码
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param timeFrame K线的时间周期（日线、小时线等）
     * @return 一个包含历史数据的 BarSeries 对象
     */
    BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame);
}