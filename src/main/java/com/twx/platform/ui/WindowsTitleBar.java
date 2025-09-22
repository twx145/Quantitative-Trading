package com.twx.platform.ui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;

public class WindowsTitleBar {

    // 定义 DWMWINDOWATTRIBUTE 常量，用于设置暗黑模式
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    // 加载 dwmapi.dll 库
    private interface DwmApi extends User32 {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        // 定义 DwmSetWindowAttribute 函数原型
        HRESULT DwmSetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    /**
     * 为指定的JavaFX Stage启用或禁用Windows原生暗黑模式标题栏
     * @param stage javafx.stage.Stage 窗口
     * @param isDark 布尔值, true为暗黑模式, false为明亮模式
     */
    public static void setDarkMode(javafx.stage.Stage stage, boolean isDark) {
        // 仅在Windows上运行
        if (!System.getProperty("os.name").startsWith("Windows")) {
            return;
        }

        try {
            // 关键步骤：获取窗口的本地句柄 (HWND)
            // 这是一个脆弱的方法，依赖于窗口标题。确保标题是唯一的。
            HWND hwnd = User32.INSTANCE.FindWindow(null, stage.getTitle());
            if (hwnd == null) {
                System.err.println("无法找到窗口句柄，请确保stage.show()已被调用且标题唯一。");
                return;
            }

            // 1 表示暗黑模式, 0 表示明亮模式
            int darkMode = isDark ? 1 : 0;
            IntByReference attrValue = new IntByReference(darkMode);

            // 调用原生API
            HRESULT result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    hwnd,
                    DWMWA_USE_IMMERSIVE_DARK_MODE,
                    attrValue,
                    Integer.BYTES
            );

            if (result.intValue() != 0) {
                System.err.println("DwmSetWindowAttribute 调用失败，错误代码: " + result.intValue());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}