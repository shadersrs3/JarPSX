package jarpsx.backend.component;

import jarpsx.backend.Emulator;
import jarpsx.backend.component.InterruptController;

public class DMA {
    public class Channel {
        private int baseAddress;
        private int blockControl;
        private int channelControl;
        public Channel() {
            baseAddress = 0;
            blockControl = 0;
            channelControl = 0;
        }
        
        public int getBaseAddress() {
            return baseAddress;
        }
        
        public void setBaseAddress(int baseAddress) {
            this.baseAddress = baseAddress & 0xFFFFFF;
        }
        
        public int getBlockControl() {
            return blockControl;
        }
        
        public void setBlockControl(int blockControl) {
            this.blockControl = blockControl;
        }
        
        public int getChannelControl() {
            return channelControl;
        }
        
        public void setChannelControl(int channelControl) {
            this.channelControl = channelControl;
        }
        
    }
    
    public static final int MDECin = 0;
    public static final int MDECout = 1;
    public static final int GPU = 2;
    public static final int CDROM = 3;
    public static final int SPU = 4;
    public static final int OTC = 6;

    private static final int FROM_MAIN_RAM = 1;    
    private static final int TO_MAIN_RAM = 0;

    private Emulator emulator;
    private int DPCR, DICR;
    private Channel[] channel;
    public DMA(Emulator emulator) {
        this.emulator = emulator;
        DPCR = DICR = 0;
        channel = new Channel[7];
        for (int i = 0; i < 7; i++) 
            channel[i] = new Channel();
    }

    public void setDPCR(int value) {
        DPCR = value;
    }
    
    public void setDICR(int value) {
        int interruptFlags = ~(value & 0x7F000000);
        value &= interruptFlags;
        DICR = value;
    }
    
    public int getDPCR() {
        return DPCR;
    }
    
    public int getDICR() {
        return DICR;
    }

    public Channel getChannel(int index) {
        return channel[index];
    }

    public void runChannel(int index) {
        Channel channel = this.channel[index];
        int syncMode = (channel.getChannelControl() >>> 9) & 3;

        channel.channelControl &= ~(1 << 28);
        switch (syncMode) {
        case 0: { // Start immediately for transfering blocks
            int transferDirection = channel.getChannelControl() & 1;
            int transferStep = ((channel.getChannelControl() >> 1) & 1) != 0 ? -4 : 4;
            int words = channel.getBlockControl();
            if (words < 0 || words > 0xFFFF) {
                words &= 0xFFFF;
            }

            if (words == 0)
                words = 0x10000;

            int baseAddress = channel.getBaseAddress();
            for (int i = 0; i < words; i++) {
                int data;
                switch (transferDirection) {
                case TO_MAIN_RAM:
                    switch (index) {
                    case OTC:
                        if (i == words - 1) {
                            data = 0xFFFFFF; // end marker
                        } else {
                            data = (baseAddress - 4) & 0xFFFFFC;
                        }

                        emulator.memory.writeInt(baseAddress, data);
                        break;
                    case CDROM:
                        emulator.memory.writeInt(baseAddress, emulator.cdrom.readDataWord());
                        break;
                    default:
                        System.out.printf("Unimplemented transfer blocks to DMA requests channel %d", index);
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.printf("Unimplemented transfer direction %d channel %d", transferDirection, index);
                    System.exit(1);
                }
                baseAddress = (baseAddress + transferStep) & 0xFFFFFC;
            }
            break;
        }
        case 1: { // Sync blocks to DMA requests
            int transferDirection = channel.getChannelControl() & 1;
            int transferStep = ((channel.getChannelControl() >> 1) & 1) != 0 ? -4 : 4;
            int bs = channel.getBlockControl() & 0xFFFF;
            int ba = (channel.getBlockControl() >>> 16) & 0xFFFF;

            if (bs < 0 || bs > 0xFFFF)
                bs &= 0xFFFF;
            if (ba < 0 || ba > 0xFFFF)
                ba &= 0xFFFF;
            if (bs == 0)
                bs = 0x10000;
            if (ba == 0)
                ba = 0x10000;

            long words = (long)bs * (long)ba;
            int baseAddress = channel.getBaseAddress();
            for (long i = 0; i < words; i++) {
                int data;
                switch (transferDirection) {
                case TO_MAIN_RAM:
                    switch (index) {
                    case 2: // GPU (NEED TO IMPLEMENT)
                        break;
                    default:
                        System.out.printf("Unimplemented Sync blocks (to Main Ram) DMA requests channel %d", index);
                        System.exit(1);
                    }
                    break;
                case FROM_MAIN_RAM:
                    switch (index) {
                    case 2: // GPU (NEED TO IMPLEMENT)
                        break;
                    default:
                        System.out.printf("Unimplemented Sync blocks (from Main Ram) DMA requests channel %d", index);
                        System.exit(1);
                    }
                    break;
                default:
                    System.out.printf("Unimplemented Sync blocks transfer direction %d channel %d", transferDirection, index);
                    System.exit(1);
                }
                baseAddress = (baseAddress + transferStep) & 0xFFFFFC;
            }
            break;
        }
        case 2: { // Linked list
            int forceQuitDmaTimeout = 5000000;
            int baseAddress = channel.getBaseAddress();
            while (forceQuitDmaTimeout-- > 0) {
                int nextAddress = emulator.memory.readInt(baseAddress);
                int commandSize = nextAddress >>> 24;

                // will send GP0 packets here later
                for (int _baseAddress = (baseAddress + 4) & 0xFFFFFC, i = _baseAddress; i < _baseAddress+commandSize*4; i += 4) {
                }

                if ((nextAddress & 0x800000) != 0)
                    break;
                baseAddress = (nextAddress) & 0xFFFFFF;
            }
            break;
        }
        default:
            System.out.printf("Unimplemented Sync Mode %d", syncMode);
            System.exit(1);
        }

        channel.channelControl &= ~(1 << 24);
        if ((DICR & (1 << 23)) != 0 && (DICR & 0x7F0000) != 0) {
            DICR |= index << 24;
            DICR |= 1 << 31;
            emulator.interruptController.service(InterruptController.IRQ_DMA);
        }
    }
}