package jarpsx.backend.mips;

public class Disassembler {
    private enum RegisterSourceType {
        RD,
        RT,
        RS,
    }
    
    private enum InstructionType {
        InvalidType,
        RegisterShiftType,
        ImmediateType,
        RegisterType,
        JumpTargetType,
        LoadStoreType,
        MultiplyDivideType,
    }

    public static String[] aliasedRegisterNames = {
        "zero", "at", "v0", "v1",
        "a0", "a1", "a2", "a3",
        "t0", "t1", "t2", "t3",
        "t4", "t5", "t6", "t7",
        "s0", "s1", "s2", "s3",
        "s4", "s5", "s6", "s7",
        "t8", "t9", "k0", "k1",
        "gp", "sp", "fp", "ra",
        "pc", "hi", "lo",
    };

    public static String[] registerNames = {
        "r0",  "r1",  "r2",  "r3",
        "r4",  "r5",  "r6",  "r7",
        "r8",  "r9",  "r10", "r11",
        "r12", "r13", "r14", "r15",
        "r16", "r17", "r18", "r19",
        "r20", "r21", "r22", "r23",
        "r24", "r25", "r26", "r27",
        "r28", "r29", "r30", "r31",
        "pc", "hi",  "lo",
    };

    public static String[] cop0RegisterNames = {
        "cop0r0", "cop0r1", "cop0r2", "cop0_bpc",
        "cop0r4", "cop0r5_bda", "cop0r6_tar", "cop0r7_dcic",
        "cop0r8_bada", "cop0r9_bdam", "cop0r10", "copr11_bpcm",
        "cop0r12_sr", "cop0r13_cause", "cop0r14_epc", "copr15_prid",
        "cop0r16", "cop0r17", "cop0r18", "cop0r19",
        "cop0r20", "cop0r21", "cop0r22", "cop0r23",
        "cop0r24", "cop0r25", "cop0r26", "cop0r27",
        "cop0r28", "cop0r29", "cop0r30", "cop0r31",
    };

    public static String[] cop2RegisterNames = {
        "vxy0", "vz0", "vxy1", "vz1", "vxy2", "vz2", "rgbc", "otz",
        "ir0", "ir1", "ir2", "ir3", "sxy0", "sxy1", "sxy2", "sxyp",
        "sz0", "sz1", "sz2", "sz3", "rgb0", "rgb1", "rgb2", "res1",
        "mac0", "mac1", "mac2", "mac3", "irgb", "orgb", "lzcs", "lzcr",
    };

    private static String disassembleShiftType(boolean useAliasedRegisters, String name, int destination, int source, int shift, boolean variableType) {
        String sourceString, destinationString;
        String immediateLiteralString;

        if (destination == 0)
            return "nop";

        sourceString = (useAliasedRegisters ? aliasedRegisterNames[source] : registerNames[source]);
        destinationString = (useAliasedRegisters ? aliasedRegisterNames[destination] : registerNames[destination]);

        if (variableType) {
            immediateLiteralString = (useAliasedRegisters ? aliasedRegisterNames[shift] : registerNames[shift]);
        } else {
            immediateLiteralString = String.format("0x%02x", shift);
        }
        
        return name + " " + destinationString + ", " + sourceString + ", " + immediateLiteralString;
    }

    private static String disassembleImmediateType(boolean useAliasedRegisters, String name, int destination, int source, int immediate) {
        String sourceString, destinationString;
        String immediateLiteralString;

        if (destination == 0)
            return "nop";

        if (destination == -1) {
            return name + " " + (useAliasedRegisters ? aliasedRegisterNames[source] : registerNames[source]) + ", " + String.format("0x%04X", immediate);
        }
        
        sourceString = (useAliasedRegisters ? aliasedRegisterNames[source] : registerNames[source]);
        destinationString = (useAliasedRegisters ? aliasedRegisterNames[destination] : registerNames[destination]);
        immediateLiteralString = disassembleArithmeticImmediate(immediate);
        return name + " " + destinationString + ", " + sourceString + ", " + immediateLiteralString;
    }

    private static String disassembleMultiplyDivideType(boolean useAliasedRegisters, String name, int source, int source2) {
        String sourceString, source2String;
        sourceString = (useAliasedRegisters ? aliasedRegisterNames[source] : registerNames[source]);
        source2String = (useAliasedRegisters ? aliasedRegisterNames[source2] : registerNames[source2]);
        return name + " " + sourceString + ", " + source2String;
    }

    private static String disassembleRegisterType(boolean useAliasedRegisters, String name, int destination, int source, int source2) {
        String sourceString, source2String, destinationString;

        if (destination == 0)
            return "nop";

        sourceString = (useAliasedRegisters ? aliasedRegisterNames[source] : registerNames[source]);
        source2String = (useAliasedRegisters ? aliasedRegisterNames[source2] : registerNames[source2]);
        destinationString = (useAliasedRegisters ? aliasedRegisterNames[destination] : registerNames[destination]);
        return name + " " + destinationString + ", " + sourceString + ", " + source2String;
    }

    private static String disassembleArithmeticImmediate(int immediate) {
        if (immediate < 0)
            return String.format("-0x%x", -immediate);
        return String.format("0x%x", immediate);
    }

    private static String disassembleLoadStoreType(boolean useAliasedRegisters, String name, int rs, int rt, int immediate) {
        String _rs, _rt;
        String immediateLiteralString;
        _rs = (useAliasedRegisters ? aliasedRegisterNames[rs] : registerNames[rs]);
        _rt = (useAliasedRegisters ? aliasedRegisterNames[rt] : registerNames[rt]);
        immediateLiteralString = disassembleArithmeticImmediate(immediate);
        return String.format("%s %s, %s(%s)", name, _rt, immediateLiteralString, _rs);
    }

    private static String disassembleBranchType(boolean useAliasedRegisters, String name, int a, int b, int offset, int currentAddress) {
        String aReg, bReg;
        String calculatedAddress;

        aReg = (useAliasedRegisters ? aliasedRegisterNames[a] : registerNames[a]);
        calculatedAddress = String.format("0x%08X", currentAddress + (int)(short)offset * 4 + 4);
        if (b == -1) {
            return String.format("%s %s, %s", name, aReg, calculatedAddress);
        }

        bReg = (useAliasedRegisters ? aliasedRegisterNames[b] : registerNames[b]);
        return String.format("%s %s, %s, %s", name, aReg, bReg, calculatedAddress);
    }

    private static String disassembleJumpTargetType(String name, int currentAddress, int target) {
        return name + " " + String.format("0x%08X", (currentAddress & 0xF0000000) | (target << 2));
    }

    private static int getRegisterSourceIndex(RegisterSourceType type, int instruction) {
        switch (type) {
        case RegisterSourceType.RD: return (instruction >>> 11) & 0x1F;
        case RegisterSourceType.RT: return (instruction >>> 16) & 0x1F;
        case RegisterSourceType.RS: return (instruction >>> 21) & 0x1F;
        }
        return 0;
    }

    public static String disassemble(int instruction, int currentAddress, boolean useAliasedRegisters) {
        int opcode = instruction >>> 26;
        int func = instruction & 0x3F;
        InstructionType type = InstructionType.InvalidType;
        String name = "UNKNOWN";
        int immediate = 0;
        boolean variableShift = false;

        switch (opcode) {
        case 0x00: {
            switch (func) {
            case 0x00: name = "sll"; variableShift = false; type = InstructionType.RegisterShiftType; break;
            case 0x02: name = "srl"; variableShift = false; type = InstructionType.RegisterShiftType; break;
            case 0x03: name = "sra"; variableShift = false; type = InstructionType.RegisterShiftType; break;
            case 0x04: name = "sllv"; variableShift = true; type = InstructionType.RegisterShiftType; break;
            case 0x06: name = "srlv"; variableShift = true; type = InstructionType.RegisterShiftType; break;
            case 0x07: name = "srav"; variableShift = true; type = InstructionType.RegisterShiftType; break;
            case 0x08: return "jr " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)]);
            case 0x09: {
                int rd = getRegisterSourceIndex(RegisterSourceType.RD, instruction);
                String rdStr = useAliasedRegisters ? aliasedRegisterNames[rd] : registerNames[rd];
                if (rd == 31)
                    return "jalr " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)]);
                return "jalr " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)]) + ", " + rdStr;
            }
            case 0x0C: return "syscall";
            case 0x0D: return "break";
            case 0x10: return "mfhi " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RD, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RD, instruction)]);
            case 0x11: return "mthi " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)]);
            case 0x12: return "mflo " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RD, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RD, instruction)]);
            case 0x13: return "mtlo " + (useAliasedRegisters ? aliasedRegisterNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)] : registerNames[getRegisterSourceIndex(RegisterSourceType.RS, instruction)]);

            case 0x18: name = "mult"; type = InstructionType.MultiplyDivideType; break;
            case 0x19: name = "multu"; type = InstructionType.MultiplyDivideType; break;
            case 0x1A: name = "div"; type = InstructionType.MultiplyDivideType; break;
            case 0x1B: name = "divu"; type = InstructionType.MultiplyDivideType; break;
            case 0x20: name = "add"; type = InstructionType.RegisterType; break;
            case 0x21: name = "addu"; type = InstructionType.RegisterType; break;
            case 0x22: name = "sub"; type = InstructionType.RegisterType; break;
            case 0x23: name = "subu"; type = InstructionType.RegisterType; break;
            case 0x24: name = "and"; type = InstructionType.RegisterType; break;
            case 0x25: name = "or"; type = InstructionType.RegisterType; break;
            case 0x26: name = "xor"; type = InstructionType.RegisterType; break;
            case 0x27: name = "nor"; type = InstructionType.RegisterType; break;
            case 0x2A: name = "slt"; type = InstructionType.RegisterType; break;
            case 0x2B: name = "sltu"; type = InstructionType.RegisterType; break;
            }
            break;
        }
        case 0x01: {
            boolean link = ((instruction >>> 16) & 0x1E) == 0x10; // link is only from the first bit additions
            boolean ge = ((instruction >>> 16) & 0x1) == 1;

            name = "bltz";

            if (ge)
                name = "bgez";

            if (link)
                name += "al";
            
            return disassembleBranchType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RS, instruction), -1, instruction & 0xFFFF, currentAddress);
        }
        case 0x02:
            name = "j";
            type = InstructionType.JumpTargetType;
            break;
        case 0x03:
            name = "jal";
            type = InstructionType.JumpTargetType;
            break;

        // Branch two special cases for bne and beq
        case 0x04: return disassembleBranchType(useAliasedRegisters, "beq", getRegisterSourceIndex(RegisterSourceType.RS, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction), instruction & 0xFFFF, currentAddress);
        case 0x05: return disassembleBranchType(useAliasedRegisters, "bne", getRegisterSourceIndex(RegisterSourceType.RS, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction), instruction & 0xFFFF, currentAddress);
        case 0x06: return disassembleBranchType(useAliasedRegisters, "bltz", getRegisterSourceIndex(RegisterSourceType.RS, instruction), -1, instruction & 0xFFFF, currentAddress);
        case 0x07: return disassembleBranchType(useAliasedRegisters, "bgtz", getRegisterSourceIndex(RegisterSourceType.RS, instruction), -1, instruction & 0xFFFF, currentAddress);

        // Arithmetic & Logic (lui case is quite a special one)
        case 0x8: case 0x9: case 0xA: case 0xB:
        case 0xC: case 0xD: case 0xE:
            type = InstructionType.ImmediateType;
            if (opcode >= 0x8 && opcode <= 0xB) {
                immediate = (int)(short)(instruction & 0xFFFF);
            } else {
                immediate = instruction & 0xFFFF;
            }

            switch (opcode) {
            case 0x8: name = "addi"; break;
            case 0x9: name = "addiu"; break;
            case 0xA: name = "slti"; break;
            case 0xB: name = "sltiu"; break;
            case 0xC: name = "andi"; break;
            case 0xD: name = "ori"; break;
            case 0xE: name = "xori"; break;
            }
            break;
        case 0x0F: return disassembleImmediateType(useAliasedRegisters, "lui", -1, getRegisterSourceIndex(RegisterSourceType.RT, instruction), instruction & 0xFFFF);

        case 0x10: return "implement cop0 disassembly";
        case 0x12: return "implement cop2 disassembly";
        
        case 0x31:
        case 0x11: return "invalid cop1 instruction";

        // Stores, loads
        case 0x20: type = InstructionType.LoadStoreType; name = "lb"; break;
        case 0x21: type = InstructionType.LoadStoreType; name = "lh"; break;
        case 0x22: type = InstructionType.LoadStoreType; name = "lwl"; break;
        case 0x23: type = InstructionType.LoadStoreType; name = "lw"; break;
        case 0x24: type = InstructionType.LoadStoreType; name = "lbu"; break;
        case 0x25: type = InstructionType.LoadStoreType; name = "lhu"; break;
        case 0x26: type = InstructionType.LoadStoreType; name = "lwr"; break;
        case 0x28: type = InstructionType.LoadStoreType; name = "sb"; break;
        case 0x29: type = InstructionType.LoadStoreType; name = "sh"; break;
        case 0x2A: type = InstructionType.LoadStoreType; name = "swl"; break;
        case 0x2E: type = InstructionType.LoadStoreType; name = "swr"; break;
        case 0x2B: type = InstructionType.LoadStoreType; name = "sw"; break;
        case 0x32: type = InstructionType.LoadStoreType; name = "lwc2"; break;
        case 0x3A: type = InstructionType.LoadStoreType; name = "swc2"; break;
        }


        switch (type) {
        case InstructionType.ImmediateType:
            return disassembleImmediateType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RT, instruction), getRegisterSourceIndex(RegisterSourceType.RS, instruction), immediate);
        case InstructionType.RegisterShiftType:
            if (variableShift)
                return disassembleShiftType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RD, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction), getRegisterSourceIndex(RegisterSourceType.RS, instruction), true);
            return disassembleShiftType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RD, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction), (instruction >>> 6) & 0x1F, false);
        case InstructionType.JumpTargetType:
            return disassembleJumpTargetType(name, currentAddress, instruction & 0x3FFFFFF);
        case InstructionType.LoadStoreType:
            return disassembleLoadStoreType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RS, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction), (int)(short)(instruction & 0xFFFF));
        case InstructionType.RegisterType:
            return disassembleRegisterType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RD, instruction), getRegisterSourceIndex(RegisterSourceType.RS, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction));
        case InstructionType.MultiplyDivideType:
            return disassembleMultiplyDivideType(useAliasedRegisters, name, getRegisterSourceIndex(RegisterSourceType.RS, instruction), getRegisterSourceIndex(RegisterSourceType.RT, instruction));
        }
        
        return String.format("UNKNOWN INSTRUCTION 0x%08X", instruction, opcode);
    }
}