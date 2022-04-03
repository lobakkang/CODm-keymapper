package com.lobakkang.keymapper;

import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.os.BatteryManager;
import android.os.Build;

import java.net.*;
import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import android.view.MotionEvent;

class MovementManager extends Thread {
    public AtomicInteger x = new AtomicInteger(202);
    public AtomicInteger y = new AtomicInteger(534);
    public AtomicBoolean d = new AtomicBoolean(true);
    public Size screen_size;
    public Controller controller;

    private int mx = 202;
    private int my = 534;

    public void run() {
        try {
            while (true) {
                TimeUnit.MILLISECONDS.sleep(50);

                while (d.get() == false) {
                    mx += (x.get() < mx) ? -1 : 1;
                    my += (y.get() < my) ? -1 : 1;
                    // logger.i(mx + " " + my);
                    controller.injectTouch(MotionEvent.ACTION_MOVE, 0, new Position(new Point(mx, my), screen_size),
                            1.0f,
                            MotionEvent.BUTTON_PRIMARY);

                    if (x.get() == mx && y.get() == my) {
                        d.set(true);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Exception is caught");
        }
    }
}

public final class Server {
    private Server() {
        // not instantiable
    }

    private static Socket socket = null;
    private static ServerSocket server_conn = null;

    public static void main(String... args) throws Exception {
        logger.i("Initializing program");
        Options options = new Options();
        Device device = new Device(options);
        Controller controller = new Controller(device);
        Size screen_size = device.getScreenInfo().getUnlockedVideoSize();

        int camx = 0;
        int camy = 0;

        Position movement_center_pos = new Position(new Point(202, 534), screen_size);
        Position camera_center_pos = new Position(new Point(1138, 360), screen_size);
        Position left_pos = new Position(new Point(102, 534), screen_size);
        Position right_pos = new Position(new Point(302, 534), screen_size);
        Position backward_pos = new Position(new Point(202, 634), screen_size);
        Position forward_pos = new Position(new Point(202, 400), screen_size);

        Position forward_right_pos = new Position(new Point(302, 434), screen_size);
        Position forward_left_pos = new Position(new Point(102, 434), screen_size);
        Position backward_right_pos = new Position(new Point(302, 634), screen_size);
        Position backward_left_pos = new Position(new Point(102, 634), screen_size);

        Position fire_pos = new Position(new Point(88, 376), screen_size);
        Position scope_pos = new Position(new Point(1444, 410), screen_size);
        Position jump_pos = new Position(new Point(1569, 498), screen_size);
        Position reload_pos = new Position(new Point(1300, 620), screen_size);

        controller.injectTouch(MotionEvent.ACTION_DOWN, 0, movement_center_pos, 1.0f,
                MotionEvent.BUTTON_PRIMARY);
        controller.injectTouch(MotionEvent.ACTION_DOWN, 1, camera_center_pos, 1.0f,
                MotionEvent.BUTTON_PRIMARY);

        /*
         * MovementManager movementManager = new MovementManager();
         * movementManager.screen_size = screen_size;
         * movementManager.controller = controller;
         * movementManager.start();
         */

        server_conn = new ServerSocket(6969);
        socket = server_conn.accept();
        logger.i("Client connected");

        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line = "";

        boolean isMoving = false;
        long lastTime = System.currentTimeMillis();
        String prevMove = "M";
        boolean isActivated = true;

        try {
            while (line != "end" && line != null) {
                line = in.readLine();
                if (line != null && line.length() == 18) {
                    int mouse_x = Integer.parseInt(line.substring(0, 5));
                    int mouse_y = Integer.parseInt(line.substring(5, 10));
                    String movement = line.substring(10, 12);
                    String keycode = line.substring(12, 18);

                    if (isActivated) {
                        if (movement.contains("W")) {
                            if (movement.contains("D")) {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, forward_right_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            } else if (movement.contains("A")) {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, forward_left_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            } else {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, forward_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            }
                            isMoving = true;
                        } else if (movement.contains("S")) {
                            if (movement.contains("D")) {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, backward_right_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            } else if (movement.contains("A")) {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, backward_left_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            } else {
                                controller.injectTouch(MotionEvent.ACTION_MOVE, 0, backward_pos, 1.0f,
                                        MotionEvent.BUTTON_PRIMARY);
                            }
                            isMoving = true;
                        } else if (movement.contains("A")) {
                            controller.injectTouch(MotionEvent.ACTION_MOVE, 0, left_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            isMoving = true;
                        } else if (movement.contains("D")) {
                            controller.injectTouch(MotionEvent.ACTION_MOVE, 0, right_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            isMoving = true;
                        } else if (isMoving) {
                            controller.injectTouch(MotionEvent.ACTION_MOVE, 0, movement_center_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            logger.i("stopped");
                            isMoving = false;
                        }

                        if (keycode.charAt(0) == '1') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 2, jump_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            controller.injectTouch(MotionEvent.ACTION_DOWN, 2, jump_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        } else if (keycode.charAt(0) == '0') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 2, jump_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        }

                        if (keycode.charAt(1) == '1') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 2, reload_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            controller.injectTouch(MotionEvent.ACTION_DOWN, 2, reload_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        } else if (keycode.charAt(1) == '0') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 2, reload_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        }

                        if (keycode.charAt(2) == '1') {
                            controller.injectTouch(MotionEvent.ACTION_DOWN, 3, fire_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        } else if (keycode.charAt(2) == '0') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 3, fire_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        }

                        if (keycode.charAt(3) == '1') {
                            controller.injectTouch(MotionEvent.ACTION_DOWN, 4, scope_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        } else if (keycode.charAt(3) == '0') {
                            controller.injectTouch(MotionEvent.ACTION_UP, 4, scope_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                        }

                        camx -= (50000 - mouse_x);
                        camy -= (50000 - mouse_y);

                        controller.injectTouch(MotionEvent.ACTION_MOVE, 1,
                                new Position(new Point(1138 + camx, 360 + camy), screen_size),
                                1.0f,
                                MotionEvent.BUTTON_PRIMARY);

                        if (mouse_x == 50000 && mouse_y == 50000) {
                            controller.injectTouch(MotionEvent.ACTION_UP, 1,
                                    new Position(new Point(1138 + camx, 360 + camy), screen_size),
                                    1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            controller.injectTouch(MotionEvent.ACTION_DOWN, 1, camera_center_pos, 1.0f,
                                    MotionEvent.BUTTON_PRIMARY);
                            camx = 0;
                            camy = 0;
                        }
                    }

                    if (keycode.charAt(5) == '1') {
                        controller.injectTouch(MotionEvent.ACTION_DOWN, 0, movement_center_pos, 1.0f,
                                MotionEvent.BUTTON_PRIMARY);
                        controller.injectTouch(MotionEvent.ACTION_DOWN, 1, camera_center_pos, 1.0f,
                                MotionEvent.BUTTON_PRIMARY);
                        isActivated = true;
                    } else if (keycode.charAt(5) == '0') {
                        controller.injectTouch(MotionEvent.ACTION_UP, 0, movement_center_pos, 1.0f,
                                MotionEvent.BUTTON_PRIMARY);
                        controller.injectTouch(MotionEvent.ACTION_UP, 1, camera_center_pos, 1.0f,
                                MotionEvent.BUTTON_PRIMARY);
                        isActivated = false;
                    }

                    // System.out.println(mouse_x + " " + mouse_y + " " + movement + " " + keycode);
                } else {
                    logger.w("undefined socket stream detected");
                }
            }
            logger.i("Closing connection");
        } catch (IOException e) {
            logger.i(e.toString());
        }

        socket.close();
        in.close();

        /*
         * return;
         * 
         * Size scr_s = device.getScreenInfo().getUnlockedVideoSize();
         * Point ptn = new Point(100, 100);
         * Position pos = new Position(ptn, scr_s);
         * 
         * controller.injectTouch(MotionEvent.ACTION_DOWN, 0, pos, 1.0f,
         * MotionEvent.BUTTON_PRIMARY);
         * TimeUnit.SECONDS.sleep(1);
         * pos.setPoint(new Point(300, 300));
         * controller.injectTouch(MotionEvent.ACTION_UP, 0, pos, 1.0f,
         * MotionEvent.BUTTON_PRIMARY);
         * logger.i("Bye");
         */
    }
}

// export CLASSPATH=/storage/emulated/0/Download/phone
// app_process /system/bin com.lobakkang.keymapper.Server