package com.twx.platform.data;

import com.twx.platform.common.Ticker;
import com.twx.platform.common.TimeFrame;
import org.ta4j.core.BarSeries;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据提供者接口，定义了获取金融数据和信息的标准。
 */
public interface DataProvider {

    /**
     * 【新增】定义市场类型的枚举
     */
    enum MarketType {
        ALL("所有市场"),
        A_SHARE("A股"),
        HK_STOCK("港股"),
        US_STOCK("美股"),
        FUND("基金");

        private final String displayName;

        MarketType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * 用于封装股票搜索建议的数据类 (Record)。
     */
    record StockSuggestion(String ticker, String name) {
        @Override
        public String toString() {
            return String.format("%s (%s)", name, ticker);
        }
    }

    BarSeries getHistoricalData(Ticker ticker, LocalDate startDate, LocalDate endDate, TimeFrame timeFrame);

    String getCompanyName(Ticker ticker) throws IOException;

    /**
     * 【修改】根据关键词和市场类型搜索股票，返回建议列表。
     * @param keyword    搜索关键词 (e.g., "茅台", "mt", "600519")
     * @param marketType 市场类型筛选
     * @return 包含股票建议的列表
     */
    List<StockSuggestion> searchStocks(String keyword, MarketType marketType) throws IOException, InterruptedException;
}