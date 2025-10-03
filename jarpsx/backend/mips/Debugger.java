package jarpsx.backend.mips;

import java.util.List;
import java.util.Iterator;
import java.util.Stack;

import jarpsx.backend.mips.MIPS;
import jarpsx.backend.mips.Instruction;
import jarpsx.backend.Emulator;

public class Debugger {
    private class Breakpoint {
        public static final int ATTRIBUTE_ENABLE = (1 << 0);
        public static final int ATTRIBUTE_WITH_VALUE = (1 << 1);
        public static final int ATTRIBUTE_WRITE = (1 << 2);
        public static final int ATTRIBUTE_READ = (1 << 3);
        public static final int ATTRIBUTE_RW = (1 << 4);
        private int address;
        private int value;
        private int attributes;

        Breakpoint(int address, int value, int attributes) {
            this.address = address;
            this.value = value;
            this.attributes = attributes;
        }

        public int getAttributes() {
            return attributes;
        }

        public void setAttributes(int attributes) {
            if ((attributes & (ATTRIBUTE_READ|ATTRIBUTE_WRITE)) == (ATTRIBUTE_READ|ATTRIBUTE_WRITE))
                attributes = (attributes & ~(ATTRIBUTE_READ|ATTRIBUTE_WRITE)) | ATTRIBUTE_RW;
            this.attributes = attributes;
        }

        public boolean reachedTarget(int address) {
            return this.address == address;
        }

        public boolean reachedValue(int value) {
            return (getAttributes() & ATTRIBUTE_WITH_VALUE) != 0 && this.value == value;
        }
    }

    Emulator emulator;
    List<Breakpoint> fetchBreakpoints;
    List<Breakpoint> memoryBreakpoints;
    Stack<Integer> addressCallStack;

    void pushAddressCallStack(int address) {
        addressCallStack.push(address);
    }
    
    void popAddressCallStack() {
        if (!addressCallStack.empty())
            addressCallStack.pop();
    }
    
    public Debugger(Emulator emulator) {
        this.emulator = emulator;
    }
    
    public void addFetchBreakpoint(Breakpoint bp) {
        fetchBreakpoints.add(bp);
    }
    
    public void removeFetchBreakpoint(Breakpoint bp) {
        fetchBreakpoints.remove(bp);
    }
    
    public void addMemoryBreakpoint(Breakpoint bp) {
        memoryBreakpoints.add(bp);
    }
    
    public void removeMemoryBreakpoint(Breakpoint bp) {
        memoryBreakpoints.remove(bp);
    }

    public boolean checkForBreakpoint() {
        Iterator<Breakpoint> bpIt = fetchBreakpoints.iterator();
        int data = 0;
        int pc = emulator.mips.PC;
        try {
            data = emulator.memory.readInt(pc);
        } catch (Exception e) {
            data = -1;
        }

        Instruction instruction = new Instruction(data);
        while (bpIt.hasNext()) {
            Breakpoint bp = bpIt.next();
            if ((bp.getAttributes() & Breakpoint.ATTRIBUTE_ENABLE) != 0 && bp.reachedTarget(pc)) {
                return true;
            }
        }

        bpIt = memoryBreakpoints.iterator();
        int address = 0xFFFFFFFF;
        int value = 0xFFFFFFFF;
        int attributesMemoryType = 0;

        while (bpIt.hasNext()) {
            Breakpoint bp = bpIt.next();
            if (bp.reachedTarget(address) && attributesMemoryType != 0) {
                if ((bp.getAttributes() & Breakpoint.ATTRIBUTE_WITH_VALUE) == 0)
                    return true;
                else {
                    if (bp.reachedValue(value))
                        return true;
                }
            }
        }
        return false;
    }
}