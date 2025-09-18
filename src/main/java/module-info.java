module com.twx.quantitative.trading {
    requires javafx.controls;
    requires javafx.fxml;
    requires ta4j.core;
    requires org.slf4j;
    requires java.net.http;
    opens com.twx.platform.ui to javafx.fxml;

    exports com.twx.platform.common;
    exports com.twx.platform.data;
    exports com.twx.platform.data.impl;
    exports com.twx.platform.engine;
    exports com.twx.platform.portfolio;
    exports com.twx.platform.portfolio.impl;
    exports com.twx.platform.position;
    exports com.twx.platform.position.impl;
    exports com.twx.platform.strategy;
    exports com.twx.platform.strategy.impl;
    exports com.twx.platform.ui;
}