package org.lwjgl.glfw;

import java.nio.charset.StandardCharsets;

public class CallbackBridge {
    public static final int CLIPBOARD_COPY = 2000;
    public static final int CLIPBOARD_PASTE = 2001;

    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CHAR_MODS = 1001;
    public static final int EVENT_TYPE_CURSOR_ENTER = 1002;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_FRAMEBUFFER_SIZE = 1004;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;
    public static final int EVENT_TYPE_SCROLL = 1007;
    public static final int EVENT_TYPE_WINDOW_SIZE = 1008;

    public static final int ANDROID_TYPE_GRAB_STATE = 0;

    public static final boolean INPUT_DEBUG_ENABLED =
            Boolean.parseBoolean(System.getProperty("glfwstub.debugInput", "false"));

    private static volatile boolean sInputReady = false;
    private static volatile boolean sGrabbing = false;
    private static volatile int sCursorShape = 0;
    private static volatile String sClipboard = "";

    public static void sendData(int type, String data) {
        nativeSendData(false, type, data);
    }

    public static void nativeSendData(boolean isAndroid, int type, String data) {
    }

    public static boolean nativeSetInputReady(boolean ready) {
        sInputReady = ready;
        return true;
    }

    public static String nativeClipboard(int action, byte[] copy) {
        if (action == CLIPBOARD_COPY && copy != null) {
            sClipboard = new String(copy, StandardCharsets.UTF_8);
            return sClipboard;
        }
        return sClipboard;
    }

    public static void nativeSetGrabbing(boolean grab) {
        sGrabbing = grab;
    }

    public static void nativeSetCursorShape(int shape) {
        sCursorShape = shape;
    }

    public static boolean isGrabbing() {
        return sGrabbing;
    }

    public static int getCursorShape() {
        return sCursorShape;
    }
}
