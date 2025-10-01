package com.twx.platform.analysis;

import org.jfree.data.xy.XYDataset; // 导入JFreeChart的数据集
import org.ta4j.core.BarSeries;
import java.util.List;

public interface AnalysisTechnique {
    /**
     * 注意：返回类型已更改为 JFreeChart 的 XYDataset 列表
     */
    List<XYDataset> calculate(BarSeries series);

    String getName();
}