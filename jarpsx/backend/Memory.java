package jarpsx.backend;

import java.io.RandomAccessFile;

import jarpsx.backend.Emulator;
import jarpsx.backend.PSXIntegerConstants;
import jarpsx.backend.mips.Disassembler;
import jarpsx.backend.component.*;

/*
 * Memory is implemented as software fastmem
 */
public class Memory {
    // I/O
    // Memory control
    public static final int EXPANSION_1_BASE_ADDRESS_OFFSET = 0x1000;
    public static final int EXPANSION_2_BASE_ADDRESS_OFFSET = 0x1004;
    public static final int EXPANSION_1_DELAY_OFFSET = 0x1008;
    public static final int EXPANSION_3_DELAY_OFFSET = 0x100C;
    public static final int EXPANSION_2_DELAY_OFFSET = 0x101C;
    public static final int BIOS_ROM_DELAY_OFFSET = 0x1010;
    public static final int SPU_DELAY_OFFSET = 0x1014;
    public static final int CDROM_DELAY_OFFSET = 0x1018;
    public static final int COMMON_DELAY_OFFSET = 0x1020;
    public static final int RAM_SIZE_OFFSET = 0x1060;

    // Peripheral
    public static final int JOY_DATA_OFFSET = 0x1040;
    public static final int JOY_STAT_OFFSET = 0x1044;
    public static final int JOY_MODE_OFFSET = 0x1048;
    public static final int JOY_CTRL_OFFSET = 0x104A;
    public static final int JOY_BAUD_OFFSET = 0x104E;
    public static final int SIO_DATA_OFFSET = 0x1050;
    public static final int SIO_STAT_OFFSET = 0x1054;
    public static final int SIO_MODE_OFFSET = 0x1058;
    public static final int SIO_CTRL_OFFSET = 0x105A;
    public static final int SIO_MISC_OFFSET = 0x105C;
    public static final int SIO_BAUD_OFFSET = 0x105E;

    // Interrupt control
    public static final int I_STAT_OFFSET = 0x1070;
    public static final int I_MASK_OFFSET = 0x1074;

    // DMA & Timer handled by memory read/write
    public static final int DPCR_OFFSET = 0x10F0;
    public static final int DICR_OFFSET = 0x10F4;

    // CDROM
    // Read
    public static final int CDROM_HSTS_OFFSET = 0x1800;
    public static final int CDROM_RESULT_OFFSET = 0x1801;
    public static final int CDROM_RDDATA_OFFSET = 0x1802;
    public static final int CDROM_HINT_OFFSET = 0x1803;

    // Write
    public static final int CDROM_ADDRESS_OFFSET = 0x1800;
    public static final int CDROM_COMMAND_OFFSET = 0x1801;
    public static final int CDROM_PARAMETER_OFFSET = 0x1802;
    public static final int CDROM_HCHPCTL_OFFSET = 0x1803;

    // GPU
    // Write
    public static final int GP0_COMMAND_OFFSET = 0x1810;
    public static final int GP1_COMMAND_OFFSET = 0x1814;
    // Read
    public static final int GPUREAD_OFFSET = 0x1810;
    public static final int GPUSTAT_OFFSET = 0x1814;

    // MDEC
    // Write
    public static final int MDEC_COMMAND_OFFSET = 0x1820;
    public static final int MDEC_CONTROL_OFFSET = 0x1824;
    // Read
    public static final int MDEC_RESPONSE_OFFSET = 0x1820;
    public static final int MDEC_STATUS_OFFSET = 0x1824;

    // SPU handled by memory read/write

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

            switch (offset) {
            case CDROM_HINT_OFFSET:
            case CDROM_RDDATA_OFFSET:
            case CDROM_RESULT_OFFSET:
            case CDROM_HSTS_OFFSET:
                return (byte)emulator.cdrom.read(offset - 0x1800);
            }

            throw new RuntimeException(String.format("Unimplemented readByte I/O offset 0x%04X", offset));
        }

        public short readShort(int offset) {
            if (offset >= 0 && offset < 0x400) {
                int result;
                result = ((int)readByte(offset) & 0xFF) | (int)readByte(offset + 1) << 8;
                return (short) result;
            }

            if (offset >= 0x1C00 && offset <= 0x1FFF) {
                // System.out.printf("Unimplemented SPU readShort 0x1F80%04X\n", offset);
                return 0;
            }

            if (offset >= 0x1100 && offset <= 0x1108 + 0x20) {
                Timer.TimerData data = emulator.timer.getTimer((offset - 0x1100) / 0x10);
                switch (offset & 0xF) {
                case 0: return (short)data.readValue();
                case 4: return (short)data.readMode();
                case 8: return (short)data.readTarget();
                }
            }


            switch (offset) {
            case JOY_CTRL_OFFSET: System.out.printf("Unimplemented JOY_CTRL read16\n"); return (short)0;
            case JOY_STAT_OFFSET: System.out.printf("Unimplemented JOY_STAT read16\n"); return (short)7;
            case I_STAT_OFFSET: return (short)emulator.interruptController.readStatus();
            case I_MASK_OFFSET: return (short)emulator.interruptController.readMask();
            }

            throw new RuntimeException(String.format("Unimplemented readShort I/O offset 0x%04X", offset));
        }

        public int readInt(int offset) {
            if (offset >= 0 && offset < 0x400) {
                int result = ((int)readShort(offset) & 0xFFFF) | (int)readShort(offset + 2) << 16;
                return result;
            }

            if (offset >= 0x1C00 && offset <= 0x1FFF) {
                System.out.printf("Unimplemented SPU readInt 0x1F80%04X", offset);
                return 0;
            }

            if (offset >= 0x1080 && offset <= 0x10EF) {
                DMA.Channel channel = emulator.dma.getChannel((offset - 0x1080) / 0x10);

                switch (offset & 0xF) {
                case 0: return channel.getBaseAddress();
                case 4: return channel.getBlockControl();
                case 8: return channel.getChannelControl();
                }
            }

            if (offset >= 0x1100 && offset <= 0x1108 + 0x20) {
                Timer.TimerData data = emulator.timer.getTimer((offset - 0x1100) / 0x10);
                switch (offset & 0xF) {
                case 0: return data.readValue();
                case 4: return data.readMode();
                case 8: return data.readTarget();
                }
            }

            switch (offset) {
            case EXPANSION_2_DELAY_OFFSET:
                return 0;
            case EXPANSION_1_BASE_ADDRESS_OFFSET:
            case EXPANSION_2_BASE_ADDRESS_OFFSET:
            case EXPANSION_1_DELAY_OFFSET:
            case EXPANSION_3_DELAY_OFFSET:
            case BIOS_ROM_DELAY_OFFSET:
            case SPU_DELAY_OFFSET:
            case CDROM_DELAY_OFFSET:
            case COMMON_DELAY_OFFSET:
            case RAM_SIZE_OFFSET:
                System.out.printf("Unimplemented MEMORY CONTROL readInt 0x1F80%04X\n", offset);
                return 0;
            case I_STAT_OFFSET: return emulator.interruptController.readStatus();
            case I_MASK_OFFSET: return emulator.interruptController.readMask();
            case DPCR_OFFSET: return emulator.dma.getDPCR();
            case DICR_OFFSET: return emulator.dma.getDICR();
            case GPUREAD_OFFSET:
                return 0;
            case GPUSTAT_OFFSET:
                return 0xFC000000;
            }

            throw new RuntimeException(String.format("Unimplemented readInt I/O offset 0x%04X", offset));
        }
        
        public void writeByte(int offset, byte value) {
            if (offset >= 0 && offset < 0x400) {
                scratchpad[offset] = value;
                return;
            }

            switch (offset) {
            case 0x2041: // POST
                return;
            case JOY_DATA_OFFSET:
                System.out.printf("Unimplemented JOY_DATA writeByte %02X\n", (int)value & 0xFF);
                return;
            case CDROM_ADDRESS_OFFSET:
            case CDROM_COMMAND_OFFSET:
            case CDROM_PARAMETER_OFFSET:
            case CDROM_HCHPCTL_OFFSET:
                emulator.cdrom.write(offset - 0x1800, (int)value & 0xFF);
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

            if (offset >= 0x1100 && offset <= 0x1108 + 0x20) {
                Timer.TimerData data = emulator.timer.getTimer((offset - 0x1100) / 0x10);
                switch (offset & 0xF) {
                case 0:
                    data.writeValue((int)value & 0xFFFF);
                    return;
                case 4:
                    data.writeMode((int)value & 0xFFFF);
                    return;
                case 8:
                    data.writeTarget((int)value & 0xFFFF);
                    return;
                }
            }

            if (offset >= 0x1C00 && offset <= 0x1FFF) {
                // System.out.printf("Unimplemented SPU writeShort 0x1F80%04X=%04X\n", offset, value);
                return;
            }

            switch (offset) {
            case JOY_MODE_OFFSET:
                System.out.printf("Unimplemented JOY_MODE writeShort %04X\n", (int)value & 0xFFFF);
                return;
            case JOY_CTRL_OFFSET:
                System.out.printf("Unimplemented JOY_CTRL writeShort %04X\n", (int)value & 0xFFFF);
                return;
            case JOY_BAUD_OFFSET:
                System.out.printf("Unimplemented JOY_BAUD writeShort %04X\n", (int)value & 0xFFFF);
                return;
            case I_STAT_OFFSET:
                emulator.interruptController.writeStatus(emulator.interruptController.readStatus() & value);
                emulator.interruptController.acknowledge();
                return;
            case I_MASK_OFFSET:
                emulator.interruptController.writeMask((int)value & 0xFFFF);
                emulator.interruptController.acknowledge();
                return;
            }

            throw new RuntimeException(String.format("Unimplemented writeShort I/O offset 0x%04X=%04X", offset, value & 0xFFFF));
        }

        public void writeInt(int offset, int value) {
            if (offset >= 0 && offset < 0x400) {
                writeShort(offset, (short)(value & 0xFFFF));
                writeShort(offset + 2, (short)((value >>> 16) & 0xFFFF));
                return;
            }

            if (offset >= 0x1080 && offset <= 0x10EF) {
                int channelIndex = (offset - 0x1080) / 0x10;
                DMA.Channel channel = emulator.dma.getChannel(channelIndex);

                switch (offset & 0xF) {
                case 0:
                    channel.setBaseAddress(value);
                    return;
                case 4:
                    channel.setBlockControl(value);
                    return;
                case 8:
                    channel.setChannelControl(value);
                    if ((value & (1 << 24)) != 0)
                        emulator.dma.runChannel(channelIndex);
                    return;
                }
            }

            if (offset >= 0x1100 && offset <= 0x1108 + 0x20) {
                Timer.TimerData data = emulator.timer.getTimer((offset - 0x1100) / 0x10);
                switch (offset & 0xF) {
                case 0:
                    data.writeValue((int)value & 0xFFFF);
                    return;
                case 4:
                    data.writeMode((int)value & 0xFFFF);
                    return;
                case 8:
                    data.writeTarget((int)value & 0xFFFF);
                    return;
                }
            }

            switch (offset) {
            case EXPANSION_1_BASE_ADDRESS_OFFSET:
            case EXPANSION_2_BASE_ADDRESS_OFFSET:
            case EXPANSION_1_DELAY_OFFSET:
            case EXPANSION_3_DELAY_OFFSET:
            case EXPANSION_2_DELAY_OFFSET:
            case BIOS_ROM_DELAY_OFFSET:
            case SPU_DELAY_OFFSET:
            case CDROM_DELAY_OFFSET:
            case COMMON_DELAY_OFFSET:
            case RAM_SIZE_OFFSET:
                System.out.printf("Unimplemented MEMORY CONTROL writeInt 0x1F80%04X = %08X\n", offset, value);
                return;

            // Interrupts
            case I_STAT_OFFSET:
                emulator.interruptController.writeStatus(emulator.interruptController.readStatus() & value);
                emulator.interruptController.acknowledge();
                return;
            case I_MASK_OFFSET:
                emulator.interruptController.writeMask((int)value & 0xFFFF);
                emulator.interruptController.acknowledge();
                return;

            case DPCR_OFFSET: emulator.dma.setDPCR(value); return;
            case DICR_OFFSET: emulator.dma.setDICR(value); return;
            case GP0_COMMAND_OFFSET:
                return;
            case GP1_COMMAND_OFFSET:
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

    public Memory(Emulator emu) {
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
            throw new RuntimeException(String.format("Unknown writeInt address %08X value %08X PC %08X", address, value, emulator.mips.PC));
        }
        exec.writeInt(address & exec.getAddressMask(), value);
    }

    public byte[] getBiosData() {
        return biosAccess.bios;
    }
    
    public byte[] getRamData() {
        return ramDirectAccess.ram;
    }
    
    public void dumpRam(String path) {
        try (RandomAccessFile file = new RandomAccessFile(path, "w")) {
            file.write(ramDirectAccess.ram);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}