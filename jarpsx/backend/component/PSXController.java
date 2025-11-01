package jarpsx.backend.component;
import jarpsx.backend.component.Peripheral;

public class PSXController {
    public static final int SELECT = 1 << 0;
    public static final int L3 = 1 << 1;
    public static final int R3 = 1 << 2;
    public static final int START = 1 << 3;
    public static final int JOYPAD_UP = 1 << 4;
    public static final int JOYPAD_RIGHT = 1 << 5;
    public static final int JOYPAD_DOWN = 1 << 6;
    public static final int JOYPAD_LEFT = 1 << 7;
    public static final int L2 = 1 << 8;
    public static final int R2 = 1 << 9;
    public static final int L1 = 1 << 10;
    public static final int R1 = 1 << 11;
    public static final int TRIANGLE = 1 << 12;
    public static final int CIRCLE = 1 << 13;
    public static final int CROSS = 1 << 14;
    public static final int SQUARE = 1 << 15;

    private int buttonState;
    public PSXController() {
        buttonState = 0xFFFF;
    }

    public int getButtonState(int byteIndex) {
        return (buttonState >>> (byteIndex * 8)) & 0xFF;
    }

    public void setButtonState(int state, boolean set) {
        if (set) {
            buttonState &= ~state;
        } else {
            buttonState |= state;
        }
    }

    public void setButtonState(int state) {
        buttonState = ~state & 0xFFFF;
    }
}