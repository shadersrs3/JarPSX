package jarpsx.backend.component;

import jarpsx.backend.Emulator;
import jarpsx.backend.component.InterruptController;

public class Timer {
    public class TimerData {
        private int currentValue;
        private int mode;
        private int targetValue;
        private int counterIndex;

        public TimerData(int counter) {
            counterIndex = counter;
            targetValue = 0;
            mode = 0;
            currentValue = 0;
        }

        public int readValue() {
            return (currentValue / 8) & 0xFFFF;
        }

        public int readMode() {
            int value = mode;
            mode &= ~((1 << 11) | (1 << 12));
            return value;
        }
        
        public int readTarget() {
            return targetValue;
        }

        public void writeValue(int value) {
            currentValue = value & 0xFFFF;
        }

        public void writeMode(int mode) {
            this.mode = mode & 0xFFFF;
        }

        public void writeTarget(int target) {
            targetValue = target & 0xFFFF;
        }

        public void triggerInterrupt() {
            mode &= ~(1 << 10);
            if ((mode & (1 << 6)) == 0) {
                System.out.printf("irq once interrupt");
                System.exit(1);
            }

            emulator.interruptController.service(InterruptController.IRQ_TMR0 + counterIndex);
        }

        public void step() {
            int value = currentValue;
            boolean resetCounterTarget = (mode & (1 << 3)) != 0;
            boolean irqTarget = (mode & (1 << 4)) != 0;
            boolean irqFFFF = (mode & (1 << 5)) != 0;
            boolean syncEnable = (mode & 1) != 0;
            int clockSource = (1 >>> 8) & 3;

            if (syncEnable) {
                switch (counterIndex) {
                case 2:
                    if (mode != 0) {
                        System.out.printf("Hello world %x", mode);
                        System.exit(1);
                    }
                }
            }

            this.currentValue = (this.currentValue + 1);
            if (value == 0xFFFF) {
                if (irqFFFF) {
                    triggerInterrupt();
                }
                
                mode |= 1 << 12;
            }

            if (value == targetValue) {
                if (resetCounterTarget) {
                    currentValue = 0;
                }

                if (irqTarget) {
                    triggerInterrupt();
                }

                mode |= 1 << 11;
            }
        }
    }

    private Emulator emulator;
    private TimerData[] timerData;

    public Timer(Emulator emulator) {
        this.emulator = emulator;
        timerData = new TimerData[3];
        for (int i = 0; i < 3; i++)
            timerData[i] = new TimerData(i);
    }
    
    public TimerData getTimer(int index) {
        return timerData[index];
    }
    
    public void step() {
        for (int i = 0; i < 3; i++)
            timerData[i].step();
    }
}