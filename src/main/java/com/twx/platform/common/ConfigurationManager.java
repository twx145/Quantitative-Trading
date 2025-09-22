// src/main/java/com/twx/platform/common/ConfigurationManager.java

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
     * @return 如果已设置则返回 Key，否则返回 null 或空字符串.
     */
    public String getKimiApiKey() {
        // 第二个参数是默认值，如果找不到 KIMI_API_KEY，就返回 null
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
            // 如果传入 null，则移除该键
            prefs.remove(KIMI_API_KEY);
        }
    }
}