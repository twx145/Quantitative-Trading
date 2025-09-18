package com.twx.platform.ui;

import com.twx.platform.common.Order;
import com.twx.platform.common.TradeSignal;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.format.DateTimeFormatter;

/**
 * 用于交易记录TableView的数据模型。
 * 每一行代表一笔已执行的交易。
 */
public class TradeLogItem {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SimpleStringProperty date;
    private final SimpleStringProperty direction;
    private final SimpleDoubleProperty quantity;
    private final SimpleDoubleProperty price;
    private final SimpleDoubleProperty commission;

    public TradeLogItem(Order order) {
        this.date = new SimpleStringProperty(order.timestamp().toLocalDate().format(DATE_FORMATTER));
        this.direction = new SimpleStringProperty(order.signal() == TradeSignal.BUY ? "买入" : "卖出");
        this.quantity = new SimpleDoubleProperty(order.quantity());
        this.price = new SimpleDoubleProperty(order.price());
        // 计算手续费
        double commissionValue = order.quantity() * order.price() * 0.0003; // 假设手续费率为万分之三
        this.commission = new SimpleDoubleProperty(commissionValue);
    }

    // --- JavaFX Property Getter 方法 ---
    // TableView 通过这些方法来获取单元格的数据
    public String getDate() { return date.get(); }
    public String getDirection() { return direction.get(); }
    public double getQuantity() { return quantity.get(); }
    public double getPrice() { return price.get(); }
    public double getCommission() { return commission.get(); }

    // --- JavaFX Property Accessor 方法 ---
    // 这些方法用于数据绑定
    public SimpleStringProperty dateProperty() { return date; }
    public SimpleStringProperty directionProperty() { return direction; }
    public SimpleDoubleProperty quantityProperty() { return quantity; }
    public SimpleDoubleProperty priceProperty() { return price; }
    public SimpleDoubleProperty commissionProperty() { return commission; }
}