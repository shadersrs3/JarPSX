package jarpsx.backend.component;

import jarpsx.backend.Emulator;
import jarpsx.backend.component.InterruptController;

public class Peripheral {
    private Emulator emulator;
    private int SIO0_STAT;
    private int packetsSent = 0;
    private int SIO0_CTRL;

    private int test = 0;

    private int[] buffer;
    public Peripheral(Emulator emulator) {
        this.emulator = emulator;
        SIO0_STAT = 0;
        SIO0_CTRL = 0;
        test = 0;
        buffer = new int[16];
        buffer[0] = 0x01;
        buffer[1] = 0x42;
        buffer[2] = 0x5A;
    }

    public void writeSioTxData(int index, int data) {
        // System.out.printf("Write TX data %04X\n", data);
        SIO0_STAT |= 1 << 1;
        if (--packetsSent <= 0) {
            SIO0_STAT |= 1 << 2;
        }
        
        if ((SIO0_CTRL & (1 << 10)) != 0) {
            SIO0_STAT |= 1 << 9;
            // emulator.interruptController.service(InterruptController.IRQ_SIO);
        }
    }

    public int readSioRxData(int index) {
        if (++test > 5) {
            test = 0;
            SIO0_STAT &= ~(1 << 1);
        }
        return buffer[test++ % 16];
    }
    
    public int readSioStat(int index) {
        return 0xFFFFFFFF;
    }

    public void writeSioMode(int index, int data) {
        // System.out.printf("Write mode %04X\n", data);
    }

    public int readSioMode(int index) {
        // System.out.printf("Read mode\n");
        return 0;
    }

    public void writeSioCtrl(int index, int data) {
        if ((data & (1 << 0)) != 0) {
            packetsSent = 5;
            SIO0_STAT |= 1 << 0;
        } else {
            SIO0_STAT &= ~(1 << 0);
        }
        
        if ((data & (7 << 10)) != 0) {
            SIO0_STAT |= 1 << 9;
            emulator.interruptController.service(InterruptController.IRQ_SIO);
        }

        SIO0_CTRL = data;
        // System.out.printf("Write control %04X\n", data);
    }

    public int readSioCtrl(int index) {
        // System.out.printf("Read control\n");
        return SIO0_CTRL;
    }

    public int readSioBaudRate(int index) {
        System.out.printf("Read baudrate\n");
        return 0;
    }

    public void writeSioBaudRate(int index, int value) {
        // System.out.printf("Write baudrate %04X\n", value);
    }
}