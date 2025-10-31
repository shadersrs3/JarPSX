package jarpsx.backend.mips;

import java.nio.file.Path;
import java.nio.file.Paths;

import jarpsx.backend.Emulator;
import jarpsx.backend.mips.Interpreter;

public class MIPS {
    private static final int[] exceptionAddress_BEV0 = { 0xBFC0_0000, 0x8000_0000, 0x8000_0040, 0x8000_0080 };
    private static final int[] exceptionAddress_BEV1 = { 0xBFC0_0000, 0xBFC0_0100, 0xBFC0_0140, 0xBFC0_0180 };
    public static final int Exception_Interrupt = 0x00;
    public static final int Exception_MOD = 0x01;
    public static final int Exception_TLBL = 0x02;
    public static final int Exception_TLBS = 0x03;
    public static final int Exception_AdEL = 0x04;
    public static final int Exception_AdES = 0x05;
    public static final int Exception_InstructionBusError = 0x06;
    public static final int Exception_DataBusError = 0x07;
    public static final int Exception_Syscall = 0x08;
    public static final int Exception_Breakpoint = 0x09;
    public static final int Exception_ReservedInstruction = 0x0A;
    public static final int Exception_CoprocessorUnused = 0x0B;
    public static final int Exception_ArithmeticOverflow = 0x0C;
    public static final int ZERO = 0;
    public static final int AT = 1;
    public static final int V0 = 2;
    public static final int V1 = 3;
    public static final int A0 = 4;
    public static final int A1 = 5;
    public static final int A2 = 6;
    public static final int A3 = 7;
    public static final int T0 = 8;
    public static final int T1 = 9;
    public static final int T2 = 10;
    public static final int T3 = 11;
    public static final int T4 = 12;
    public static final int T5 = 13;
    public static final int T6 = 14;
    public static final int T7 = 15;
    public static final int S0 = 16;
    public static final int S1 = 17;
    public static final int S2 = 18;
    public static final int S3 = 19;
    public static final int S4 = 20;
    public static final int S5 = 21;
    public static final int S6 = 22;
    public static final int S7 = 23;
    public static final int T8 = 24;
    public static final int T9 = 25;
    public static final int K0 = 26;
    public static final int K1 = 27;
    public static final int GP = 28;
    public static final int SP = 29;
    public static final int FP = 30;
    public static final int RA = 31;
    private Instruction currentInstruction;
    private Emulator emulator;
    private long cyclesElapsed;
    public int[] gpr;
    public int hi, lo;
    public int PC;
    public int branchPC;
    public boolean branchDelaySet;
    public boolean exceptionBranchDelay;
    public Cop0Register[] cop0reg;
    public int[] gteReg;
    public int loadDelayCounter;
    public LoadDelayRegister[] loadDelayReg;
    public boolean previousWriteToSR = false;
    public boolean linkSet;
    public int linkIndex;
    public boolean requiredToCancel = false;
    public int cancelRegisterIndex = 0;
    public class LoadDelayRegister {
        public int value;
        public int index;
    }

    public class Cop0Register {
        public String name;
        public int value;
        public int index;

        public Cop0Register(String name, int value, int index) {
            this.name = name;
            this.value = value;
            this.index = index;
        }
        
        public void setValue(int value) {
            switch (index) {
            case 13:
                if (((value & 0x300) & (cop0reg[12].value & 0x300)) != 0)
                    emulator.interruptController.setIrq(true);
                this.value = (this.value & ~0x00000300) | (value & 0x00000300);
                break;
            case 12:
                if (((cop0reg[13].value & 0x300) & (value & 0x300)) != 0)
                    emulator.interruptController.setIrq(true);
                previousWriteToSR = true;
                this.value = value;
                break;
            default:
                break;
            }
        }
        
        public void setValueGlobal(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public MIPS(Emulator emulator) {
        this.emulator = emulator;
        cyclesElapsed = 0L;

        currentInstruction = new Instruction(0);
        cop0reg = new Cop0Register[64];
        cop0reg[3] = new Cop0Register("BPC", 0, 3);
        cop0reg[5] = new Cop0Register("BDA", 0, 5);
        cop0reg[6] = new Cop0Register("TAR", 0, 6);
        cop0reg[7] = new Cop0Register("DCIC", 0, 7);
        cop0reg[8] = new Cop0Register("BadVaddr", 0, 9);
        cop0reg[9] = new Cop0Register("BDAM", 0, 9);
        cop0reg[11] = new Cop0Register("BPCM", 0, 11);
        cop0reg[12] = new Cop0Register("SR", 0, 12);
        cop0reg[13] = new Cop0Register("CAUSE", 0, 13);
        cop0reg[14] = new Cop0Register("EPC", 0, 14);
        cop0reg[15] = new Cop0Register("PRID", 0xbaadf00d, 15);

        gteReg = new int[64];

        hi = lo = branchPC = 0;
        branchDelaySet = false;
        exceptionBranchDelay = false;
        PC = 0xBFC00000;

        loadDelayReg = new LoadDelayRegister[2];
        loadDelayCounter = 0;
        gpr = new int[32];
        for (int i = 0; i < 32; i++)
            gpr[i] = 0;        
    }
    
    public void handleLoadDelaySlot(boolean loadDelaySet) {
        if (loadDelaySet) {
            if (branchDelaySet) {
                if (linkSet && linkIndex == loadDelayReg[0].index) {
                    loadDelayReg[0] = loadDelayReg[1];
                    loadDelayReg[1] = null;
                    --loadDelayCounter;
                    linkSet = false;
                    return;
                }
            }
            
            if (requiredToCancel && loadDelayReg[0].index == cancelRegisterIndex) {
                loadDelayReg[0] = loadDelayReg[1];
                loadDelayReg[1] = null;
                --loadDelayCounter;
                return;
            }
            
            if (!(loadDelayCounter == 2 && loadDelayReg[0].index == loadDelayReg[1].index)) { // Does not have a cancelled load delay
                gpr[loadDelayReg[0].index] = loadDelayReg[0].value;
            }

            loadDelayReg[0] = loadDelayReg[1];
            loadDelayReg[1] = null;
            --loadDelayCounter;
        }
    }
    
    public void writeGPR(int index, int data) {
        gpr[index] = data;
        requiredToCancel = true;
        cancelRegisterIndex = index;
    }

    public void writeGPRDelayed(int index, int data) {
        loadDelayReg[loadDelayCounter] = new LoadDelayRegister();
        loadDelayReg[loadDelayCounter].index = index;
        loadDelayReg[loadDelayCounter].value = data;    
        loadDelayCounter++;
        requiredToCancel = false;
        // gpr[index] = data;
    }

    public long getCyclesElapsed() {
        return cyclesElapsed;
    }
    
    public void incrementCycles(long cycles) {
        cyclesElapsed += cycles;
    }

    public byte readByte(int address) {
        return emulator.memory.readByte(address);
    }

    public short readShort(int address) {
        return emulator.memory.readShort(address);
    }

    public int readByteUnsigned(int address) {
        return (int)emulator.memory.readByte(address) & 0xFF;
    }

    public int readShortUnsigned(int address) {
        return (int)emulator.memory.readShort(address) & 0xFFFF;
    }

    public int readInt(int address) {
        return emulator.memory.readInt(address);
    }

    public void writeByte(int address, byte value) {
        emulator.memory.writeByte(address, value);
    }

    public void writeShort(int address, short value) {
        emulator.memory.writeShort(address, value);
    }

    public void writeInt(int address, int value) {
        emulator.memory.writeInt(address, value);
    }

    public void setJump(int address) {
        branchPC = address;
        branchDelaySet = true;
        linkSet = false;
    }

    public void setCop0Value(int index, int value) {
        if (cop0reg[index] == null)
            throw new RuntimeException(String.format("Invalid cop0 register index %d with write 0x%08X PC %08X", index, value, PC));
        cop0reg[index].setValue(value);
    }
    
    public int getCop0Value(int index) {
        if (cop0reg[index] == null)
            throw new RuntimeException(String.format("Invalid read cop0 register index %d PC %08X RA %08X", index, PC, gpr[31]));
        return cop0reg[index].getValue();
    }

    public void setBadVaddr(int address) {
        cop0reg[8].value = address;
    }
    
    public void setEPC(int address) {
        cop0reg[14].value = address;
    }

    public void setDCIC(int data) {
        cop0reg[7].value = data;
    }

    static public boolean once = true;
    private int x = 0;
    private Instruction fetchInstruction() {
        int data = 0;
        if ((PC & 3) != 0) {
            int address = PC;
            triggerException(Exception_AdEL);
            setBadVaddr(address);
            setEPC(address);
        }
        
        switch (PC) {
        case 0xB0:
            switch (gpr[9]) {
            case 0x3f:
                for (int i = 0; i < 2000; i++) {
                    int addr = i + gpr[4];
                    char _data = (char)readByte(addr);
                    if (_data == '\0') {
                        break;
                    }

                    System.out.printf("%c", _data);
                }
                break;
            case 0x3d:                
                System.out.print((char)gpr[4]);
                break;
            }
            break;
        case 0x80030000:
            try {
                if (once == false) {
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\jakub\\gte\\test-all\\test-all.exe");
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\gpu\\gp0-e1\\gp0-e1.exe");
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\movie-15bit.exe");
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\hello_cd.exe");
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\cdlreads.exe");
                    emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\frame-15bit-dma.exe");
                    once = true;

                    String[] args = { "auto\0", "console\0", "release\0" };
                    int argLen = 2;
                    int len = 0;
                    
                    for (int i = 0; i < argLen; i++) {
                        writeInt(0x1f800004+i*4, 0x1f800044+len);
                    
                        int x;
                        int n = args[i].length();
                        for (x = len; x < len + n; x++) {
                            writeByte(0x1f800044 + x, (byte)args[i].charAt(x-len));
                        }
                        
                        len = x;
                    }

                    writeInt(0x1f800000, argLen);
                }
            } catch (Exception e) {
                System.out.println("Exception occured: " + e.getMessage());
            }
            break;
        }

        data = readInt(PC);
        
        if (PC == 0x8001ae24) {
            System.out.printf("dec vlc 0x%08X %08x\n", PC, gpr[A0]);
            // System.exit(1);
        }
        if (PC == 0x8001a620) {
            System.out.printf("DecDCTin\n");
        }

        currentInstruction.setData(data);
        return currentInstruction;
    }

    public void triggerException(int exceptionCode) {
        int cause = cop0reg[13].getValue();
        int sr = cop0reg[12].getValue();
        int exccode = exceptionCode << 2;
        int mode = sr & 0x3F;
        int selectedHandlerAddress = 3;
        boolean bev1Set = ((sr >>> 22) & 1) != 0;
        int epc;
        int bd = 0;

        if (exceptionCode == Exception_Interrupt) {
            exceptionBranchDelay = true;
            if (branchDelaySet) {
                epc = PC - 4;
            } else {
                epc = PC;
                // System.exit(1);
            }

            branchDelaySet = false;
        } else {
            if (branchDelaySet) {
                exceptionBranchDelay = true;
                branchDelaySet = false;
                epc = PC - 4;
                bd = 0x80000000;
            } else {
                epc = PC;
            }            
        }

        if (bev1Set) {
            PC = exceptionAddress_BEV1[selectedHandlerAddress];
        } else {
            PC = exceptionAddress_BEV0[selectedHandlerAddress];
        }

        sr &= ~0x3F;
        sr |= (mode << 2) & 0x3F;
        cause = (cause & ~0x8000007C) | exccode | bd;
        cop0reg[12].setValueGlobal(sr);
        cop0reg[13].setValueGlobal(cause);
        cop0reg[14].setValueGlobal(epc);
    }

    public boolean checkForInterrupts() {
        boolean interruptOccured = false;
        boolean previousWriteToSR = this.previousWriteToSR == true;
        int interruptMask = cop0reg[12].value & 0xFF00;
        int interruptPendingFields = cop0reg[13].value & 0xFF00;
        
        if ((cop0reg[12].value & 1) != 0 && (interruptMask & interruptPendingFields) != 0 && emulator.interruptController.isIrqSet()) {
            emulator.interruptController.setIrq(false);
            interruptOccured = true;
            if (previousWriteToSR == false)
                triggerException(Exception_Interrupt);
            cop0reg[13].value &= ~(1 << 10);
        }

        return interruptOccured;
    }

    public void step() {
        boolean interruptOccured = checkForInterrupts();
        boolean previousWriteToSR = this.previousWriteToSR == true;
        boolean branchDelaySet = this.branchDelaySet == true;
        boolean loadDelaySet = loadDelayCounter > 0;

        Instruction instruction = fetchInstruction();
        gpr[0] = 0;
        
        Interpreter.execute(this, instruction);

        handleLoadDelaySlot(loadDelaySet);

        if (branchDelaySet && exceptionBranchDelay == false) {
            PC = branchPC;
            this.branchDelaySet = false;
        }

        if (previousWriteToSR && interruptOccured) {
            triggerException(Exception_Interrupt);
        }

        if (previousWriteToSR) {
            this.previousWriteToSR = false;
        }

        exceptionBranchDelay = false;
        incrementCycles(1L);
    }
}