package jarpsx.backend.component;

import jarpsx.backend.Emulator;
import jarpsx.backend.component.InterruptController;
import jarpsx.backend.component.PSXController;

public class Peripheral {
    private Emulator emulator;
    private int[] buffer;
    private int stat;
    private int ctrl;
    private int mode;
    private int baud;
    private int baudRateTimer;
    private int cpuCycles;
    private int[] rxData;
    private int rxIndex;
    private int txData;
    private int state;
    private int halfwordsReceived = 0;
    int interruptModeCounter;
    
    public Peripheral(Emulator emulator) {
        this.emulator = emulator;
        rxData = new int[32];
        stat = 3;
    }

    public int getReloadFactor() {        
        return switch (mode & 3) {
            case 0 -> 1;
            case 1 -> 1;
            case 2 -> 16;
            case 3 -> 64;
            default -> 0; // dead code bullshit
        };
    }

    public void writeTxData(int data) {
        prevTxData = txData;
        txData = data;
    }

    private int currentResponseCounter = 0;
    private int prevTxData;

    public int readRxData() {
        int data = rxData[rxIndex];
        int port = (ctrl & (1 << 13)) != 0 ? 2 : 1;
        int interruptModeBytes;
        
        switch ((mode >>> 8) & 3) {
        case 0: interruptModeBytes = 1; break;
        case 1: interruptModeBytes = 2; break;
        case 2: interruptModeBytes = 4; break;
        case 3: interruptModeBytes = 8; break;
        default:
            interruptModeBytes = 0;
        };
        
        if (++interruptModeCounter == interruptModeBytes) {
            interruptModeCounter = 0;
            emulator.interruptController.service(InterruptController.IRQ_SIO0);
        }

        if (port == 2)
            return 0;

        if (prevTxData == 0x42 && txData == 0) {
            return 0x5A;
        }
        
        if (halfwordsReceived != 0) {
            halfwordsReceived--;
            currentResponseCounter++;
            switch (currentResponseCounter) {
            case 1: // lower byte
                return emulator.psxController.getButtonState(0);
            case 2: // upper byte
                return emulator.psxController.getButtonState(1);
            case 3:
                return 0x80;
            case 4:
                return 0x80;
            case 5:
                return 0x80;
            case 6:
                return 0x80;
            // etc..
            }
        }

        switch (txData) {
        case 1: // always a psx pad controller
            break;
        case 0x42:
            halfwordsReceived = 2 + 2 + 2;
            currentResponseCounter = 0;
            return 0x73;
        }
        return data;
    }

    public int readStat() {
        return stat | baudRateTimer << 11;
    }

    public void writeMode(int data) {
        mode = data;
    }

    public int readMode() {
        return mode;
    }

    public void writeCtrl(int data) {
        if ((data & (1 << 4)) != 0)
            stat &= ~0x238;
        ctrl = data;
    }

    public int readCtrl() {
        return ctrl;
    }

    public int readBaudRate() {
        return baud;
    }

    public void writeBaudRate(int value) {
        baud = value;
    }

    public void step(int cycles) {
        baudRateTimer -= cycles;
        if (baudRateTimer <= 0) {
            baudRateTimer = getReloadFactor() * readBaudRate() / 2;
        }
    }
}