package com.twx.platform.common;

import java.util.prefs.Preferences;

/**
 * 应用程序配置管理器.
 * 使用 Java Preferences API 在不同操作系统上安全地存储用户设置.
 */
public class ConfigurationManager {

    // 使用单例模式确保全局只有一个实例
    private static final ConfigurationManager INSTANCE = new ConfigurationManager();

    private final Preferences prefs;

    // 定义配置项的键名，方便复用
    private static final String KIMI_API_KEY = "KIMI_API_KEY";
    // >>> 新增配置项键名 <<<
    private static final String SEARCH_API_KEY = "SEARCH_API_KEY";
    private static final String SEARCH_API_URL = "SEARCH_API_URL";

    // 默认的搜索引擎 API URL (以 Brave Search API 为例)
    private static final String DEFAULT_SEARCH_API_URL = "https://api.search.brave.com/res/v1/web/search";

    private ConfigurationManager() {
        // userNodeForPackage 会为这个类所在的包创建一个独立的、属于当前用户的配置存储节点
        this.prefs = Preferences.userNodeForPackage(ConfigurationManager.class);
    }

    /**
     * 获取全局唯一的 ConfigurationManager 实例.
     * @return ConfigurationManager 实例
     */
    public static ConfigurationManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取存储的 Kimi API Key.
     * @return 如果已设置则返回 Key，否则返回 null.
     */
    public String getKimiApiKey() {
        return prefs.get(KIMI_API_KEY, null);
    }

    /**
     * 保存 Kimi API Key.
     * @param apiKey 要保存的 API Key
     */
    public void setKimiApiKey(String apiKey) {
        if (apiKey != null) {
            prefs.put(KIMI_API_KEY, apiKey);
        } else {
            prefs.remove(KIMI_API_KEY);
        }
    }

    // >>> 新增网络搜索相关的 Getter 和 Setter <<<

    /**
     * 获取存储的搜索引擎 API Key.
     * @return 如果已设置则返回 Key，否则返回 null.
     */
    public String getSearchApiKey() {
        return prefs.get(SEARCH_API_KEY, null);
    }

    /**
     * 保存搜索引擎 API Key.
     * @param apiKey 要保存的 API Key
     */
    public void setSearchApiKey(String apiKey) {
        if (apiKey != null) {
            prefs.put(SEARCH_API_KEY, apiKey);
        } else {
            prefs.remove(SEARCH_API_KEY);
        }
    }

    /**
     * 获取存储的搜索引擎 API URL.
     * @return 如果已设置则返回 URL，否则返回默认的 Brave Search API URL.
     */
    public String getSearchApiUrl() {
        return prefs.get(SEARCH_API_URL, DEFAULT_SEARCH_API_URL);
    }

    /**
     * 保存搜索引擎 API URL.
     * @param apiUrl 要保存的 API URL
     */
    public void setSearchApiUrl(String apiUrl) {
        if (apiUrl != null && !apiUrl.trim().isEmpty()) {
            prefs.put(SEARCH_API_URL, apiUrl.trim());
        } else {
            // 如果传入空，则恢复默认
            prefs.remove(SEARCH_API_URL);
        }
    }
}