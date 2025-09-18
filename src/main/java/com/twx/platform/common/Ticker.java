package com.twx.platform.common;

public record Ticker(String symbol) {

    @Override
    public String toString() {
        return symbol;
    }
}