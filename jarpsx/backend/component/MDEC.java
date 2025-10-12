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
    private int pixelBlockX, pixelBlockY;
    private int pixelBlockArea;
    private int pixelBlockSize;
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
        quantTable = new byte[128];
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

    public int readMacroblockData() {
        int index = pixelBlockArea;
        pixelBlockArea += 2;
        return toRgb(pixelBlocks[index]) | toRgb(pixelBlocks[index + 1]) << 16;
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
        while (n < 64) {
            if (qfact == 0)
                value = signExtend(data & 0x3FF) * 2;

            value = saturate(value);

            if (qfact > 0)
                block[zigzag[zigzag[n]]] = value;
            if (qfact == 0)
                block[n] = value;

            data = mdecCodeBlock[++src];
            if (data == 0xDEAD) {
                src = -1;
                break;
            }

            value = (signExtend(data & 0x3FF) * quantTable[chroma ? n + 64 : n + 0] * qfact + 4) / 8;
            n += (data >>> 10) + 1;
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

    class MacroblockYCbCr {
        int Y;
        int Cb;
        int Cr;
    }
    
    private void convertToRgb() {
        int xx = 0, yy = 0;
        for (int block = 0; block < 4; block++) {
            int[] yBlock = null;
            switch (block) {
            case 0:
                xx = 0; yy = 0;
                yBlock = y1Block;
                break;
            case 1:
                xx = 0; yy = 8;
                yBlock = y2Block;
                break;
            case 2:
                xx = 8; yy = 0;
                yBlock = y3Block;
                break;
            case 3:
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
        boolean commandSet = false;
        if ((mdecStatusRegister & (1 << 29)) == 0) {
            currentCommand = data >>> 29;
            commandData = data;
            switch (currentCommand) {
            case DECODE_MACROBLOCK:
                parameterSize = parameterCounter = (data & 0xFFFF) * 4L;
                currentBlock = 4;
                mdecStatusRegister = (mdecStatusRegister & ~0x70000) | currentBlock << 16;
                pixelBlockArea = 0;
                pixelBlockSize = 1;
                for (int i = 0; i < mdecCodeBlock.length; i++)
                    mdecCodeBlock[i] = 0xDEAD;
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
            commandSet = true;
        }

        int baseOffset = (int)(parameterSize - parameterCounter - 4);
        if (baseOffset >= 0) {
            switch (currentCommand) {
            case DECODE_MACROBLOCK: {
                mdecCodeBlock[currentMdecCodeOffset] = data & 0xFFFF;
                mdecCodeBlock[currentMdecCodeOffset + 1] = data >>> 16;

                // TODO: implement this properly later
                if ((mdecCodeBlock[currentMdecCodeOffset + 1] == 0xFE00) || (mdecCodeBlock[currentMdecCodeOffset] == 0xFE00)) {
                    currentBlock = (currentBlock + 1) % 6;
                    mdecStatusRegister = (mdecStatusRegister & ~0x70000) | currentBlock << 16;
                }

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
                int depth = (readStatusRegister() >>> 25) & 3;
                src = 0;
                while (src < parameterSize && src != -1) {
                    src = decodeRLBlock(crBlock, 5, src, true);
                    if (src == -1)
                        break;

                    src = decodeRLBlock(cbBlock, 6, src, true);
                    if (src == -1)
                        break;

                    src = decodeRLBlock(y1Block, 1, src, false);
                    if (src == -1)
                        break;

                    src = decodeRLBlock(y2Block, 2, src, false);
                    if (src == -1)
                        break;

                    src = decodeRLBlock(y3Block, 3, src, false);
                    if (src == -1)
                        break;

                    src = decodeRLBlock(y4Block, 4, src, false);
                    if (src == -1)
                        break;

                    switch (depth) {
                    case 2:
                    case 3: {
                        convertToRgb();
                        for (int x = 0; x < 16 * 16; x++) {
                            pixelBlocks[x + pixelBlockArea] = pixelBlock[x];
                        }

                        pixelBlockArea += 256;
                        break;
                    }
                    default:
                        System.out.printf("Unimplemented macroblock depth %d\n", depth);
                        System.exit(1);
                    }
                }

                pixelBlockSize = pixelBlockArea;
                pixelBlockArea = 0;
                src = 0;
                currentMdecCodeOffset = 0;
                break;
            }
            }
            currentCommand = 0;
        } else {
            mdecStatusRegister |= 1 << 29;
            mdecStatusRegister = (mdecStatusRegister & ~0xFFFF) | (int)(((parameterCounter >>> 2) - 1) & 0xFFFF);
            parameterCounter -= 4;
        }
    }

    public void writeMdecControl(int value) {
        int dataInRequest = value & (1 << 30);
        int dataOutRequest = value & (1 << 29);
        if ((value & 0x80000000) != 0)
            mdecStatusRegister = 0x80040000; // aborts all commands as well

        if (dataInRequest != 0)
            mdecStatusRegister = (mdecStatusRegister & ~(1 << 28)) | dataInRequest >>> 2;
        if (dataOutRequest != 0)
            mdecStatusRegister = (mdecStatusRegister & ~(1 << 27)) | dataOutRequest >>> 2;
    }
}