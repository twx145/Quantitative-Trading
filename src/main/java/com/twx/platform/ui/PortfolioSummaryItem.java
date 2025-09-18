package com.twx.platform.ui;

import javafx.beans.property.SimpleStringProperty;

/**
 * 用于TableView显示回测总结的数据模型。
 */
public class PortfolioSummaryItem {
    private final SimpleStringProperty item;
    private final SimpleStringProperty value;

    public PortfolioSummaryItem(String item, String value) {
        this.item = new SimpleStringProperty(item);
        this.value = new SimpleStringProperty(value);
    }

    public String getItem() { return item.get(); }
    public String getValue() { return value.get(); }
    public SimpleStringProperty itemProperty() { return item; }
    public SimpleStringProperty valueProperty() { return value; }
}