package jarpsx.backend.component;

import jarpsx.backend.Emulator;
import jarpsx.backend.IDisk;

class Fifo {
    public class Data {
        public int data;
    }

    private Data[] fifo;
    private int queueSize;
    private int front, back;
    private int currentSize;
    private String name;

    public Fifo(String name, int queueSize) {
        this.name = name;
        this.queueSize = queueSize;
        fifo = new Data[queueSize];
        for (int i = 0; i < queueSize; i++) {
            fifo[i] = new Data();
            fifo[i].data = 0;
        }

        currentSize = front = back = 0;
    }

    public Data enqueue(int data) {
        if (currentSize > queueSize) {
            currentSize = queueSize;
        } else
            currentSize++;

        Data _fifo = fifo[back];
        fifo[back].data = data;
        back = (back + 1) % queueSize;
        return _fifo;
    }
    
    public Data fetch() {
        int front = this.front;
        this.front = (this.front + 1) % queueSize;
        if (currentSize > 0)
            currentSize--;
        return fifo[front];
    }
    
    public void pop() {
        this.front = (this.front + 1) % queueSize;
        if (currentSize > 0)
            currentSize--;
    }
    
    public Data peek() {
        return fifo[front];
    }

    public Data peekSafe() {
        if (empty())
            return null;
        return fifo[front];
    }
    
    public void reset() {
        currentSize = 0;
    }
    
    public boolean empty() {
        return currentSize == 0;
    }
    
    public boolean full() {
        return currentSize == queueSize;
    }
}

public class CDROM {        
    public static final int Int_NoIntr = 0;
    public static final int Int_DataReady = 1;
    public static final int Int_Complete = 2;
    public static final int Int_Acknowledge = 3;
    public static final int Int_DataEnd = 4;
    public static final int Int_DiskError = 5;

    private static final int StatusCode_PlayCDDA = 1 << 7;
    private static final int StatusCode_Seek = 1 << 6;
    private static final int StatusCode_Read = 1 << 5;
    private static final int StatusCode_ShellOpen = 1 << 4;
    private static final int StatusCode_IdError = 1 << 3;
    private static final int StatusCode_SeekError = 1 << 2;
    private static final int StatusCode_Motor = 1 << 1;
    private static final int StatusCode_Error = 1 << 0;

    private static final int[] NoDiskData = { 0x08, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    private static final int[] LicensedMode2Data = { 0x02, 0x00, 0x20, 0x00, (int)'S', (int)'C', (int)'E', (int)'A' };

    private static final int NopAverage = 0xC4E1;
    private static final int InitAverage = 0x13CCE;
    private static final int ReadSingleSpeed = 0x6E3F5;
    private static final int ReadDoubleSpeed = 0x6E3F5 / 2;
    // Second response
    private static final int GetIDAverage = 0x4A00;
    private static final int PauseSingleSpeed = 0x021181c;
    private static final int PauseDoubleSpeed = 0x010bd93;
    private static final int PausePaused = 0x0001df2;

    private static final int REQUEST_INT3 = 1;
    private static final int REQUEST_INT3_INT2 = 2;
    private static final int REQUEST_INT2 = 3;
    private static final int REQUEST_INT3_INT1 = 4;
    private static final int REQUEST_INT1 = 5;
    private static final int REQUEST_INT3_INT2_PAUSE = 6;
    private static final int REQUEST_INT3_YYMMDDVER = 7;
    private static final int REQUEST_INT3_INT5_GETID = 8;
    private static final int REQUEST_INT5_GETID = 9;
    private static final int REQUEST_INT2_GETID = 10;
    private static final int REQUEST_INT3_INT2_GETID = 11;
    private static final int REQUEST_INT3_INT2_SEEKL = 12;
    private static final int REQUEST_INT2_INIT = 13;
    private static final int REQUEST_INT2_PAUSE = 14;
    private static final int REQUEST_INT1_REQUEST = 15;
    private static final int REQUEST_INT3_GETTN = 16;
    private static final int REQUEST_INT3_GETTD = 17;

    private Emulator emulator;
    private int currentRegisterBank;
    private int HSTS;
    private int HCHPCTL;
    private int HINTSTS; // IRQ Flags are there 
    private int HINTMSK;
    private int sectorLba; // For Setloc
    private int mode; // For Setmode
    private int statusCode; // For commands
    private Fifo parameterFifo;
    private Fifo responseFifo;
    private Fifo commandFifo;
    private int delay;
    private int requestType;
    private int sectorOffset;
    private boolean sectorEnd;
    private int paused = 0;
    boolean dataReady;

    private int readStatusCode() {
        return statusCode | StatusCode_Motor;
    }

    public CDROM(Emulator emulator) {
        this.emulator = emulator;
        currentRegisterBank = 0;
        HSTS = 0;
        HCHPCTL = 0;
        HINTSTS = 0;
        HINTMSK = 0;
        sectorLba = 0;
        mode = 0;
        statusCode = 0;
        delay = 0;
        requestType = 0;
        sectorEnd = false;
        sectorOffset = 0;
        parameterFifo = new Fifo("Parameter FIFO", 16);
        responseFifo = new Fifo("Response FIFO", 16);
        commandFifo = new Fifo("Command FIFO", 32);
        dataReady = false;
    }

    private static int BCD(int value) {
        return (value >>> 4) * 10 + (value & 0xF);
    }

    public static int CdPosToInt(int min, int sec, int frame) {
        return ((min * 60 + sec) * 75 + frame) - 150;
    }

    public void write(int offset, int value) {
        if (offset == 0) {
            currentRegisterBank = value & 3;
            return;
        }

        switch (currentRegisterBank) {
        case 0:
            HSTS |= 1 << 7;
            switch (offset) {
            case 0: currentRegisterBank = value & 3; return;
            case 2: parameterFifo.enqueue(value); return;
            case 3: HCHPCTL = value; return;
            case 1: {
                System.out.printf("CMD %02x %d\n", value, requestType);
                switch (value) {
                case 0x1:
                    requestType = REQUEST_INT3;
                    setDelay(0x2012);
                    break;
                case 0x2: {
                    int mm = BCD(parameterFifo.fetch().data);
                    int ss = BCD(parameterFifo.fetch().data);
                    int sector = BCD(parameterFifo.fetch().data);
                    System.out.printf("SetLoc %d:%d:%d\n", mm, ss, sector);
                    sectorLba = CdPosToInt(mm, ss, sector);
                    sectorOffset = 0;
                    requestType = REQUEST_INT3;
                    setDelay(1000);
                    break;
                }
                case 0xA: // Init
                    requestType = REQUEST_INT3_INT2;
                    mode = 0x20;
                    setDelay(InitAverage);
                    break;
                case 0xC:
                    requestType = REQUEST_INT3;
                    setDelay(1000);
                    break;
                case 0xE: {
                    int mode = parameterFifo.fetch().data;
                    this.mode = mode;
                    requestType = REQUEST_INT3;
                    setDelay(5000);
                    break;
                }
                case 0x6: // ReadN
                    requestType = REQUEST_INT3_INT1;
                    setDelay(50000);
                    break;
                case 0x9: // Pause
                    paused = 1;
                    // requestType = REQUEST_INT3_INT2_PAUSE;
                    // setDelay(30000);
                    break;
                case 0x13: // GetTN
                    requestType = REQUEST_INT3_GETTN;
                    setDelay(30000);
                    break;
                case 0x14:
                    requestType = REQUEST_INT3_GETTD;
                    setDelay(30000);
                    break;
                case 0x15: // SeekL
                    requestType = REQUEST_INT3_INT2_SEEKL;
                    setDelay(10000);
                    break;
                case 0x19: // Test
                {
                    int subcommand = parameterFifo.fetch().data;
                    switch (subcommand) {
                    case 0x20:
                        requestType = REQUEST_INT3_YYMMDDVER;
                        setDelay(1000);
                        break;
                    default:
                        System.out.printf("Unimplemented test subcommand %02X", subcommand);
                        System.exit(1);
                    }
                    break;
                }
                case 0x1A:
                    requestType = REQUEST_INT3_INT2_GETID;
                    setDelay(1000);
                    break;
                default:
                    System.out.printf("Command %02X unimplemented", value);
                    System.exit(1);
                }
                return;
            }
            }
            break;
        case 1:
            switch (offset) {
            case 2: HINTMSK = value; checkForInterrupts(); return;
            case 3:
                HINTSTS = HINTSTS & ~(value & 7);
                if ((value & 0x40) != 0) {
                    parameterFifo.reset();
                }
                checkForInterrupts();
                return;
            }
            break;
        case 2:
            switch (offset) {
            case 2:
            case 3:
                System.out.printf("UNIMPLEMENTED ATV%d (VOLUME %02X)\n", offset - 2, value);
                return;
            }
            break;
        case 3:
            switch (offset) {
            case 1:
            case 2:
                System.out.printf("UNIMPLEMENTED ATV%d (VOLUME %02X)\n", (offset-1)+2, value);
                return;
            case 3:
                System.out.printf("UNIMPLEMENTED ADPCTL\n");
                return;
            }
        }
        System.out.printf("UNIMPLEMENTED WRITE BANK %d OFFSET %d = %02X", currentRegisterBank, offset, value);
        System.exit(1);
    }
    
    public int read(int offset) {
        switch (offset) {
        case 0: {
            int result = HSTS | currentRegisterBank | (parameterFifo.empty() ? 1 << 3 : 0) | (parameterFifo.full() ? 0 : 1 << 4) | (responseFifo.empty() ? 0 : 1 << 5);
            return result;
        }
        case 1: {
            Fifo.Data response = responseFifo.fetch();
            return response.data;
        }
        case 3:
            if (currentRegisterBank == 1 || currentRegisterBank == 3)
                return HINTSTS;
            return HINTMSK;
        }

        System.out.printf("UNIMPLEMENTED READ BANK %d OFFSET %d", currentRegisterBank, offset);
        System.exit(1);
        return 0;
    }
    
    public void doIrq(int irq) {
        HINTSTS = irq;
        checkForInterrupts();
    }

    public void checkForInterrupts() {
        if ((HINTSTS & HINTMSK & 7) != 0) {
            emulator.interruptController.service(InterruptController.IRQ_CDROM);
        }
    }

    private static int littleEndian(byte[] data) {
        int _data = 0;
        for (int i = 0; i < data.length; i++) {
            _data |= ((int)data[i] & 0xFF) << (i * 8);
        }
        return _data;
    }

    public int readDataWord() {
        byte[] data = new byte[4];
        int sectorSizeMax = (mode & (1 << 5)) != 0 ? 0x924 : 0x800;

        
        if (sectorSizeMax == 0x924) {
            emulator.disk.readData(data, sectorLba * 0x930 + sectorOffset + 0xC, 4);
        } else {
            emulator.disk.readData(data, sectorLba * 0x930 + sectorOffset + 0x18, 4);
        }

        sectorOffset += 4;
        if (sectorOffset == sectorSizeMax) {
            sectorOffset = 0;
            sectorLba++;
            dataReady = true;
        }
        return littleEndian(data);
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public void step(int cycles) {
        if (delay > 0) {
            delay -= cycles;
            if (delay > 0)
                return;
        }

        switch (requestType) {
        case REQUEST_INT3:
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_Acknowledge);
            requestType = 0;
            HSTS &= ~(1 << 7);
            break;
        case REQUEST_INT2:
            statusCode &= ~0xE0;
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_Complete);
            HSTS &= ~(1 << 6);
            HSTS &= ~(1 << 7);
            requestType = 0;
            break;
        case REQUEST_INT3_INT2:
            parameterFifo.reset();
            responseFifo.reset();
            responseFifo.enqueue((1 << 1));
            requestType = REQUEST_INT2_INIT;
            doIrq(Int_Acknowledge);
            setDelay(InitAverage);
            break;
        case REQUEST_INT2_INIT:
            parameterFifo.reset();
            responseFifo.enqueue((1 << 1));
            doIrq(Int_Complete);
            requestType = 0;
            HSTS &= ~(1 << 7);
            break;
        case REQUEST_INT3_INT1:
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_Acknowledge);
            requestType = REQUEST_INT1_REQUEST;
            if ((mode & (1 << 7)) != 0) {
                setDelay(ReadDoubleSpeed);
            } else {
                setDelay(ReadSingleSpeed);
            }
            break;
        case REQUEST_INT1_REQUEST:
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_DataReady);
            dataReady = true;
            requestType = REQUEST_INT1;
            if ((mode & (1 << 7)) != 0) {
                setDelay(ReadDoubleSpeed);
            } else {
                setDelay(ReadSingleSpeed);
            }
            break;
        case REQUEST_INT1:
            requestType = REQUEST_INT1;
            if ((HCHPCTL & 0x80) != 0 && dataReady) {
                responseFifo.enqueue(readStatusCode());
                doIrq(Int_DataReady);
                HSTS |= 1 << 6;
                if ((mode & (1 << 7)) != 0) {
                    setDelay(ReadDoubleSpeed);
                } else {
                    setDelay(ReadSingleSpeed);
                }

                HCHPCTL &= ~0x80;
                dataReady = false;
            } else if (paused >= 1) {
                doIrq(Int_DataReady);
                setDelay(PauseSingleSpeed);
                if (++paused < 500) {
                    requestType = REQUEST_INT3_INT2_PAUSE;
                    paused = 0;
                }
            }
            break;
        case REQUEST_INT2_PAUSE:
            responseFifo.reset();
            responseFifo.enqueue(0x02);
            requestType = 0;
            HSTS &= ~((1 << 7) | (1 << 6));
            doIrq(2);
            break;
        case REQUEST_INT3_INT2_PAUSE:
            responseFifo.reset();
            responseFifo.enqueue(0x22);
            requestType = REQUEST_INT2_PAUSE;
            doIrq(Int_Acknowledge);
            if ((mode & (1 << 7)) != 0) {
                setDelay(PauseDoubleSpeed);
            } else {
                setDelay(PauseSingleSpeed);
            }
            break;
        case REQUEST_INT3_YYMMDDVER:
            responseFifo.enqueue(0x95);
            responseFifo.enqueue(0x05);
            responseFifo.enqueue(0x16);
            responseFifo.enqueue(0xC1);
            doIrq(Int_Acknowledge);
            requestType = 0;
            break;
        case REQUEST_INT3_INT2_GETID:
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_Acknowledge);
            setDelay(0x1230);
            requestType = REQUEST_INT2_GETID;
            break;
        case REQUEST_INT3_INT5_GETID:
            responseFifo.enqueue(readStatusCode());
            doIrq(Int_Acknowledge);
            setDelay(0x1230);
            requestType = REQUEST_INT5_GETID;
            break;
        case REQUEST_INT5_GETID:
            for (int i = 0; i < NoDiskData.length; i++)
                responseFifo.enqueue(NoDiskData[i]);
            doIrq(Int_DiskError);
            HSTS &= ~(1 << 7);
            requestType = 0;
            break;
        case REQUEST_INT2_GETID:
            for (int i = 0; i < LicensedMode2Data.length; i++)
                responseFifo.enqueue(LicensedMode2Data[i]);
            doIrq(Int_Complete);
            HSTS &= ~(1 << 7);
            requestType = 0;
            break;
        case REQUEST_INT3_INT2_SEEKL:
            statusCode = (statusCode & ~0xE0) | StatusCode_Seek;
            responseFifo.enqueue(readStatusCode());
            setDelay(0x21820);
            requestType = REQUEST_INT2;
            break;
        case REQUEST_INT3_GETTN:
            responseFifo.enqueue(readStatusCode());
            responseFifo.enqueue(0x01);
            responseFifo.enqueue(0x14);
            doIrq(Int_Acknowledge);
            requestType = 0;
            break;
        case REQUEST_INT3_GETTD: {
            int track = parameterFifo.fetch().data;

            responseFifo.enqueue(readStatusCode());
            switch (track) {
            case 0:
                responseFifo.enqueue(0);
                responseFifo.enqueue(0);
                break;
            case 2:
                responseFifo.enqueue(0);
                responseFifo.enqueue(0);
                break;
            default:
                System.out.printf("Unimplemented GetTd track %d\n", track);
                System.exit(1);
            }
            doIrq(Int_Acknowledge);
            requestType = 0;
            break;
        }
        }
    }
}