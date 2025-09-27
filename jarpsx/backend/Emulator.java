package jarpsx.backend;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import jarpsx.backend.mips.MIPS;
import jarpsx.backend.mips.Debugger;
import jarpsx.backend.Memory;

public class Emulator {
    static public class Stats {
        public long microsecondsRanPerFrame;
        public long microsecondsRan;
    }

    private Memory memory;
    private MIPS mips;
    private Debugger debugger;
    private boolean error;
    public Stats stats;

    public Emulator() {
        memory = new Memory(this);
        mips = new MIPS(this);
        debugger = new Debugger(this);
        stats = new Stats();
        stats.microsecondsRanPerFrame = stats.microsecondsRan = 0;

        error = false;
    }

    public Memory getMemory() {
        return memory;
    }

    public MIPS getCpu() {
        return mips;
    }

    public Debugger getDebugger() {
        return debugger;
    }

    public void setError(boolean state) {
        error = state;
    }

    public boolean hasError() {
        return error;
    }

    public void loadBIOS(String biosPath) throws FileNotFoundException, IOException {
        FileInputStream reader;
        
        byte[] buf;
        int status;
        int readBytes;
        System.out.println("Loading BIOS from path: " + biosPath);

        readBytes = status = 0;
        reader = new FileInputStream(biosPath);
    
        buf = new byte[16384];
        while (readBytes < 524288) {
            status = reader.read(buf);

            if (status == -1)
                break;

            Memory.Utility.copyMemory(memory.getBiosData(), readBytes, buf, 0, status);
            readBytes += status;
            if (readBytes >= 524288)
                break;

        }

        reader.close();
    }

    private static class ByteArray {
        public static int readIntLittleEndian(byte[] data) {
            int set = 0;
            for (int i = 0; i < data.length && i < 4; i++) {
                set = set | ((int)data[i] & 0xFF) << (i * 8);
            }
            return set;
        }
    }

    public void sideloadPSXExecutable(String executablePath) throws FileNotFoundException, IOException {
        FileInputStream reader;
        byte[] headerIntData;
        int status;
        int readBytes;
        int initialPC, initialGP, ramDestinationAddress, baseAddress, offset, initialSP, fileSize;
        System.out.println("Sideloading PSX executable from path: " + executablePath);

        reader = new FileInputStream(executablePath);
        headerIntData = new byte[4];
        reader.skip(0x10);
        reader.read(headerIntData); // 0x10
        initialPC = ByteArray.readIntLittleEndian(headerIntData);
        reader.read(headerIntData); // 0x14
        initialGP = ByteArray.readIntLittleEndian(headerIntData);
        reader.read(headerIntData); // 0x18
        ramDestinationAddress = ByteArray.readIntLittleEndian(headerIntData);
        reader.read(headerIntData); // 0x1C
        fileSize = ByteArray.readIntLittleEndian(headerIntData);
        reader.skip(0x10);
        reader.read(headerIntData); // 0x30
        baseAddress = ByteArray.readIntLittleEndian(headerIntData);
        reader.read(headerIntData); // 0x34
        offset = ByteArray.readIntLittleEndian(headerIntData);
        initialSP = baseAddress + offset;
        reader.skip(0x7C8); // reaches to 0x800

        byte[] buffer = new byte[0x800];
        readBytes = status = 0;
        while (readBytes < fileSize) {
            status = reader.read(buffer);
            if (status == -1)
                break;
            
            Memory.Utility.copyMemory(memory.getRamData(), (ramDestinationAddress & 0x1FFFFF) + readBytes, buffer, 0, status);
            readBytes += status;
        }

        mips.PC = initialPC;
        mips.branchDelaySet = false;

        if (baseAddress != 0) {
            mips.gpr[29] = mips.gpr[30] = initialSP;
            mips.gpr[28] = initialGP;
        }
    }
    
    public void runFor(int cycles) {
        try {
            for (int i = 0; i < cycles; i++) {
                mips.step();
            }
        } catch (Exception exception) {
            StackTraceElement[] elements = exception.getStackTrace();
            System.out.println("Stack trace and message: " + exception.getMessage());
            for (int i = 0; i < elements.length; i++) {
                System.out.println("    " + elements[i].toString());
            }
            System.exit(-1);
        }
    }
}