package jarpsx.backend;

public enum PSXIntegerConstants {
    BIOS_SIZE (1024 * 512),
    RAM_SIZE (1024 * 2048),
    CPU_CLOCK (44100 * 0x300),
    VIDEO_CLOCK (44100 * 0x300 * 11 / 7);

    final private int size_;

    PSXIntegerConstants(int size) {
        size_ = size;
    }

    public int getInt() { return size_; }
}