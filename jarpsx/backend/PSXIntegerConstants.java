package jarpsx.backend;

public enum PSXIntegerConstants {
    BIOS_SIZE (1024 * 512),
    RAM_SIZE (1024 * 2048);

    // I/O
    public static final int BIOS_ROM_DELAY_OFFSET = 0x1010;
    public static final int RAM_SIZE_OFFSET = 0x1060;
    public static final int COMMON_DELAY_OFFSET = 0x1020;
    public static final int EXPANSION_1_BASE_ADDRESS_OFFSET = 0x1000;
    public static final int EXPANSION_2_BASE_ADDRESS_OFFSET = 0x1004;
    public static final int EXPANSION_1_DELAY_SIZE_OFFSET = 0x1008;
    public static final int EXPANSION_2_DELAY_SIZE_OFFSET = 0x101C;
    public static final int EXPANSION_3_DELAY_SIZE_OFFSET = 0x100C;
    public static final int SPU_DELAY_OFFSET = 0x1014;
    public static final int CDROM_DELAY_OFFSET = 0x1018;

    public static final int I_STAT_OFFSET = 0x1070;
    public static final int I_MASK_OFFSET = 0x1074;

    final private int size_;

    PSXIntegerConstants(int size) {
        size_ = size;
    }

    public int getInt() { return size_; }
}