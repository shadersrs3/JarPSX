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

    public class LoadDelayRegister {
        public int value;
        public int index;
    }

    public class Cop0Register {
        private String name;
        private int value;
        private int index;

        Cop0Register(String name, int value, int index) {
            this.name = name;
            this.value = value;
            this.index = index;
        }
        
        public void setValue(int value) {
            switch (index) {
            case 13:
                this.value = (this.value & ~0x00000300) | (value & 0x00000300);
                break;
            case 12:
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

    public interface Cop2RegisterInterface {
        public String getName();
        public int getIndex();
        public void setValue(int value);
        public int getValue();
        public void setValue64(long value);
        public long getValue64();
        default String getCopRegisterName() {
            return "";
        }
    }
    
    public MIPS(Emulator emulator) {
        this.emulator = emulator;
        cyclesElapsed = 0L;

        cop0reg = new Cop0Register[64];

        cop0reg[3] = new Cop0Register("BPC", 0, 3);
        cop0reg[5] = new Cop0Register("BDA", 0, 5);
        cop0reg[6] = new Cop0Register("TAR", 0, 6);
        cop0reg[7] = new Cop0Register("DCIC", 0, 7);
        cop0reg[8] = new Cop0Register("BadA", 0, 9);
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
        
        Interpreter.initializeTable();
    }
    
    public void handleLoadDelaySlot(boolean loadDelaySet) {
        if (loadDelaySet) {
            if (!(loadDelayCounter == 2 && loadDelayReg[0].index == loadDelayReg[1].index)) { // Does not have a cancelled load delay
                gpr[loadDelayReg[0].index] = loadDelayReg[0].value;
            }

            loadDelayReg[0] = loadDelayReg[1];
            loadDelayReg[1] = null;
            --loadDelayCounter;
        }
    }

    public void writeGPRDelayed(int index, int data) {
        loadDelayReg[loadDelayCounter] = new LoadDelayRegister();
        loadDelayReg[loadDelayCounter].index = index;
        loadDelayReg[loadDelayCounter].value = data;    
        loadDelayCounter++;
        // gpr[index] = data;
    }

    public long getCyclesElapsed() {
        return cyclesElapsed;
    }
    
    public void incrementCycles(long cycles) {
        cyclesElapsed += cycles;
    }

    public byte readByte(int address) {
        return emulator.getMemory().readByte(address);
    }

    public short readShort(int address) {
        return emulator.getMemory().readShort(address);
    }

    public int readByteUnsigned(int address) {
        return (int)emulator.getMemory().readByte(address) & 0xFF;
    }

    public int readShortUnsigned(int address) {
        return (int)emulator.getMemory().readShort(address) & 0xFFFF;
    }

    public int readInt(int address) {
        return emulator.getMemory().readInt(address);
    }

    public void writeByte(int address, byte value) {
        emulator.getMemory().writeByte(address, value);
    }

    public void writeShort(int address, short value) {
        emulator.getMemory().writeShort(address, value);
    }

    public void writeInt(int address, int value) {
        emulator.getMemory().writeInt(address, value);
    }

    public void setJump(int address) {
        branchPC = address;
        branchDelaySet = true;
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
            epc = PC;
        } else {
            if (branchDelaySet) {
                exceptionBranchDelay = true;
                branchDelaySet = false;
                epc = PC;
                bd = 0x80000000;
                System.out.println("hello world");
                System.exit(-1);
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
        
        // System.out.println("Cause " + String.format("%08X", cop0reg[13].getValue()));
        //System.out.println("Exception occured " + String.format("%08X", PC));
        //System.exit(-1);
    }

    static boolean once = false;
    static String test;
    static int counter = 0;

    public void step() {
        switch (PC) {
        /*
        case 0x80010310:
            if (counter > 50) { // make a blacklist here
                PC = 0x80010130; // or exit as you like
            }
            break;
        case 0x80010108:
            // assume static counter = 0
            counter++;
            System.out.println("Test " + counter);
            break;
        */
        case 0xB0:
            if (gpr[9] == 0x3d) {
                if ((char)gpr[4] == '\n') {
                    test = "";
                } else {
                    test += (char) gpr[4];
                }
                System.out.print((char)gpr[4]);
            }
            break;
        case 0x80030000:
            try {
                if (once == false) {
                    // emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\jakub\\gte\\test-all\\test-all.exe");
                    emulator.sideloadPSXExecutable(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\psxtest_cpx.exe");
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
        case 0x8001eeb4: {
            /*
            String n = new String();
            for (int i = 0, v; (v = readByte(gpr[4] + i)) != 0; i++) {
                n += (char) v;
                System.out.print((char) v);
            }
            */
            // System.out.println(String.format("%08x", gpr[31]));
            break;
        }
        }

        if ((cop0reg[12].value & 1) != 0) {
            //System.out.printf("interrupt occured\n");
        }

        boolean branchDelaySet = this.branchDelaySet == true;
        boolean loadDelaySet = loadDelayCounter > 0;
        Instruction instruction = new Instruction(emulator.getMemory().readInt(PC));
        gpr[0] = 0;
        Interpreter.execute(this, instruction);

        handleLoadDelaySlot(loadDelaySet);
        if (branchDelaySet && exceptionBranchDelay == false) {
            PC = branchPC;
            this.branchDelaySet = false;
        }

        exceptionBranchDelay = false;
        incrementCycles(1L);
    }
}