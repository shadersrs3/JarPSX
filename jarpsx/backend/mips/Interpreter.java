package jarpsx.backend.mips;

import jarpsx.backend.mips.Instruction;
import jarpsx.backend.mips.GTEInterpreter;
import jarpsx.backend.mips.GTEInstruction;
import jarpsx.backend.mips.Disassembler;

public class Interpreter {
    private interface Execution {
        public void execute(MIPS mips, Instruction instruction);
    }
    
    public static Execution[] opcodeExecutor = new Execution[0x40];
    public static Execution[] functorExecutor = new Execution[0x40];

    public static void interpretSLL(MIPS mips, Instruction instruction) {
        int rd, rt, imm5;
        
        rd = instruction.rd();
        rt = instruction.rt();
        imm5 = instruction.imm5();
        mips.writeGPR(rd, mips.gpr[rt] << imm5);
        mips.PC += 4;
    }

    public static void interpretSRL(MIPS mips, Instruction instruction) {
        int rd, rt, imm5;
        
        rd = instruction.rd();
        rt = instruction.rt();
        imm5 = instruction.imm5();
        mips.writeGPR(rd, mips.gpr[rt] >>> imm5);
        mips.PC += 4;
    }

    public static void interpretSRA(MIPS mips, Instruction instruction) {
        int rd, rt, imm5;
        
        rd = instruction.rd();
        rt = instruction.rt();
        imm5 = instruction.imm5();
        mips.writeGPR(rd, mips.gpr[rt] >> imm5);
        mips.PC += 4;
    }

    public static void interpretSLLV(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        int shift;
        
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        shift = mips.gpr[rs] & 0x1F;
        mips.writeGPR(rd, mips.gpr[rt] << shift);
        mips.PC += 4;
    }

    public static void interpretSRLV(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        int shift;
        
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        shift = mips.gpr[rs] & 0x1F;
        mips.writeGPR(rd, mips.gpr[rt] >>> shift);
        mips.PC += 4;
    }

    public static void interpretSRAV(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        int shift;
        
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        shift = mips.gpr[rs] & 0x1F;
        mips.writeGPR(rd, mips.gpr[rt] >> shift);
        mips.PC += 4;
    }

    public static void interpretJR(MIPS mips, Instruction instruction) {
        int address = mips.gpr[instruction.rs()];
        
        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            mips.setEPC(address);
            mips.setBadVaddr(address);
            return;
        }

        mips.setJump(mips.gpr[instruction.rs()]);
        mips.PC += 4;
    }

    public static void interpretJALR(MIPS mips, Instruction instruction) {
        int address = mips.gpr[instruction.rs()];

        mips.gpr[instruction.rd()] = mips.PC + 8;
        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            mips.setEPC(address);
            mips.setBadVaddr(address);
            return;
        }

        mips.linkSet = true;
        mips.linkIndex = instruction.rd();
        mips.setJump(address);
        mips.PC += 4;
    }

    public static void interpretSYSCALL(MIPS mips, Instruction instruction) {
        mips.triggerException(MIPS.Exception_Syscall);
    }

    public static void interpretBREAK(MIPS mips, Instruction instruction) {
        mips.triggerException(MIPS.Exception_Breakpoint);
    }

    public static void interpretMFHI(MIPS mips, Instruction instruction) {
        mips.writeGPR(instruction.rd(), mips.hi);
        mips.PC += 4;
    }

    public static void interpretMTHI(MIPS mips, Instruction instruction) {
        mips.hi = mips.gpr[instruction.rs()];
        mips.PC += 4;
    }

    public static void interpretMFLO(MIPS mips, Instruction instruction) {
        mips.writeGPR(instruction.rd(), mips.lo);
        mips.PC += 4;
    }

    public static void interpretMTLO(MIPS mips, Instruction instruction) {
        mips.lo = mips.gpr[instruction.rs()];
        mips.PC += 4;
    }

    public static void interpretMULT(MIPS mips, Instruction instruction) {
        long a = (long)(int)mips.gpr[instruction.rs()];
        long b = (long)(int)mips.gpr[instruction.rt()];
        long result = a * b;
        mips.hi = (int)(result >>> 32);
        mips.lo = (int)(result & 0xFFFFFFFF);
        mips.PC += 4;
    }

    public static void interpretMULTU(MIPS mips, Instruction instruction) {
        long a = (long)mips.gpr[instruction.rs()];
        long b = (long)mips.gpr[instruction.rt()];
        long result = (a & 0xFFFFFFFFL) * (b & 0xFFFFFFFFL);
        mips.hi = (int)(result >>> 32);
        mips.lo = (int)(result & 0xFFFFFFFF);
        mips.PC += 4;
    }

    public static void interpretDIV(MIPS mips, Instruction instruction) {
        int Rs = mips.gpr[instruction.rs()];
        int Rt = mips.gpr[instruction.rt()];
        if (Rt == 0) {
            mips.hi = Rs;
            mips.lo = Rs >= 0 ? -1 : +1;
        } else if (Rs == -0x80000000 && Rt == -1) {
            mips.hi = 0;
            mips.lo = -0x80000000;
        } else {
            mips.lo = Rs / Rt;
            mips.hi = Rs % Rt;
        }
        mips.PC += 4;
    }

    public static void interpretDIVU(MIPS mips, Instruction instruction) {
        int Rs = mips.gpr[instruction.rs()];
        int Rt = mips.gpr[instruction.rt()];
        if (Rt == 0) {
            mips.hi = Rs;
            mips.lo = 0xFFFFFFFF;
        } else {
            mips.lo = Integer.divideUnsigned(Rs, Rt);
            mips.hi = Integer.remainderUnsigned(Rs, Rt);
        }

        mips.PC += 4;
    }

    public static void interpretADD(MIPS mips, Instruction instruction) {
        int rd, rt, rs, result;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        result = mips.gpr[rs] + mips.gpr[rt];
        if (((mips.gpr[rs] ^ mips.gpr[rt]) & 0x80000000) == 0 && ((result ^ mips.gpr[rs]) & 0x80000000) == 0x80000000) {
            mips.triggerException(MIPS.Exception_ArithmeticOverflow);
            return;
        }

        mips.writeGPR(rd, result);
        mips.PC += 4;
    }

    public static void interpretADDU(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, mips.gpr[rs] + mips.gpr[rt]);
        mips.PC += 4;
    }

    public static void interpretSUB(MIPS mips, Instruction instruction) {
        int rd, rt, rs, result;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        result = mips.gpr[rs] - mips.gpr[rt];
        if (((mips.gpr[rs] ^ mips.gpr[rt]) & 0x80000000) == 0x80000000 && ((result ^ mips.gpr[rs]) & 0x80000000) == 0x80000000) {
            mips.triggerException(MIPS.Exception_ArithmeticOverflow);            
            return;
        }

        mips.writeGPR(rd, result);
        mips.PC += 4;
    }

    public static void interpretSUBU(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, mips.gpr[rs] - mips.gpr[rt]);
        mips.PC += 4;
    }

    public static void interpretOR(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, mips.gpr[rs] | mips.gpr[rt]);
        mips.PC += 4;
    }

    public static void interpretAND(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, mips.gpr[rs] & mips.gpr[rt]);
        mips.PC += 4;
    }

    public static void interpretXOR(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, mips.gpr[rs] ^ mips.gpr[rt]);
        mips.PC += 4;
    }

    public static void interpretNOR(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.writeGPR(rd, ~(mips.gpr[rs] | mips.gpr[rt]));
        mips.PC += 4;
    }

    public static void interpretSLT(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        boolean set;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        set = mips.gpr[rs] < mips.gpr[rt];

        mips.writeGPR(rd, set ? 1 : 0);
        mips.PC += 4;
    }

    public static void interpretSLTU(MIPS mips, Instruction instruction) {
        int rd, rt, rs;
        rd = instruction.rd();
        rt = instruction.rt();
        rs = instruction.rs();
        mips.gpr[rd] = Integer.compareUnsigned(mips.gpr[rs], mips.gpr[rt]);
        if (mips.gpr[rd] < 0)
            mips.writeGPR(rd, 1);
        else
            mips.writeGPR(rd, 0);
        mips.PC += 4;
    }

    public static void interpretFUNCTOR(MIPS mips, Instruction instruction) {
        Execution exec = getFunctorExecutor(instruction);
        if (exec == null) {
            throw new RuntimeException(String.format("Unknown functor %02X PC %08X", instruction.functor(), mips.PC));
        }
        
        exec.execute(mips, instruction);
    }

    public static void interpretBLTZ_BGEZ(MIPS mips, Instruction instruction) {
        int rs = instruction.rs();
        int code = instruction.rt();
        int offset = instruction.signedImmediate();
        int branchAddress = mips.PC + offset * 4 + 4;
        boolean link = (code & 0x1E) == 0x10;
        boolean lessThan = (code & 1) == 0;
        boolean branchSet;

        if (lessThan) {
            branchSet = mips.gpr[rs] < 0;
        } else {
            branchSet = mips.gpr[rs] >= 0;
        }
        
        if (branchSet)
            mips.setJump(branchAddress);
        
        if (link) {
            mips.gpr[31] = mips.PC + 8;
            mips.linkSet = true;
            mips.linkIndex = 31;
        }

        mips.PC += 4;
    }

    public static void interpretJ(MIPS mips, Instruction instruction) {
        int address = (mips.PC & 0xF0000000) | (instruction.target() << 2);
        mips.setJump(address);
        mips.PC += 4;
    }

    public static void interpretJAL(MIPS mips, Instruction instruction) {
        int address = (mips.PC & 0xF0000000) | (instruction.target() << 2);
        mips.gpr[31] = mips.PC + 8;
        mips.setJump(address);

        mips.linkSet = true;
        mips.linkIndex = 31;

        mips.PC += 4;
    }

    public static void interpretBEQ(MIPS mips, Instruction instruction) {
        int rs = instruction.rs();
        int rt = instruction.rt();
        int offset = instruction.signedImmediate();
        int branchAddress = mips.PC + offset * 4 + 4;
        
        if (mips.gpr[rs] == mips.gpr[rt]) {
            mips.setJump(branchAddress);
        }

        mips.PC += 4;
    }

    public static void interpretBNE(MIPS mips, Instruction instruction) {
        int rs = instruction.rs();
        int rt = instruction.rt();
        int offset = instruction.signedImmediate();
        int branchAddress = mips.PC + offset * 4 + 4;
        
        if (mips.gpr[rs] != mips.gpr[rt]) {
            mips.setJump(branchAddress);
        }

        mips.PC += 4;
    }

    public static void interpretBLEZ(MIPS mips, Instruction instruction) {
        int rs = instruction.rs();
        int rt = instruction.rt();
        int offset = instruction.signedImmediate();
        int branchAddress = mips.PC + offset * 4 + 4;
        
        if (mips.gpr[rs] <= 0) {
            mips.setJump(branchAddress);
        }

        mips.PC += 4;
    }

    public static void interpretBGTZ(MIPS mips, Instruction instruction) {
        int rs = instruction.rs();
        int rt = instruction.rt();
        int offset = instruction.signedImmediate();
        int branchAddress = mips.PC + offset * 4 + 4;
        
        if (mips.gpr[rs] > 0) {
            mips.setJump(branchAddress);
        }

        mips.PC += 4;
    }

    public static void interpretCOP0(MIPS mips, Instruction instruction) {
        int op = instruction.rs();
        switch (op) {
        case 0b00000:
            if (instruction.functor() == 0) {
                mips.writeGPRDelayed(instruction.rt(), mips.getCop0Value(instruction.rd()));
                mips.PC += 4;
                return;
            }
            break;
        case 0b00100:
            if (instruction.functor() == 0) {
                mips.setCop0Value(instruction.rd(), mips.gpr[instruction.rt()]);
                mips.PC += 4;
                return;
            }
            break;
        case 0b10000:
            if (instruction.functor() == 0x10) {
                int sr = mips.cop0reg[12].getValue();
                int iku = (sr & 0x3F) >> 2;
                sr = (sr & ~0xF) | iku;
                mips.cop0reg[12].setValue(sr);
                mips.PC += 4;
                return;
            }
            break;
        }
        System.out.println(String.format("Invalid cop0 opcode %02X PC %08X", op, mips.PC));
        System.exit(-1);
    }
    
    public static void interpretCOP2(MIPS mips, Instruction instruction) {
        int op = instruction.rs();

        if ((op & 0b10000) == 0b10000) {
            GTEInterpreter.execute(mips, new GTEInstruction(instruction.getData() & 0x1FFFFFF));
            return;
        }
        
        switch (op) {
        case 0b0_0000:
            mips.writeGPRDelayed(instruction.rt(), GTEInterpreter.readRegister(mips, instruction.rd()));
            mips.PC += 4;
            return;
        case 0b0_0010:
            mips.writeGPRDelayed(instruction.rt(), GTEInterpreter.readRegister(mips, instruction.rd() + 32));
            mips.PC += 4;
            return;
        case 0b0_0100:
            GTEInterpreter.writeRegister(mips, instruction.rd(), mips.gpr[instruction.rt()]);
            mips.PC += 4;
            return;
        case 0b0_0110:
            GTEInterpreter.writeRegister(mips, instruction.rd() + 32, mips.gpr[instruction.rt()]);
            mips.PC += 4;
            return;
        }
        System.out.println(String.format("Invalid cop2 opcode %02X PC %08X", op, mips.PC));
        System.exit(-1);
    }

    public static void interpretADDI(MIPS mips, Instruction instruction) {
        int imm = instruction.signedImmediate();
        int rt = instruction.rt();
        int rs = instruction.rs();
        int result = mips.gpr[rs] + imm;
        if (((mips.gpr[rs] ^ imm) & 0x80000000) == 0 && ((result ^ mips.gpr[rs]) & 0x80000000) == 0x80000000) {
            mips.triggerException(MIPS.Exception_ArithmeticOverflow);
            return;
        }

        mips.writeGPR(rt, result);
        mips.PC += 4;
    }

    public static void interpretADDIU(MIPS mips, Instruction instruction) {
        int imm = instruction.signedImmediate();
        int rt = instruction.rt();
        int rs = instruction.rs();
        mips.writeGPR(rt, mips.gpr[rs] + imm);
        mips.PC += 4;
    }

    public static void interpretSLTI(MIPS mips, Instruction instruction) {
        int imm = instruction.signedImmediate();
        int rt = instruction.rt();
        int rs = instruction.rs();
        boolean set = mips.gpr[rs] < imm;
        mips.writeGPR(rt, set ? 1 : 0);
        mips.PC += 4;
    }
    
    public static void interpretSLTIU(MIPS mips, Instruction instruction) {
        int imm = instruction.signedImmediate();
        int rt = instruction.rt();
        int rs = instruction.rs();
        
        mips.gpr[rt] = Integer.compareUnsigned(mips.gpr[rs], imm);
        if (mips.gpr[rt] < 0)
            mips.gpr[rt] = 1;
        else
            mips.gpr[rt] = 0;

        mips.PC += 4;
    }

    public static void interpretANDI(MIPS mips, Instruction instruction) {
        int rt = instruction.rt();
        int rs = instruction.rs();
        int imm = instruction.unsignedImmediate();
        mips.writeGPR(rt, mips.gpr[rs] & imm);
        mips.PC += 4;
    }
    
    public static void interpretORI(MIPS mips, Instruction instruction) {
        int rt = instruction.rt();
        int rs = instruction.rs();
        int imm = instruction.unsignedImmediate();
        mips.writeGPR(rt, mips.gpr[rs] | imm);
        mips.PC += 4;
    }

    public static void interpretXORI(MIPS mips, Instruction instruction) {
        int rt = instruction.rt();
        int rs = instruction.rs();
        int imm = instruction.unsignedImmediate();
        mips.writeGPR(rt, mips.gpr[rs] ^ imm);
        mips.PC += 4;
    }
    
    public static void interpretLUI(MIPS mips, Instruction instruction) {
        int rt = instruction.rt();
        int shift = instruction.unsignedImmediate() << 16;
        mips.writeGPR(rt, shift);
        mips.PC += 4;
    }

    public static void interpretLB(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;
        mips.writeGPRDelayed(rt, mips.readByte(address));
        mips.PC += 4;
    }

    public static void interpretLH(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 1) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        mips.writeGPRDelayed(rt, mips.readShort(address));
        mips.PC += 4;
    }

    public static void interpretLWL(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data, offset, result;
        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        offset = ((mips.gpr[rs] + imm) & 3) * 8;
        address = (mips.gpr[rs] + imm) & ~3;
        data = mips.readInt(address);

        int old = mips.gpr[rt];
        if (mips.loadDelayCounter == 1 && mips.loadDelayReg[0].index == rt)
            old = mips.loadDelayReg[0].value;

        result = (old & (0x00FFFFFF >>> offset)) | (data << (24 - offset));
        mips.writeGPRDelayed(rt, result);
        mips.PC += 4;
    }

    public static void interpretLW(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        mips.writeGPRDelayed(rt, mips.readInt(address));
        mips.PC += 4;
    }

    public static void interpretLBU(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address  = mips.gpr[rs] + imm;
        mips.writeGPRDelayed(rt, mips.readByteUnsigned(address));
        mips.PC += 4;
    }

    public static void interpretLHU(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 1) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        mips.writeGPRDelayed(rt, mips.readShortUnsigned(address));
        mips.PC += 4;
    }

    public static void interpretLWR(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data, offset, result;

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        offset = ((mips.gpr[rs] + imm) & 3) * 8;
        address = (mips.gpr[rs] + imm) & ~3;
        data = mips.readInt(address);

        int old = mips.gpr[rt];
        if (mips.loadDelayCounter == 1 && mips.loadDelayReg[0].index == rt)
            old = mips.loadDelayReg[0].value;

        result = (old & (0xFFFFFF00 << (24 - offset))) | (data >>> offset);
        mips.writeGPRDelayed(rt, result);
        mips.PC += 4;
    }

    public static void interpretSB(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data;

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;
        data = mips.gpr[rt];
        mips.writeByte(address, (byte)data);
        mips.PC += 4;
    }

    public static void interpretSH(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 1) != 0) {
            mips.triggerException(MIPS.Exception_AdES);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        data = mips.gpr[rt];
        mips.writeShort(address, (short)data);
        mips.PC += 4;
    }

    public static void interpretSWL(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data, offset, result;
        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        offset = ((mips.gpr[rs] + imm) & 3) * 8;
        address = (mips.gpr[rs] + imm) & ~3;
        data = mips.readInt(address);        
        result = (mips.gpr[rt] >>> (24 - offset)) | (data & (0xFFFFFF00 << offset));
        mips.writeInt(address, result);
        mips.PC += 4;
    }


    public static void interpretSW(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdES);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        data = mips.gpr[rt];
        mips.writeInt(address, data);
        mips.PC += 4;
    }

    public static void interpretSWR(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data, offset, result;
        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        offset = ((mips.gpr[rs] + imm) & 3) * 8;
        address = (mips.gpr[rs] + imm) & ~3;
        data = mips.readInt(address);        
        result = (mips.gpr[rt] << offset) | (data & (0x00FFFFFF >> (24 - offset)));
        mips.writeInt(address, result);
        mips.PC += 4;
    }

    private static void interpretLWC2(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdEL);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        mips.gteReg[rt] = mips.readInt(address);
        mips.PC += 4;
    }

    public static void interpretSWC2(MIPS mips, Instruction instruction) {
        int rt, rs, imm, address, data;

        rt = instruction.rt();
        rs = instruction.rs();
        imm = instruction.signedImmediate();
        address = mips.gpr[rs] + imm;

        if ((address & 3) != 0) {
            mips.triggerException(MIPS.Exception_AdES);
            return;
        }

        if ((mips.cop0reg[12].getValue() & 0x10000) != 0) {
            mips.PC += 4;
            return;
        }

        data = mips.gteReg[rt];
        mips.writeInt(address, data);
        mips.PC += 4;
    }

    private static Execution getExecutor(Instruction instruction) {
        return opcodeExecutor[instruction.opcode()];
    }

    private static Execution getFunctorExecutor(Instruction instruction) {
        return functorExecutor[instruction.functor()];
    }
    
    public static void execute(MIPS mips, Instruction instruction) {
        Execution exec = getExecutor(instruction);
        if (exec == null) {
            throw new RuntimeException(String.format("Unknown opcode %02X PC %08X", instruction.opcode(), mips.PC));
        }
        exec.execute(mips, instruction);
    }
    static {
        for (int i = 0; i < 0x40; i++) {
            opcodeExecutor[i] = functorExecutor[i] = null;
        }

        functorExecutor[0x00] = Interpreter::interpretSLL;
        functorExecutor[0x02] = Interpreter::interpretSRL;
        functorExecutor[0x03] = Interpreter::interpretSRA;
        functorExecutor[0x04] = Interpreter::interpretSLLV;
        functorExecutor[0x06] = Interpreter::interpretSRLV;
        functorExecutor[0x07] = Interpreter::interpretSRAV;
        functorExecutor[0x08] = Interpreter::interpretJR;
        functorExecutor[0x09] = Interpreter::interpretJALR;
        functorExecutor[0x0C] = Interpreter::interpretSYSCALL;
        functorExecutor[0x0D] = Interpreter::interpretBREAK;
        functorExecutor[0x10] = Interpreter::interpretMFHI;
        functorExecutor[0x11] = Interpreter::interpretMTHI;
        functorExecutor[0x12] = Interpreter::interpretMFLO;
        functorExecutor[0x13] = Interpreter::interpretMTLO;
        functorExecutor[0x18] = Interpreter::interpretMULT;
        functorExecutor[0x19] = Interpreter::interpretMULTU;
        functorExecutor[0x1A] = Interpreter::interpretDIV;
        functorExecutor[0x1B] = Interpreter::interpretDIVU;
        functorExecutor[0x20] = Interpreter::interpretADD;
        functorExecutor[0x21] = Interpreter::interpretADDU;
        functorExecutor[0x22] = Interpreter::interpretSUB;
        functorExecutor[0x23] = Interpreter::interpretSUBU;
        functorExecutor[0x24] = Interpreter::interpretAND;
        functorExecutor[0x25] = Interpreter::interpretOR;
        functorExecutor[0x26] = Interpreter::interpretXOR;
        functorExecutor[0x27] = Interpreter::interpretNOR;
        functorExecutor[0x2A] = Interpreter::interpretSLT;
        functorExecutor[0x2B] = Interpreter::interpretSLTU;
        opcodeExecutor[0x00] = Interpreter::interpretFUNCTOR;
        opcodeExecutor[0x01] = Interpreter::interpretBLTZ_BGEZ;
        opcodeExecutor[0x02] = Interpreter::interpretJ;
        opcodeExecutor[0x03] = Interpreter::interpretJAL;
        opcodeExecutor[0x04] = Interpreter::interpretBEQ;
        opcodeExecutor[0x05] = Interpreter::interpretBNE;
        opcodeExecutor[0x06] = Interpreter::interpretBLEZ;
        opcodeExecutor[0x07] = Interpreter::interpretBGTZ;
        opcodeExecutor[0x10] = Interpreter::interpretCOP0;
        opcodeExecutor[0x12] = Interpreter::interpretCOP2;
        opcodeExecutor[0x08] = Interpreter::interpretADDI;
        opcodeExecutor[0x09] = Interpreter::interpretADDIU;
        opcodeExecutor[0x0A] = Interpreter::interpretSLTI;
        opcodeExecutor[0x0B] = Interpreter::interpretSLTIU;
        opcodeExecutor[0x0C] = Interpreter::interpretANDI;
        opcodeExecutor[0x0D] = Interpreter::interpretORI;
        opcodeExecutor[0x0E] = Interpreter::interpretXORI;
        opcodeExecutor[0x0F] = Interpreter::interpretLUI;
        opcodeExecutor[0x20] = Interpreter::interpretLB;
        opcodeExecutor[0x21] = Interpreter::interpretLH;
        opcodeExecutor[0x22] = Interpreter::interpretLWL;
        opcodeExecutor[0x23] = Interpreter::interpretLW;
        opcodeExecutor[0x24] = Interpreter::interpretLBU;
        opcodeExecutor[0x25] = Interpreter::interpretLHU;
        opcodeExecutor[0x26] = Interpreter::interpretLWR;
        opcodeExecutor[0x28] = Interpreter::interpretSB;
        opcodeExecutor[0x29] = Interpreter::interpretSH;
        opcodeExecutor[0x2A] = Interpreter::interpretSWL;
        opcodeExecutor[0x2B] = Interpreter::interpretSW;
        opcodeExecutor[0x2E] = Interpreter::interpretSWR;
        opcodeExecutor[0x32] = Interpreter::interpretLWC2;
        opcodeExecutor[0x3A] = Interpreter::interpretSWC2;
    }
}