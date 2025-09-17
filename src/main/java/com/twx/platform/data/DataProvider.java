package com.twx.platform.data;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import org.ta4j.core.BarSeries;
import java.time.LocalDate;

// 数据提供者接口，定义了获取数据的标准
public interface DataProvider {
    BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame);
}