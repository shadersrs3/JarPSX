package jarpsx.backend.component;

import jarpsx.backend.Emulator;

public class InterruptController {
    private int status;
    private int mask;
    private boolean irqSet;
    private Emulator emulator;

    public static final int IRQ_VBLANK = 0;
    public static final int IRQ_GPU = 1;
    public static final int IRQ_CDROM = 2;
    public static final int IRQ_DMA = 3;
    public static final int IRQ_TMR0 = 4;
    public static final int IRQ_TMR1 = 5;
    public static final int IRQ_TMR2 = 6;
    public static final int IRQ_CTRL = 7;
    public static final int IRQ_SIO = 8;
    public static final int IRQ_SPU = 9;

    public InterruptController(Emulator emulator) {
        this.emulator = emulator;
        mask = 0;
        status = 0;
        irqSet = false;
    }
    
    public int readStatus() {
        int status = this.status;
        return this.status;
    }
    
    public void writeStatus(int value) {
        status = value;
    }

    public void service(int irq) {
        status |= 1 << irq;
        acknowledge();
    }

    public void acknowledge() {
        if ((status & mask) != 0) {
            emulator.mips.cop0reg[13].value |= 1 << 10;
            irqSet = true;
        } else {
            emulator.mips.cop0reg[13].value &= ~(1 << 10);
        }
    }
    
    public void setIrq(boolean state) {
        irqSet = state;
    }
    
    public boolean isIrqSet() {
        return irqSet;
    }

    public int readMask() {
        return mask;
    }
    
    public void writeMask(int mask) {
        if ((emulator.interruptController.status & mask) == 0)
            emulator.mips.cop0reg[13].value &= ~(1 << 10);
        mask |= 1 << 9;
        this.mask = mask;
    }
}