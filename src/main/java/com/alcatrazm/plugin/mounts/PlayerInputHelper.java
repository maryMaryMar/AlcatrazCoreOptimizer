package com.alcatrazm.plugin.mounts;

import org.bukkit.entity.Player;
import java.lang.reflect.Method;

public class PlayerInputHelper {

    public record InputResult(
            boolean forward,
            boolean backward,
            boolean left,
            boolean right,
            boolean jump,
            boolean shift,
            boolean sprint
    ) {
        public boolean hasMovement() {
            return forward || backward || left || right || jump || shift;
        }
    }

    private static final InputResult EMPTY = new InputResult(false, false, false, false, false, false, false);

    private static boolean initialized = false;
    private static Method getCurrentInputMethod;
    private static Method isForwardMethod;
    private static Method isBackwardMethod;
    private static Method isLeftMethod;
    private static Method isRightMethod;
    private static Method isJumpMethod;
    private static Method isSneakMethod;
    private static Method isSprintMethod;

    private static synchronized void init(Player player) {
        if (initialized) return;
        initialized = true;
        try {
            // Intento 1: Paper 1.21 API nativa Player.getCurrentInput() -> org.bukkit.Input
            for (Method m : player.getClass().getMethods()) {
                if (m.getName().equals("getCurrentInput") && m.getParameterCount() == 0) {
                    getCurrentInputMethod = m;
                    Class<?> inputClass = m.getReturnType();
                    for (Method im : inputClass.getMethods()) {
                        String name = im.getName().toLowerCase();
                        if (name.contains("forward")) isForwardMethod = im;
                        else if (name.contains("backward")) isBackwardMethod = im;
                        else if (name.contains("left")) isLeftMethod = im;
                        else if (name.contains("right")) isRightMethod = im;
                        else if (name.contains("jump")) isJumpMethod = im;
                        else if (name.contains("sneak") || name.contains("shift")) isSneakMethod = im;
                        else if (name.contains("sprint")) isSprintMethod = im;
                    }
                    return;
                }
            }

            // Intento 2: NMS getLastClientInput()
            Method getHandle = player.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(player);
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("lastclientinput") && m.getParameterCount() == 0) {
                    getCurrentInputMethod = m;
                    Class<?> inputClass = m.getReturnType();
                    for (Method im : inputClass.getMethods()) {
                        String name = im.getName().toLowerCase();
                        if (name.contains("forward")) isForwardMethod = im;
                        else if (name.contains("backward")) isBackwardMethod = im;
                        else if (name.contains("left")) isLeftMethod = im;
                        else if (name.contains("right")) isRightMethod = im;
                        else if (name.contains("jump")) isJumpMethod = im;
                        else if (name.contains("shift") || name.contains("sneak")) isSneakMethod = im;
                        else if (name.contains("sprint")) isSprintMethod = im;
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static InputResult getInput(Player player) {
        if (player == null || !player.isOnline()) {
            return EMPTY;
        }
        if (!initialized) {
            init(player);
        }

        if (getCurrentInputMethod != null) {
            try {
                Object target = getCurrentInputMethod.getDeclaringClass().isAssignableFrom(player.getClass())
                        ? player
                        : player.getClass().getMethod("getHandle").invoke(player);
                Object inputObj = getCurrentInputMethod.invoke(target);
                if (inputObj != null) {
                    boolean forward = isForwardMethod != null && (boolean) isForwardMethod.invoke(inputObj);
                    boolean backward = isBackwardMethod != null && (boolean) isBackwardMethod.invoke(inputObj);
                    boolean left = isLeftMethod != null && (boolean) isLeftMethod.invoke(inputObj);
                    boolean right = isRightMethod != null && (boolean) isRightMethod.invoke(inputObj);
                    boolean jump = isJumpMethod != null && (boolean) isJumpMethod.invoke(inputObj);
                    boolean shift = isSneakMethod != null && (boolean) isSneakMethod.invoke(inputObj);
                    boolean sprint = isSprintMethod != null && (boolean) isSprintMethod.invoke(inputObj);

                    return new InputResult(forward, backward, left, right, jump, shift, sprint);
                }
            } catch (Throwable ignored) {}
        }

        return EMPTY;
    }

    public static boolean isAvailable() {
        return true;
    }
}

