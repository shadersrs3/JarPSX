package jarpsx.backend.component;

import jarpsx.backend.Emulator;

public class MDEC {
    private Emulator emulator;

    private static final int DECODE_MACROBLOCK = 0x1;
    private static final int SETQUANT_TABLE = 0x2;
    private static final int SETSCALE_TABLE = 0x3;
    private static final int MDEC_TERMINATION_CODE = 0xFE00;
    private int mdecStatusRegister;
    private int currentCommand;
    private int commandData;
    private long parameterCounter;
    private boolean colorSet;
    private short[] idctMatrix;
    private byte[] quantTable;
    private long parameterSize;
    private int[] mdecCodeBlock;
    private int[] pixelBlock;
    private int currentMdecCodeOffset;
    private int pixelBlockArea;
    private int pixelBlockAreaSize;
    private int[] pixelBlocks;
    private static final int[] zigzag = {
        0 ,1 ,5 ,6 ,14,15,27,28,
        2 ,4 ,7 ,13,16,26,29,42,
        3 ,8 ,12,17,25,30,41,43,
        9 ,11,18,24,31,40,44,53,
        10,19,23,32,39,45,52,54,
        20,22,33,38,46,51,55,60,
        21,34,37,47,50,56,59,61,
        35,36,48,49,57,58,62,63
    };

    private static int[] zagzig;

    static {
        zagzig = new int[64];
        for (int i = 0; i < 64; i++) {
            zagzig[zigzag[i]] = i;
        }
    }

    private int[] crBlock = new int[64];
    private int[] cbBlock = new int[64];
    private int[] y1Block = new int[64];
    private int[] y2Block = new int[64];
    private int[] y3Block = new int[64];
    private int[] y4Block = new int[64];
    private int currentBlock;
    private int src;

    public MDEC(Emulator emulator) {
        this.emulator = emulator;

        currentCommand = 0;
        mdecStatusRegister = 0;
        parameterCounter = 0;
        commandData = 0;
        idctMatrix = new short[64];
        quantTable = new byte[256]; // just incase of out of bounds
        colorSet = false;
        pixelBlock = new int[16 * 16];
        pixelBlocks = new int[16 * 16 * 1024];
        // random ass values
        currentBlock = 0;
        mdecCodeBlock = new int[65536*2];
        currentMdecCodeOffset = 0;
        src = 0;
    }

    private static int signExtend(int value) {
        if ((value & (1 << 9)) != 0) {
            value |= 0xFFFFFC00;
        } else {
            value &= 0x1FF;
        }
        return value;
    }

    private static int saturate(int value) {
        if (value < -0x400)
            value = -0x400;
        if (value > 0x3FF)
            value = 0x3FF;
        return value;
    }

    public int readStatusRegister() {
        return mdecStatusRegister;
    }

    public int toRgb(int value) {
        int r = value & 0xFF;
        int g = (value >>> 8) & 0xFF;
        int b = (value >>> 16) & 0xFF;
        r /= 8;
        g /= 8;
        b /= 8;
        return r << 0 | g << 5 | b << 10;
    }

    private int out = 0;
    public int readMacroblockData() {
        int index = pixelBlockArea;
        int depth = (readStatusRegister() >>> 25) & 3;

        if (out >= pixelBlockAreaSize || pixelBlockArea >= pixelBlockAreaSize) {
            mdecStatusRegister |= 1 << 31;
            return 0;
        }

        mdecStatusRegister &= ~(1 << 31);
        switch (depth) {
        case 2:
            pixelBlockArea++;
            switch (index % 4) {
            case 0:
                return pixelBlocks[out] | (pixelBlocks[out + 1] << 24);
            case 1:
                return ((pixelBlocks[out + 1] & 0xffff00) >> 8) | ((pixelBlocks[out + 2] & 0xffff) << 16);
            case 2: {
                int data  = ((pixelBlocks[out + 2] & 0xff0000) >>> 16) | ((pixelBlocks[out + 3] & 0xffffff) << 8);
                pixelBlockArea = 0;
                out += 4;
                return data;
            }
            }
            break;
        case 3:
            pixelBlockArea += 2;
            return pixelBlocks[index] | pixelBlocks[index + 1] << 16;
        }
        return 0;
    }

    private void realIdctCore(int[] src) {
        int[] dst = new int[src.length];

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int sum = 0;
                for (int z = 0; z < 8; z++) {
                    sum = sum + src[y+z*8]*(idctMatrix[x+z*8]/8);
                }
                dst[x + y * 8] = (sum + 0xfff) / 0x2000;
            }
        }

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int sum = 0;
                for (int z = 0; z < 8; z++) {
                    sum = sum + dst[y+z*8]*(idctMatrix[x+z*8]/8);
                }
                src[x + y * 8] = (sum + 0xfff) / 0x2000;
            }
        }
    }

    private int decodeRLBlock(int[] block, int type, int src, boolean chroma) {
        int n = 0;
        if (src == -1)
            return -1;

        int data = mdecCodeBlock[src];
        for (int i = 0; i <= 63; i++)
            block[i] = 0;

        while ((data = mdecCodeBlock[src]) == 0xFE00)
            src++;

        int qfact = data >>> 10;
        int coeff = signExtend(data & 0x3FF);
        int value = coeff * ((int)quantTable[chroma ? 64 : 0] & 0xFF);
        int k = 0;
        while (k < 64) {
            if (qfact == 0)
                value = signExtend(data & 0x3FF) * 2;
            value = saturate(value);

            if (qfact > 0)
                block[zagzig[k]] = value;
            if (qfact == 0)
                block[k] = value;
            ++src;
            data = mdecCodeBlock[src];
            if (data == 0xFE00) {
                // src = -1;
                break;
            }

            n = data;
            k = k + ((n >>> 10) & 0x3F) + 1;
            value = (signExtend(data & 0x3FF) * quantTable[chroma ? k + 64 : k] * qfact + 4) / 8;
            continue;
        }

        realIdctCore(block);
        return src;
    }
    
    private static int saturateRGB(int value) {
        if (value < 0)
            value = 0;
        if (value > 0xFF)
            value = 0xFF;
        return value;
    }

    private void convertToRgb() {
        int xx = 0, yy = 0;
        for (int block = 0; block < 4; block++) {
            int[] yBlock = null;
            switch (block) {
            case 0: // upper left
                xx = 0; yy = 0;
                yBlock = y1Block;
                break;
            case 1: // upper right
                xx = 8; yy = 0;
                yBlock = y2Block;
                break;
            case 2: // lower left
                xx = 0; yy = 8;
                yBlock = y3Block;
                break;
            case 3: // lower right
                xx = 8; yy = 8;
                yBlock = y4Block;
                break;
            }

            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int Cr = crBlock[((x + xx) / 2) + ((y + yy) / 2) * 8];
                    int Cb = cbBlock[((x + xx) / 2) + ((y + yy) / 2) * 8];
                    int Y = yBlock[x + y * 8];
                    // avocado reference (will reuse the one from psx-spx later)
                    int R = (int)(Y + (1.402 * (Cr)));
                    int G = (int)(Y - (0.334136 * (Cb)) - (0.714136 * (Cr)));
                    int B = (int)(Y + (1.772 * (Cb)));

                    R = saturateRGB(R + 128);
                    G = saturateRGB(G + 128);
                    B = saturateRGB(B + 128);
                    int BGR = R << 0 | G << 8 | B << 16;
                    pixelBlock[(x + xx) + (y + yy) * 16] = BGR;
                }
            }
        }
    }

    private int counter = 0;
    public void writeDataWord(int data) {
        int copyBits = (data & 0x1E000000) >>> 2;
        if ((mdecStatusRegister & (1 << 29)) == 0) {
            currentCommand = data >>> 29;
            commandData = data;
            mdecStatusRegister &= ~(1 << 30);
            switch (currentCommand) {
            case 4: case 5: case 6: case 7: case 0: break;
            case DECODE_MACROBLOCK:
                parameterSize = parameterCounter = (data & 0xFFFF) * 4L;
                pixelBlockArea = 0;
                break;
            // the values are from the jakub bad apple test
            case SETQUANT_TABLE:
                colorSet = (data & (1 << 0)) != 0;
                parameterSize = parameterCounter = colorSet == false ? 16 * 4 : 32 * 4;
                break;
            case SETSCALE_TABLE:
                parameterSize = parameterCounter = 64 / 2 * 4;
                break;
            default:
                System.out.printf("Unimplemented MDEC command %d\n", currentCommand);
                System.exit(1);
            }

            mdecStatusRegister = (mdecStatusRegister & ~0x7800000) | copyBits;
            mdecStatusRegister &= ~(1 << 31);
        }

        int baseOffset = (int)(parameterSize - parameterCounter - 4);
        if (baseOffset >= 0) {
            switch (currentCommand) {
            case DECODE_MACROBLOCK: {
                mdecCodeBlock[currentMdecCodeOffset] = data & 0xFFFF;
                mdecCodeBlock[currentMdecCodeOffset + 1] = data >>> 16;
                currentMdecCodeOffset += 2;
                break;
            }
            case SETQUANT_TABLE:
                for (int i = 0; i < 4; i++)
                    quantTable[baseOffset + i] = (byte)(data >>> (i * 8));
                break;
            case SETSCALE_TABLE:
                for (int i = 0; i < 2; i++)
                    idctMatrix[baseOffset / 2 + i] = (short)(data >>> (i * 16));
                break;
            }
        }

        if ((parameterCounter >> 2) <= 0L) {
            mdecStatusRegister &= ~(1 << 29);
            switch (currentCommand) {
            case DECODE_MACROBLOCK: {
                mdecStatusRegister |= 1 << 30;
                int depth = (readStatusRegister() >>> 25) & 3;
                src = 0;
                while (src < parameterSize / 2) {
                    src = decodeRLBlock(crBlock, 5, src, true);
                    src = decodeRLBlock(cbBlock, 6, src, true);
                    src = decodeRLBlock(y1Block, 1, src, false);
                    src = decodeRLBlock(y2Block, 2, src, false);
                    src = decodeRLBlock(y3Block, 3, src, false);
                    src = decodeRLBlock(y4Block, 4, src, false);
                    switch (depth) {
                    case 2:
                        convertToRgb();
                        for (int x = 0; x < 16 * 16; x++) {
                            int BGR = pixelBlock[x];
                            pixelBlocks[pixelBlockArea + x] = BGR;
                        }

                        pixelBlockArea += 256;
                        break;
                    case 3: {
                        convertToRgb();
                        for (int x = 0; x < 16 * 16; x++) {
                            int BGR = pixelBlock[x];
                            pixelBlocks[pixelBlockArea + x] = toRgb(BGR);
                        }
                        pixelBlockArea += 256;
                        break;
                    }
                    default:
                        System.out.printf("Unimplemented macroblock depth %d\n", crBlock[0]);
                        System.exit(1);
                    }
                }

                out = 0;
                pixelBlockAreaSize = pixelBlockArea;
                pixelBlockArea = 0;
                src = 0;
                currentMdecCodeOffset = 0;
                break;
            }
            }

            currentCommand = 0;
            mdecStatusRegister = (mdecStatusRegister & ~0xFFFF) | (int)(((parameterCounter >>> 2) - 1) & 0xFFFF);
            mdecStatusRegister |= (1 << 30);
        } else {
            mdecStatusRegister |= 1 << 29;
            mdecStatusRegister = (mdecStatusRegister & ~0xFFFF) | (int)(((parameterCounter >>> 2) - 1) & 0xFFFF);
            parameterCounter -= 4;
        }
    }

    public void writeMdecControl(int value) {
        int dataInRequest = value & (1 << 30);
        int dataOutRequest = value & (1 << 29);
        mdecStatusRegister = (mdecStatusRegister & ~(1 << 28)) | dataInRequest >>> 2;
        mdecStatusRegister = (mdecStatusRegister & ~(1 << 27)) | dataOutRequest >>> 2;
        if ((value & 0x80000000) != 0)
            mdecStatusRegister = 0x80040000; // aborts all commands as well
    }
}