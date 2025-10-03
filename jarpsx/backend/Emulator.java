package jarpsx.backend;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import jarpsx.backend.component.*;
import jarpsx.backend.*;
import jarpsx.backend.mips.*;

public class Emulator {
    static public class Stats {
        public long microsecondsRanPerFrame;
        public long microsecondsRan;
    }

    public Memory memory;
    public MIPS mips;
    public Debugger debugger;
    public CDROM cdrom;
    public DMA dma;
    public InterruptController interruptController;
    public MDEC mdec;
    public Peripheral peripheral;
    public SPU spu;
    public Timer timer;
    public Scheduler scheduler;
    public Stats stats;
    public Disk disk;
    private boolean error;

    public Emulator() {
        scheduler = new Scheduler(this);
        memory = new Memory(this);
        mips = new MIPS(this);
        debugger = new Debugger(this);
        interruptController = new InterruptController(this);
        timer = new Timer(this);
        dma = new DMA(this);
        cdrom = new CDROM(this);
        stats = new Stats();
        disk = new Disk();

        stats.microsecondsRanPerFrame = stats.microsecondsRan = 0;
        scheduler.registerEventCallback(Scheduler.EVENT_BREAK_DISPATCH, (userdata) -> {
            scheduler.schedule(20000, Scheduler.EVENT_BREAK_DISPATCH, null);
        });
        
        scheduler.schedule(20000, Scheduler.EVENT_BREAK_DISPATCH, null);

        error = false;
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

        reader.close();
    }
    
    public void dumpMemory() {
        try {
            FileOutputStream out = new FileOutputStream(Paths.get("").toAbsolutePath().toString() + "\\ram.dump");
            out.write(memory.getRamData());
            out.close();
        } catch (Exception e) {
            
        }
    }
    
    public void runFor(int cycles) {
        try {
            for (int i = 0; i < cycles; i++) {
                mips.step();
                cdrom.step(1);
            }

            interruptController.service(InterruptController.IRQ_VBLANK);
        } catch (Exception exception) {
            StackTraceElement[] elements = exception.getStackTrace();

            System.out.println("Stack trace and message: " + exception.getMessage());
            exception.printStackTrace();
            
            System.out.printf("\nPC %08X", mips.gpr[31]);
            System.exit(-1);
        }
    }
}