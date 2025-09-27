package jarpsx.backend;

import jarpsx.backend.PSXIntegerConstants;
import jarpsx.backend.mips.Disassembler;

/*
 * Memory is implemented as software fastmem
 */
public class Memory {
    private interface SoftwarePageExecutor {
        public int getAddressMask();
        public byte readByte(int offset);
        public short readShort(int offset);
        public int readInt(int offset);
        public void writeByte(int offset, byte value);
        public void writeShort(int offset, short value);
        public void writeInt(int offset, int value);
    }

    public class Utility {
        static void setMemory(byte[] data, int c, int size, int offset) {
            for (int i = 0; i < size; i++) {
                data[i + offset] = (byte) c;
            }
        }

        static void copyMemory(byte[] dst, int dstOffset, byte[] src, int srcOffset, int size) {
            for (int i = 0; i < size; i++) {
                dst[i + dstOffset] = src[i + srcOffset];
            }
        }
    }

    private class RAMDirectAccess implements SoftwarePageExecutor {
        public byte[] ram;

        RAMDirectAccess() {
            ram = new byte[PSXIntegerConstants.RAM_SIZE.getInt()];
        }

        public int getAddressMask() {
            return 0x1FFFFF;
        }

        public byte readByte(int offset) {
            return ram[offset];
        }

        public short readShort(int offset) {
            int result;
            result = ((int)readByte(offset) & 0xFF) | (int)readByte(offset + 1) << 8;
            return (short) result;
        }

        public int readInt(int offset) {
            int result = ((int)readShort(offset) & 0xFFFF) | (int)readShort(offset + 2) << 16;
            return result;
        }

        public void writeByte(int offset, byte value) {
            ram[offset] = value;
        }

        public void writeShort(int offset, short value) {
            writeByte(offset, (byte)(value & 0xFF));
            writeByte(offset + 1, (byte)(value >>> 8));
        }

        public void writeInt(int offset, int value) {
            writeShort(offset, (short)(value & 0xFFFF));
            writeShort(offset + 2, (short)((value >>> 16) & 0xFFFF));
        }
    }
    
    private class BIOSAccess implements SoftwarePageExecutor {
        public byte[] bios;

        BIOSAccess() {
            bios = new byte[PSXIntegerConstants.BIOS_SIZE.getInt()];
        }

        public int getAddressMask() {
            return 0x7FFFF;
        }

        public byte readByte(int offset) {
            return bios[offset];
        }

        public short readShort(int offset) {
            int result;
            result = ((int)readByte(offset) & 0xFF) | (int)readByte(offset + 1) << 8;
            return (short) result;
        }

        public int readInt(int offset) {
            int result = ((int)readShort(offset) & 0xFFFF) | (int)readShort(offset + 2) << 16;
            return result;
        }

        public void writeByte(int offset, byte value) {
            throw new RuntimeException(String.format("Invalid writeByte BIOS offset 0x%05X=%02X", offset, value & 0xFF));
        }

        public void writeShort(int offset, short value) {
            throw new RuntimeException(String.format("Invalid writeShort BIOS offset 0x%05X=%04X", offset, value & 0xFFFF));
        }

        public void writeInt(int offset, int value) {
            throw new RuntimeException(String.format("Invalid writeInt BIOS offset 0x%05X=%08X", offset, value));
        }
    }
    
    private class MMIOAccessPage implements SoftwarePageExecutor {
        private Emulator emulator;
        private byte[] scratchpad;
        
        MMIOAccessPage() {
            scratchpad = new byte[1024];
        }

        public void setEmulator(Emulator emulator) {
            this.emulator = emulator;
        }

        public int getAddressMask() {
            return 0xFFFF;
        }

        public byte readByte(int offset) {
            if (offset >= 0 && offset < 0x400) {
                return scratchpad[offset];
            }

            if (offset >= 0x1040 && offset <= 0x105F) {
                return 0;
            }

            throw new RuntimeException(String.format("Unimplemented readByte I/O offset 0x%04X", offset));
        }

        private static int s = 0;

        public short readShort(int offset) {
            if (offset >= 0 && offset < 0x400) {
                int result;
                result = ((int)readByte(offset) & 0xFF) | (int)readByte(offset + 1) << 8;
                return (short) result;
            }

            if (offset >= 0x1040 && offset <= 0x105F) {
                return 0;
            }

            switch (offset) {
            case 0x1100:
            case 0x1104:
            case 0x1108:
            case 0x1110:
            case 0x1118:
            case 0x1114:
            case 0x1120:
            case 0x1124:
            case 0x1128:
            case 0x1070:
                return 0;
            case 0x1074:
                return 0;
            }

            if (offset >= 0x1C00 && offset < 0x2000) // SPU
                return 0;

            throw new RuntimeException(String.format("Unimplemented readShort I/O offset 0x%04X", offset));
        }

        public int readInt(int offset) {
            if (offset >= 0 && offset < 0x400) {
                int result = ((int)readShort(offset) & 0xFFFF) | (int)readShort(offset + 2) << 16;
                return result;
            }

            switch (offset) {
            case 0x1810:
            case 0x1110:
            case 0x10A8:
            case 0x10E8:
            case 0x10F0:
            case 0x10F4:
                return 0;
            case PSXIntegerConstants.I_STAT_OFFSET:
            case PSXIntegerConstants.I_MASK_OFFSET:
                return 0;
            case 0x1814:
                return 0x1c000000;
            }

            throw new RuntimeException(String.format("Unimplemented readInt I/O offset 0x%04X", offset));
        }
        
        public void writeByte(int offset, byte value) {
            if (offset >= 0 && offset < 0x400) {
                scratchpad[offset] = value;
                return;
            }

            switch (offset) {
            case 0x2041:
                return;
            }
            throw new RuntimeException(String.format("Unimplemented writeByte I/O offset 0x%04X=%02X", offset, value & 0xFF));
        }

        public void writeShort(int offset, short value) {
            if (offset >= 0 && offset < 0x400) {
                writeByte(offset, (byte)(value & 0xFF));
                writeByte(offset + 1, (byte)(value >>> 8));
                return;
            }

            if (offset >= 0x1040 && offset <= 0x105F) {
                return;
            }

            switch (offset) {
            case 0x1070:
            case 0x1074:
                return;
            }

            if (offset >= 0x1100 && offset <= 0x112F) { // Timer
                return;
            }

            if (offset >= 0x1C00 && offset < 0x2000) // SPU
                return;
            throw new RuntimeException(String.format("Unimplemented writeShort I/O offset 0x%04X=%04X", offset, value & 0xFFFF));
        }

        public void writeInt(int offset, int value) {
            if (offset >= 0 && offset < 0x400) {
                writeShort(offset, (short)(value & 0xFFFF));
                writeShort(offset + 2, (short)((value >>> 16) & 0xFFFF));
                return;
            }

            switch (offset) {
            case 0x10E0:
            case 0x10E4:
            case 0x10E8:
            case 0x10A8:
            case 0x1814:
            case 0x1810:
            case 0x10F0:
            case 0x1118:
            case 0x1114:
            case 0x10F4:
            case 0x10A0:
            case 0x10A4:
                return;
            case PSXIntegerConstants.EXPANSION_3_DELAY_SIZE_OFFSET:
            case PSXIntegerConstants.CDROM_DELAY_OFFSET:
            case PSXIntegerConstants.SPU_DELAY_OFFSET:
            case PSXIntegerConstants.EXPANSION_1_DELAY_SIZE_OFFSET:
            case PSXIntegerConstants.EXPANSION_2_DELAY_SIZE_OFFSET:
            case PSXIntegerConstants.EXPANSION_1_BASE_ADDRESS_OFFSET:
            case PSXIntegerConstants.EXPANSION_2_BASE_ADDRESS_OFFSET:
            case PSXIntegerConstants.COMMON_DELAY_OFFSET:
            case PSXIntegerConstants.RAM_SIZE_OFFSET:
            case PSXIntegerConstants.BIOS_ROM_DELAY_OFFSET:
                return;

            case PSXIntegerConstants.I_STAT_OFFSET:
            case PSXIntegerConstants.I_MASK_OFFSET:
                // System.out.println("Interrupt write " + value);
                return;
            }
            throw new RuntimeException(String.format("Unimplemented writeInt I/O offset 0x%04X=%08X", offset, value));
        }
    }
    
    private class CacheControl implements SoftwarePageExecutor {
        CacheControl() {}

        public int getAddressMask() {
            return 0xFFFF;
        }

        public byte readByte(int offset) {
            throw new RuntimeException(String.format("Invalid readByte CACHE CONTROL offset 0x%04X", offset));
        }

        public short readShort(int offset) {
            throw new RuntimeException(String.format("Invalid readShort CACHE CONTROL offset 0x%04X", offset));
        }

        public int readInt(int offset) {
            throw new RuntimeException(String.format("Invalid readInt CACHE CONTROL offset 0x%04X", offset));
        }

        public void writeByte(int offset, byte value) {
            throw new RuntimeException(String.format("Invalid writeByte CACHE CONTROL offset 0x%05X=%02X", offset, value & 0xFF));
        }

        public void writeShort(int offset, short value) {
            throw new RuntimeException(String.format("Invalid writeShort CACHE CONTROL offset 0x%05X=%04X", offset, value & 0xFFFF));
        }

        public void writeInt(int offset, int value) {
            switch (offset) {
            case 0x0130:
                return;
            }
            throw new RuntimeException(String.format("Invalid writeInt CACHE CONTROL offset 0x%05X=%08X", offset, value));
        }
    }
    
    private class ExpansionRegion1 implements SoftwarePageExecutor {
        ExpansionRegion1() {}

        public int getAddressMask() {
            return 0x7FFFFF;
        }

        public byte readByte(int offset) {
            return 0;
        }

        public short readShort(int offset) {
            throw new RuntimeException(String.format("Invalid readShort EXPANSION REGION 1 offset 0x%04X", offset));
        }

        public int readInt(int offset) {
            throw new RuntimeException(String.format("Invalid readInt EXPANSION REGION 1 offset 0x%04X", offset));
        }

        public void writeByte(int offset, byte value) {
            throw new RuntimeException(String.format("Invalid writeByte EXPANSION REGION 1 offset 0x%05X=%02X", offset, value & 0xFF));
        }

        public void writeShort(int offset, short value) {
            throw new RuntimeException(String.format("Invalid writeShort EXPANSION REGION 1 offset 0x%05X=%04X", offset, value & 0xFFFF));
        }

        public void writeInt(int offset, int value) {
            throw new RuntimeException(String.format("Invalid writeInt EXPANSION REGION 1 offset 0x%05X=%08X", offset, value));
        }
    }

    private SoftwarePageExecutor[] pageExecutor;
    private RAMDirectAccess ramDirectAccess;
    private MMIOAccessPage mmioAccess;
    private BIOSAccess biosAccess;
    private CacheControl cacheControl;
    private ExpansionRegion1 expansionRegion1;
    private Emulator emulator;

    Memory(Emulator emu) {
        pageExecutor = new SoftwarePageExecutor[0x10000];
        ramDirectAccess = new RAMDirectAccess();
        mmioAccess = new MMIOAccessPage();
        biosAccess = new BIOSAccess();
        cacheControl = new CacheControl();
        expansionRegion1 = new ExpansionRegion1();

        // RAM
        for (int i = 0; i < 0x20; i++) {
            pageExecutor[i] = ramDirectAccess;
            pageExecutor[i + 0x8000] = ramDirectAccess;
            pageExecutor[i + 0xA000] = ramDirectAccess;
        }

        for (int i = 0; i < 0x80; i++) {
            pageExecutor[i + 0x1F00] = expansionRegion1;
            pageExecutor[i + 0x9F00] = expansionRegion1;
            pageExecutor[i + 0xBF00] = expansionRegion1;
        }

        // Scratchpad, I/O
        pageExecutor[0x1F80] = mmioAccess;
        pageExecutor[0x9F80] = mmioAccess;
        pageExecutor[0xBF80] = mmioAccess;
        pageExecutor[0xFFFE] = cacheControl;
        // BIOS
        for (int i = 0; i < 8; i++) {
            pageExecutor[i + 0x1FC0] = biosAccess;
            pageExecutor[i + 0x9FC0] = biosAccess;
            pageExecutor[i + 0xBFC0] = biosAccess;
        }

        emulator = emu;
        mmioAccess.setEmulator(emu);
    }

    public byte readByte(int address) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null)
            throw new RuntimeException(String.format("Unknown readByte address %08X", address));

        return exec.readByte(address & exec.getAddressMask());
    }

    public short readShort(int address) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null)
            throw new RuntimeException(String.format("Unknown readShort address %08X", address));

        return exec.readShort(address & exec.getAddressMask());
    }

    public int readInt(int address) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null)
            throw new RuntimeException(String.format("Unknown readInt address %08X", address));

        return exec.readInt(address & exec.getAddressMask());
    }

    public void writeByte(int address, byte value) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null)
            throw new RuntimeException(String.format("Unknown writeByte address %08X", address));

        exec.writeByte(address & exec.getAddressMask(), value);
    }

    public void writeShort(int address, short value) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null)
            throw new RuntimeException(String.format("Unknown writeShort address %08X", address));

        exec.writeShort(address & exec.getAddressMask(), value);
    }

    public void writeInt(int address, int value) {
        SoftwarePageExecutor exec = pageExecutor[address >>> 16];

        if (exec == null) {
            throw new RuntimeException(String.format("Unknown writeInt address %08X value %08X PC %08X", address, value, emulator.getCpu().PC));
        }
        exec.writeInt(address & exec.getAddressMask(), value);
    }

    public byte[] getBiosData() {
        return biosAccess.bios;
    }
    
    public byte[] getRamData() {
        return ramDirectAccess.ram;
    }
}