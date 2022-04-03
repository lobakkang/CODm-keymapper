package com.lobakkang.keymapper;

import com.lobakkang.keymapper.logger;

import com.lobakkang.keymapper.wrappers.InputManager;
import com.lobakkang.keymapper.wrappers.ServiceManager;
import com.lobakkang.keymapper.wrappers.SurfaceControl;

import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Device {

    public static final int POWER_MODE_OFF = SurfaceControl.POWER_MODE_OFF;
    public static final int POWER_MODE_NORMAL = SurfaceControl.POWER_MODE_NORMAL;

    public static final int INJECT_MODE_ASYNC = InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;
    public static final int INJECT_MODE_WAIT_FOR_RESULT = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT;
    public static final int INJECT_MODE_WAIT_FOR_FINISH = InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH;

    public static final int LOCK_VIDEO_ORIENTATION_UNLOCKED = -1;
    public static final int LOCK_VIDEO_ORIENTATION_INITIAL = -2;

    private static final ServiceManager SERVICE_MANAGER = new ServiceManager();
    private static final Settings SETTINGS = new Settings(SERVICE_MANAGER);

    public interface ClipboardListener {
        void onClipboardTextChanged(String text);
    }

    private final Size deviceSize;
    private final Rect crop;
    private int maxSize;
    private final int lockVideoOrientation;

    private ScreenInfo screenInfo;
    private final AtomicBoolean isSettingClipboard = new AtomicBoolean();

    /**
     * Logical display identifier
     */
    private final int displayId;

    /**
     * The surface flinger layer stack associated with this logical display
     */
    private final int layerStack;

    private final boolean supportsInputEvents;

    public Device(Options options) {
        displayId = options.getDisplayId();
        DisplayInfo displayInfo = SERVICE_MANAGER.getDisplayManager().getDisplayInfo(displayId);
        if (displayInfo == null) {
            int[] displayIds = SERVICE_MANAGER.getDisplayManager().getDisplayIds();
            throw new InvalidDisplayIdException(displayId, displayIds);
        }

        int displayInfoFlags = displayInfo.getFlags();

        deviceSize = displayInfo.getSize();
        crop = options.getCrop();
        maxSize = options.getMaxSize();
        lockVideoOrientation = options.getLockVideoOrientation();

        screenInfo = ScreenInfo.computeScreenInfo(displayInfo.getRotation(), deviceSize, crop, maxSize, lockVideoOrientation);
        layerStack = displayInfo.getLayerStack();


        if ((displayInfoFlags & DisplayInfo.FLAG_SUPPORTS_PROTECTED_BUFFERS) == 0) {
            logger.w("Display doesn't have FLAG_SUPPORTS_PROTECTED_BUFFERS flag, mirroring can be restricted");
        }

        // main display or any display on Android >= Q
        supportsInputEvents = displayId == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        if (!supportsInputEvents) {
            logger.w("Input events are not supported for secondary displays before Android 10");
        }
    }

    public synchronized void setMaxSize(int newMaxSize) {
        maxSize = newMaxSize;
        screenInfo = ScreenInfo.computeScreenInfo(screenInfo.getReverseVideoRotation(), deviceSize, crop, newMaxSize, lockVideoOrientation);
    }

    public synchronized ScreenInfo getScreenInfo() {
        return screenInfo;
    }

    public int getLayerStack() {
        return layerStack;
    }

    public Point getPhysicalPoint(Position position) {
        // it hides the field on purpose, to read it with a lock
        @SuppressWarnings("checkstyle:HiddenField")
        ScreenInfo screenInfo = getScreenInfo(); // read with synchronization

        // ignore the locked video orientation, the events will apply in coordinates considered in the physical device orientation
        Size unlockedVideoSize = screenInfo.getUnlockedVideoSize();

        int reverseVideoRotation = screenInfo.getReverseVideoRotation();
        // reverse the video rotation to apply the events
        Position devicePosition = position.rotate(reverseVideoRotation);

        Size clientVideoSize = devicePosition.getScreenSize();
        if (!unlockedVideoSize.equals(clientVideoSize)) {
            // The client sends a click relative to a video with wrong dimensions,
            // the device may have been rotated since the event was generated, so ignore the event
            return null;
        }
        Rect contentRect = screenInfo.getContentRect();
        Point point = devicePosition.getPoint();
        int convertedX = contentRect.left + point.getX() * contentRect.width() / unlockedVideoSize.getWidth();
        int convertedY = contentRect.top + point.getY() * contentRect.height() / unlockedVideoSize.getHeight();
        return new Point(convertedX, convertedY);
    }

    public static String getDeviceName() {
        return Build.MODEL;
    }

    public static boolean supportsInputEvents(int displayId) {
        return displayId == 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public boolean supportsInputEvents() {
        return supportsInputEvents;
    }

    public static boolean injectEvent(InputEvent inputEvent, int displayId, int injectMode) {
        if (!supportsInputEvents(displayId)) {
            throw new AssertionError("Could not inject input event if !supportsInputEvents()");
        }

        if (displayId != 0 && !InputManager.setDisplayId(inputEvent, displayId)) {
            return false;
        }

        return SERVICE_MANAGER.getInputManager().injectInputEvent(inputEvent, injectMode);
    }

    public boolean injectEvent(InputEvent event, int injectMode) {
        return injectEvent(event, displayId, injectMode);
    }

    public static boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, int displayId, int injectMode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, action, keyCode, repeat, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        return injectEvent(event, displayId, injectMode);
    }

    public boolean injectKeyEvent(int action, int keyCode, int repeat, int metaState, int injectMode) {
        return injectKeyEvent(action, keyCode, repeat, metaState, displayId, injectMode);
    }

    public static boolean pressReleaseKeycode(int keyCode, int displayId, int injectMode) {
        return injectKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0, 0, displayId, injectMode)
                && injectKeyEvent(KeyEvent.ACTION_UP, keyCode, 0, 0, displayId, injectMode);
    }

    public boolean pressReleaseKeycode(int keyCode, int injectMode) {
        return pressReleaseKeycode(keyCode, displayId, injectMode);
    }

    public static boolean isScreenOn() {
        return SERVICE_MANAGER.getPowerManager().isScreenOn();
    }

    public static void expandNotificationPanel() {
        SERVICE_MANAGER.getStatusBarManager().expandNotificationsPanel();
    }

    public static void expandSettingsPanel() {
        SERVICE_MANAGER.getStatusBarManager().expandSettingsPanel();
    }

    public static void collapsePanels() {
        SERVICE_MANAGER.getStatusBarManager().collapsePanels();
    }

   
    /**
     * @param mode one of the {@code POWER_MODE_*} constants
     */
    public static boolean setScreenPowerMode(int mode) {
        IBinder d = SurfaceControl.getBuiltInDisplay();
        if (d == null) {
            logger.e("Could not get built-in display");
            return false;
        }
        return SurfaceControl.setDisplayPowerMode(d, mode);
    }

    public static boolean powerOffScreen(int displayId) {
        if (!isScreenOn()) {
            return true;
        }
        return pressReleaseKeycode(KeyEvent.KEYCODE_POWER, displayId, Device.INJECT_MODE_ASYNC);
    }

    

    public static Settings getSettings() {
        return SETTINGS;
    }
}
