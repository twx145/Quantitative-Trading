package com.twx.platform.common;

public class Ticker {
    private final String symbol;

    public Ticker(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return symbol;
    }
}