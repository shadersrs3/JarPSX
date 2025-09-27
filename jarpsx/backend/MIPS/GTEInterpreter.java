package jarpsx.backend.mips;

import java.lang.Math;

import jarpsx.backend.mips.MIPS;
import jarpsx.backend.mips.GTEInstruction;

public class GTEInterpreter {
    public static int[] unr_table = {
        0xFF, 0xFD, 0xFB, 0xF9, 0xF7, 0xF5, 0xF3, 0xF1, 0xEF, 0xEE, 0xEC, 0xEA, 0xE8, 0xE6, 0xE4, 0xE3,
        0xE1, 0xDF, 0xDD, 0xDC, 0xDA, 0xD8, 0xD6, 0xD5, 0xD3, 0xD1, 0xD0, 0xCE, 0xCD, 0xCB, 0xC9, 0xC8,
        0xC6, 0xC5, 0xC3, 0xC1, 0xC0, 0xBE, 0xBD, 0xBB, 0xBA, 0xB8, 0xB7, 0xB5, 0xB4, 0xB2, 0xB1, 0xB0,
        0xAE, 0xAD, 0xAB, 0xAA, 0xA9, 0xA7, 0xA6, 0xA4, 0xA3, 0xA2, 0xA0, 0x9F, 0x9E, 0x9C, 0x9B, 0x9A,
        0x99, 0x97, 0x96, 0x95, 0x94, 0x92, 0x91, 0x90, 0x8F, 0x8D, 0x8C, 0x8B, 0x8A, 0x89, 0x87, 0x86,
        0x85, 0x84, 0x83, 0x82, 0x81, 0x7F, 0x7E, 0x7D, 0x7C, 0x7B, 0x7A, 0x79, 0x78, 0x77, 0x75, 0x74,
        0x73, 0x72, 0x71, 0x70, 0x6F, 0x6E, 0x6D, 0x6C, 0x6B, 0x6A, 0x69, 0x68, 0x67, 0x66, 0x65, 0x64,
        0x63, 0x62, 0x61, 0x60, 0x5F, 0x5E, 0x5D, 0x5D, 0x5C, 0x5B, 0x5A, 0x59, 0x58, 0x57, 0x56, 0x55,
        0x54, 0x53, 0x53, 0x52, 0x51, 0x50, 0x4F, 0x4E, 0x4D, 0x4D, 0x4C, 0x4B, 0x4A, 0x49, 0x48, 0x48,
        0x47, 0x46, 0x45, 0x44, 0x43, 0x43, 0x42, 0x41, 0x40, 0x3F, 0x3F, 0x3E, 0x3D, 0x3C, 0x3C, 0x3B,
        0x3A, 0x39, 0x39, 0x38, 0x37, 0x36, 0x36, 0x35, 0x34, 0x33, 0x33, 0x32, 0x31, 0x31, 0x30, 0x2F,
        0x2E, 0x2E, 0x2D, 0x2C, 0x2C, 0x2B, 0x2A, 0x2A, 0x29, 0x28, 0x28, 0x27, 0x26, 0x26, 0x25, 0x24,
        0x24, 0x23, 0x22, 0x22, 0x21, 0x20, 0x20, 0x1F, 0x1E, 0x1E, 0x1D, 0x1D, 0x1C, 0x1B, 0x1B, 0x1A,
        0x19, 0x19, 0x18, 0x18, 0x17, 0x16, 0x16, 0x15, 0x15, 0x14, 0x14, 0x13, 0x12, 0x12, 0x11, 0x11,
        0x10, 0x0F, 0x0F, 0x0E, 0x0E, 0x0D, 0x0D, 0x0C, 0x0C, 0x0B, 0x0A, 0x0A, 0x09, 0x09, 0x08, 0x08,
        0x07, 0x07, 0x06, 0x06, 0x05, 0x05, 0x04, 0x04, 0x03, 0x03, 0x02, 0x02, 0x01, 0x01, 0x00, 0x00,
        0x00
    };

    private static int countLeadingZeros(int value, int bitCount) {
        int count = 0;
        for (int i = 0; i < bitCount; i++) {
            int mask = 1 << ((bitCount - 1) - i);
            if ((value & mask) != 0)
                break;

            count++;
        }
        return count;
    }

    private static int gteDivide(MIPS mips, int numerator, int denominator) {
        if (numerator >= denominator * 2) {  // Division overflow
            mips.gteReg[63] |= 1 << 17;
            return 0x1ffff;
        }

        int shift = 0;
        shift = countLeadingZeros(denominator, 16);
        int r1 = (denominator << shift) & 0x7fff;
        int r2 = unr_table[((r1 + 0x40) >>> 7)] + 0x101;
        int r3 = ((0x80 - (r2 * (r1 + 0x8000))) >>> 8) & 0x1ffff;
        int reciprocal = ((r2 * r3) + 0x80) >>> 8;

        int res = (int)(((((long)reciprocal * ((int)numerator << shift)) + 0x8000) >>> 16));
        // Some divisions like 0xF015/0x780B result in 0x20000, but are saturated to 0x1ffff without setting FLAG
        if (res > 0x1FFFF) {
            mips.gteReg[63] |= 1 << 17;
            res = 0x1FFFF;
        }
        return Integer.min(0x1ffff, res);
    }

    public static void writeRegister(MIPS mips, int index, int value) {
        switch (index) {
        // 16 bit vectors
        case 0:
        case 2:
        case 4:
            mips.gteReg[index] = value;
            return;
        case 1:
        case 3:
        case 5:
        case 9:
        case 10:
        case 11:
            mips.gteReg[index] = (int)((short)value & 0xFFFF);
            return;

        // Color register
        case 6:
        case 20:
        case 21:
        case 22:
        case 23:
            mips.gteReg[index] = value;
            return;
        // Average Z registers
        case 7:
            mips.gteReg[index] = value & 0xFFFF;
            return;
        // Interpolation factor
        case 8:
            mips.gteReg[index] = (int)(short)(value & 0xFFFF);
            return;
        // Screen XYZ Coordinate FIFOs
        case 12:
        case 13:
        case 14:
            mips.gteReg[index] = value;
            return;
        case 15: {
            int sxy2 = mips.gteReg[14];
            int sxy1 = mips.gteReg[13];
            mips.gteReg[12] = sxy1;
            mips.gteReg[13] = sxy2;
            mips.gteReg[14] = value;
            return;
        }
        case 16:
        case 17:
        case 18:
        case 19:
            mips.gteReg[index] = value & 0xFFFF;
            return;
        // MAC registers
        case 24:
        case 25:
        case 26:
        case 27:
            mips.gteReg[index] = value;
            return;
        // IRGB, ORGB
        case 28: {
            int red = (value & 0x1F) * 0x80;
            int green = ((value >>> 5) & 0x1F) * 0x80;
            int blue = ((value >>> 10) & 0x1F) * 0x80;
            mips.gteReg[9] = red;
            mips.gteReg[10] = green;
            mips.gteReg[11] = blue;
            return;
        }

        case 29:
            return;
        case 30:
            mips.gteReg[index] = value;

            // Leading zeros (taken from avocado)
            int zeros = 0;
            if ((value & 0x80000000) == 0) value = ~value;
            while ((value & 0x80000000) != 0) {
                zeros++;
                value <<= 1;
            }

            mips.gteReg[31] = zeros;
            return;
        case 31: return;
        // Far color
        case 53:
        case 54:
        case 55:
            mips.gteReg[index] = value;
            return;

        // Screen offset and distance
        case 56:
        case 57:
        case 60:
            mips.gteReg[index] = value;
            return;
        case 59:
            mips.gteReg[index] = (int)(short)(value & 0xFFFF);
            return;
        case 58:
            mips.gteReg[index] = value & 0xFFFF;
            return;
        // Average Z registers
        case 61:
        case 62:
            mips.gteReg[index] = (int)(short)(value & 0xFFFF);
            return;
        case 63:
            mips.gteReg[index] = (mips.gteReg[index] & ~0x7FFFF000) | (value & 0x7FFFF000);
            return;
        }

        if (index >= 32 && index <= 52) {
            switch (index) {
            case 36:
            case 44:
            case 52:
                mips.gteReg[index] = (int)(short)(value & 0xFFFF);
                break;
            default:
                mips.gteReg[index] = value;
            }
            return;
        }

        System.out.println(String.format("Unknown register index %d = %08X", index, value));
        System.exit(-1);
    }

    public static int readRegister(MIPS mips, int index) {
        switch (index) {
        case 1:
        case 3:
        case 5:
        case 9:
        case 10:
        case 11:
            return (int)(short)(mips.gteReg[index] & 0xFFFF);
        case 15:
            return mips.gteReg[14];
        case 58: // BUG (Distance H)
            return (int)(short)(mips.gteReg[index] & 0xFFFF);
        case 29:
        case 28: {
            int red = mips.gteReg[9] & 0xFFFF;
            int green = mips.gteReg[10] & 0xFFFF;
            int blue = mips.gteReg[11] & 0xFFFF;
            int result;
            
            if ((short)red < 0)
                red = 0;
            if ((short)green < 0)
                green = 0;
            if ((short)blue < 0)
                blue = 0;

            if ((short)red > 0xF80)
                red = 0xF80;
            if ((short)green > 0xF80)
                green = 0xF80;
            if ((short)blue > 0xF80)
                blue = 0xF80;

            red >>= 7;
            green >>= 7;
            blue >>= 7;

            result = red | green << 5 | blue << 10;
            return result;
        }
        case 63:
            return mips.gteReg[index] | ((mips.gteReg[index] & 0x7F87E000) != 0 ? 0x80000000 : 0);
        }
        return mips.gteReg[index];
    }

    private static int limA(MIPS mips, long result, int saturationBit, boolean lm) {
        int flag = mips.gteReg[63];
        int limit = lm ? 0 : 1;
        // flag = (flag & ~(1 << saturationBit));
        if (result < -0x8000 * limit) {
            result = -0x8000 * limit;
            flag |= (1 << saturationBit);
        } else if (result > 0x7FFF) {
            result = 0x7FFF;
            flag |= (1 << saturationBit);
        }
        
        mips.gteReg[63] = flag;
        return (short)result;
    }

    private static int limA_SF(MIPS mips, long result, int saturationBit, boolean lm, int sf) {
        int flag = mips.gteReg[63];
        int limit = lm ? 0 : 1;
        
        if ((result >> 12) < -0x8000) {
            flag |= (1 << saturationBit);
        } else if ((result >> 12) > 0x7FFF) {
            flag |= (1 << saturationBit);
        }

        if (result < -0x8000 * limit) {
            result = -0x8000 * limit;

            if (sf != 0)
                flag |= (1 << saturationBit);
        } else if (result > 0x7FFF) {
            result = 0x7FFF;
            flag |= (1 << saturationBit);
        }
        
        mips.gteReg[63] = flag;
        return (short)result;
    }

    private static int limC(MIPS mips, int result) {
        if (result < 0) {
            result = 0;
            mips.gteReg[63] |= 1 << 18;
        } else if (result > 0xFFFF) {
            result = 0xFFFF;
            mips.gteReg[63] |= 1 << 18;
        }
        return result;
    }

    private static int getShortExpanded(final int value, boolean msb) {
        if (msb)
            return (int)(short)((value >>> 16) & 0xFFFF);
        return (int)(short)(value & 0xFFFF);
    }
    
    private static long getShortExpandedLong(final int value, boolean msb) {
        if (msb) {
            return (long)(short)((value >>> 16) & 0xFFFF);
        }
        return (long)(short)(value & 0xFFFF);
    }

    private static void setMacFlag(MIPS mips, int macIndex, long result) {
        int positiveIndex;
        int negativeIndex;
        int macRegisterIndex;
        int flag = mips.gteReg[63];

        switch (macIndex) {
        case 0:
            positiveIndex = 16;
            negativeIndex = 15;
            break;
        case 1:
            positiveIndex = 30;
            negativeIndex = 27;
            break;
        case 2:
            positiveIndex = 29;
            negativeIndex = 26;
            break;
        case 3:
            positiveIndex = 28;
            negativeIndex = 25;
            break;
        default:
            positiveIndex = -1;
            negativeIndex = -1;
        }

        long _44bit = result & 0xFFF_FFFF_FFFFL;
        if (macIndex != 0) {
            if (result < -0x800_0000_0000L) {
                flag |= 1 << negativeIndex;
            } else if (result > 0x7FF_FFFF_FFFFL) {
                flag |= 1 << positiveIndex;
            }
        } else {
            if (result < -0x80000000L) {
                flag |= 1 << negativeIndex;
            } else if (result >= 0x80000000L) {
                flag |= 1 << positiveIndex;
            }
        }

        mips.gteReg[63] = flag;
    }
    
    private static void setMac0Flag(MIPS mips, long result) {
        int positiveIndex = 16;
        int negativeIndex = 15;
        int currentIndex;
        int flag = mips.gteReg[63];
        // flag = (flag & (~(1 << positiveIndex) | ~(1 << negativeIndex)));

        int _32bit = (int)(result & 0xFFFFFFFF);
        // I don't fucking know :(
        if (result < -0x80000000L) {
            flag |= 1 << negativeIndex;
        } else if (result > 0x7FFFFFFFL) {
            flag |= 1 << positiveIndex;
        }

        mips.gteReg[63] = flag;
    }

    private static void set_SXYFlag(MIPS mips, int result, boolean y) {
        int saturationBits = 0;
        if (result < -0x400) {
            if (y) {
                saturationBits = 1 << 13;
            } else {
                saturationBits = 1 << 14;
            }
        } else if (result > 0x3FF) {
            if (y) {
                saturationBits = 1 << 13;
            } else {
                saturationBits = 1 << 14;
            }
        }
        
        mips.gteReg[63] |= saturationBits;
    }

    private static int limE(MIPS mips, long result) {
        if (result < 0L) {
            result = 0L;
            mips.gteReg[63] |= 1 << 12;
        } else if (result > 0x1000) {
            result = 0x1000L;
            mips.gteReg[63] |= 1 << 12;
        }
        return (int)result;
    }
    
    private static int clamp32(int value, int low, int high) {
        if (value < low)
            value = low;
        if (value > high)
            value = high;
        return value;
    }

    private static long clamp64(long value, long low, long high) {
        if (value < low)
            value = low;
        if (value > high)
            value = high;
        return value;
    }

    public static final int TRANSLATION_VECTOR = 0;
    public static final int BACKGROUND_COLOR_VECTOR = 1;
    public static final int FAR_COLOR_VECTOR = 2;
    public static final int NONE_VECTOR = 3;
    public static final int V0_VECTOR = 4;
    public static final int V1_VECTOR = 5;
    public static final int V2_VECTOR = 6;
    public static final int IR_VECTOR = 7;
    public static final int FAR_COLOR_VECTOR_NO_SCALE = 8;
    public static final int RGB0_VECTOR = 100;
    public static final int RGBC_VECTOR = 101;
    public static final int RGBC_VECTOR_SCALED_X4 = 102;
    public static final int RGB0_VECTOR_SCALED_X4 = 103;
    public static final int ROTATION_MATRIX = 0;
    public static final int LIGHT_MATRIX = 1;
    public static final int COLOR_MATRIX = 2;
    public static final int RESERVED_MATRIX = 3;

    public static final Vector zero = new Vector(null, -1);

    public static long signExtend44(long a) {
        long mask = (1L << (44 - 1)) - 1;
        boolean sign = (a & (1L << (44 - 1))) != 0;
        long val = a & mask;
        if (sign) val |= ~mask;
        return val;
    }

    public static class Matrix {
        public long[] data;
        private MIPS mips;

        public Matrix(MIPS mips, int id) {
            data = new long[9];
            this.mips = mips;
            boolean state = false;
            int index = -1;
            switch (id) {
            case ROTATION_MATRIX:
                index = 32;
                break;
            case LIGHT_MATRIX:
                index = 40;
                break;
            case COLOR_MATRIX:
                index = 48;
                break;
            case RESERVED_MATRIX: {
                long RT13 = getShortExpandedLong(mips.gteReg[33], false), RT22 = getShortExpandedLong(mips.gteReg[34], false);
                data[0] = -(long)(mips.gteReg[6] & 0xFF) << 4;
                data[1] = (long)(mips.gteReg[6] & 0xFF) << 4;
                data[2] = (short)mips.gteReg[8];
                data[3] = RT13;
                data[4] = RT13;
                data[5] = RT13;
                data[6] = RT22;
                data[7] = RT22;
                data[8] = RT22;
                return;
            }
            default:
                throw new RuntimeException(String.format("Invalid Matrix id %d", id));
            }

            int current = -1;
            for (int i = 0; i < 9; i++) {
                if (i % 2 == 0) {
                    state = false;
                    current++;
                } else {
                    state = true;
                }
                data[i] = getShortExpandedLong(mips.gteReg[current+index], state);
            }
        }

        public long O(int index, long result) {
            setMacFlag(mips, index, result);
            return signExtend44(result);
        }

        public Vector multiply(Vector v, Vector tr) {
            Vector result = new Vector(mips, -1);
            long sumX = 0L;
            long sumY = 0L;
            long sumZ = 0L;
            sumX += tr.data[0];
            sumY += tr.data[1];
            sumZ += tr.data[2];
            for (int i = 0; i < 3; i++) {
                sumX += v.data[i] * data[i];
                sumY += v.data[i] * data[i+3];
                sumZ += v.data[i] * data[i+6];
            }

            result.data[0] = sumX;
            result.data[1] = sumY;
            result.data[2] = sumZ;
            O(1, O(1, O(1, tr.data[0] + data[0] * v.data[0]) + data[1] * v.data[1]) + data[2] * v.data[2]);
            O(2, O(2, O(2, tr.data[1] + data[3] * v.data[0]) + data[4] * v.data[1]) + data[5] * v.data[2]);
            O(3, O(3, O(3, tr.data[2] + data[6] * v.data[0]) + data[7] * v.data[1]) + data[8] * v.data[2]);
            return result;
        }

    }

    public static class Vector {
        public long[] data;
        private MIPS mips;

        public Vector() {
            data = new long[3];
            data[0] = data[1] = data[2] = 0;
        }

        public Vector(MIPS mips, int id) {
            data = new long[3];
            this.mips = mips;
            switch (id) {
            case RGB0_VECTOR:
                data[0] = (long)((mips.gteReg[20] & 0xFF) << 16);
                data[1] = (long)(((mips.gteReg[20] >>> 8) & 0xFF) << 16);
                data[2] = (long)(((mips.gteReg[20] >>> 16) & 0xFF) << 16);
                break;
            case RGB0_VECTOR_SCALED_X4:
                data[0] = (long)((mips.gteReg[20] & 0xFF) << 4);
                data[1] = (long)(((mips.gteReg[20] >>> 8) & 0xFF) << 4);
                data[2] = (long)(((mips.gteReg[20] >>> 16) & 0xFF) << 4);
                break;
            case RGBC_VECTOR:
                data[0] = (long)(mips.gteReg[6] & 0xFF) << 16;
                data[1] = (long)((mips.gteReg[6] >>> 8) & 0xFF) << 16;
                data[2] = (long)((mips.gteReg[6] >>> 16) & 0xFF) << 16;
                break;
            case RGBC_VECTOR_SCALED_X4:
                data[0] = (long)(mips.gteReg[6] & 0xFF) << 4;
                data[1] = (long)((mips.gteReg[6] >>> 8) & 0xFF) << 4;
                data[2] = (long)((mips.gteReg[6] >>> 16) & 0xFF) << 4;
                break;
            case TRANSLATION_VECTOR:
                data[0] = (long)mips.gteReg[37] << 12;
                data[1] = (long)mips.gteReg[38] << 12;
                data[2] = (long)mips.gteReg[39] << 12;
                break;
            case BACKGROUND_COLOR_VECTOR:
                data[0] = (long)mips.gteReg[45] << 12;
                data[1] = (long)mips.gteReg[46] << 12;
                data[2] = (long)mips.gteReg[47] << 12;
                break;
            case FAR_COLOR_VECTOR_NO_SCALE:
                data[0] = (long)mips.gteReg[53];
                data[1] = (long)mips.gteReg[54];
                data[2] = (long)mips.gteReg[55];
                break;
            case FAR_COLOR_VECTOR:
                data[0] = (long)mips.gteReg[53] << 12;
                data[1] = (long)mips.gteReg[54] << 12;
                data[2] = (long)mips.gteReg[55] << 12;
                break;
            case NONE_VECTOR:
                for (int i = 0; i < 3; i++)
                    data[i] = 0;
                break;
            case V0_VECTOR:
                data[0] = getShortExpandedLong(mips.gteReg[0], false);
                data[1] = getShortExpandedLong(mips.gteReg[0], true);
                data[2] = (long)(short)mips.gteReg[1];
                break;
            case V1_VECTOR:
                data[0] = getShortExpandedLong(mips.gteReg[2], false);
                data[1] = getShortExpandedLong(mips.gteReg[2], true);
                data[2] = (long)(short)mips.gteReg[3];
                break;
            case V2_VECTOR:
                data[0] = getShortExpandedLong(mips.gteReg[4], false);
                data[1] = getShortExpandedLong(mips.gteReg[4], true);
                data[2] = (long)(short)mips.gteReg[5];
                break;
            case IR_VECTOR:
                data[0] = (long)(short)mips.gteReg[9];
                data[1] = (long)(short)mips.gteReg[10];
                data[2] = (long)(short)mips.gteReg[11];
                break;
            default:
                for (int i = 0; i < 3; i++)
                    data[i] = 0;
                break;
            }
        }
        
        public Vector(long a, long b, long c) {
            data = new long[3];
            data[0] = a;
            data[1] = b;
            data[2] = c;
        }
        
        public Vector(Vector v) {
            data = new long[3];
            for (int i = 0; i < 3; i++) {
                data[i] = v.data[i];
            }
        }

        public Vector addVector(Vector a) {
            Vector result = new Vector(mips, -1);
            for (int i = 0; i < 3; i++) {
                result.data[i] = a.data[i] + this.data[i];
            }
            return result;
        }

        public Vector subtractVector(Vector a) {
            Vector result = new Vector(mips, -1);
            for (int i = 0; i < 3; i++) {
                result.data[i] = this.data[i] - a.data[i];
            }
            return result;
        }
        
        public Vector multiplyVector(long a, long b, long c) {
            Vector result = new Vector(this);
            result.data[0] *= a;
            result.data[1] *= b;
            result.data[2] *= c;
            return result;
        }
        
        public Vector multiplyScalar(long scale) {
            Vector result = new Vector(mips, -1);
            for (int i = 0; i < 3; i++) {
                result.data[i] = scale * this.data[i];
            }
            return result;
        }

        public Vector shiftFraction(int sf) {
            Vector result = new Vector(this);
            if (sf != 0) {
                for (int i = 0; i < 3; i++)
                    result.data[i] >>= sf;
            }
            return result;
        }
    }

    public static int O_MAC0(MIPS mips, long result) {
        setMacFlag(mips, 0, result);
        return (int)result;
    }
    
    private static int limB(MIPS mips, int result, int saturationBit) {
        if (result < 0) {
            result = 0;
            mips.gteReg[63] |= 1 << saturationBit;
        } else if (result > 0xFF) {
            result = 0xFF;
            mips.gteReg[63] |= 1 << saturationBit;
        }
        return result;
    }

    private static void pushColorFIFO(MIPS mips, int r, int g, int b, int code) {
        r = limB(mips, r, 21);
        g = limB(mips, g, 20);
        b = limB(mips, b, 19);
        int data = r | g << 8 | b << 16 | code << 24;
        mips.gteReg[20] = mips.gteReg[21];
        mips.gteReg[21] = mips.gteReg[22];
        mips.gteReg[22] = data;
    }
    
    public static int counter = 0;
    
    static void printf(String fmt, Object ... args) {
        System.out.printf(fmt, args);
    }

    public static void execute(MIPS mips, GTEInstruction gteInstruction) {
        int cmd = gteInstruction.command();
        int sf = gteInstruction.sf();
        boolean lm = gteInstruction.lm();
        switch (cmd) {
        case 0x01: { // RTPS
            Vector tv = new Vector(mips, TRANSLATION_VECTOR);
            Vector mv = new Vector(mips, V0_VECTOR);
            Matrix mat = new Matrix(mips, ROTATION_MATRIX);
            Vector vector = mat.multiply(mv, tv);
            Vector result = vector.shiftFraction(sf);
            mips.gteReg[63] = 0;
            int MAC1 = (int)result.data[0];
            int MAC2 = (int)result.data[1];
            int MAC3 = (int)result.data[2];
            int IR1 = limA(mips, MAC1, 24, lm);
            int IR2 = limA(mips, MAC2, 23, lm);
            int IR3 = limA_SF(mips, MAC3, 22, lm, sf);
            long y = result.data[1];
            
            setMacFlag(mips, 1, vector.data[0]);
            setMacFlag(mips, 2, vector.data[1]);
            setMacFlag(mips, 3, vector.data[2]);

            int SZ = limC(mips, (int)(result.data[2] >> (12 - sf)));
            int division = gteDivide(mips, mips.gteReg[58], SZ);
            long SX = division * (long)(short)IR1 + (long)mips.gteReg[56];
            long SY = division * (long)(short)IR2 + (long)mips.gteReg[57];
            long P = division * (long)(short)mips.gteReg[59] + (long)mips.gteReg[60];
            setMac0Flag(mips, SX);
            setMac0Flag(mips, SY);
            setMac0Flag(mips, P);
            set_SXYFlag(mips, (int)(SX >> 16), false);
            set_SXYFlag(mips, (int)(SY >> 16), true);

            int MAC0;
            MAC0 = (int)SX;
            int SX2 = (int)clamp64(SX >> 16, -0x400, 0x3FF);
            MAC0 = (int)SY;
            int SY2 = (int)clamp64(SY >> 16, -0x400, 0x3FF);
            int SXY = (int)(SX2 & 0xFFFFL) | (int)(SY2 << 16);
            MAC0 = (int)P;            

            int IR0 = limE(mips, P >> 12);
            // Now set the results from calculation and the flags are done with calculations
            // Set the IR registers (because my fucking dumbass forgot to bring aliased names lol why be fucking lazy and use an array for everything :p)
            writeRegister(mips, 8, IR0);
            writeRegister(mips, 9, IR1);
            writeRegister(mips, 10, IR2);
            writeRegister(mips, 11, IR3);

            // Set the MAC registers
            mips.gteReg[24] = MAC0;
            mips.gteReg[25] = MAC1;
            mips.gteReg[26] = MAC2;
            mips.gteReg[27] = MAC3;

            // Set the SXYZ registers
            writeRegister(mips, 15, SXY);
            mips.gteReg[16] = mips.gteReg[17];
            mips.gteReg[17] = mips.gteReg[18];
            mips.gteReg[18] = mips.gteReg[19];
            mips.gteReg[19] = SZ;
            break;
        }
        case 0x06: { // NCLIP
            int SXY0 = mips.gteReg[12];
            int SXY1 = mips.gteReg[13];
            int SXY2 = mips.gteReg[14];
            long SX0, SX1, SX2, SY0, SY1, SY2;
            long OPZ;
            SY0 = (short)((SXY0 >>> 16) & 0xFFFF);
            SY1 = (short)((SXY1 >>> 16) & 0xFFFF);
            SY2 = (short)((SXY2 >>> 16) & 0xFFFF);
            SX0 = (short)(SXY0 & 0xFFFF);
            SX1 = (short)(SXY1 & 0xFFFF);
            SX2 = (short)(SXY2 & 0xFFFF);
            OPZ = SX0*SY1 + SX1*SY2 + SX2*SY0 - SX0*SY2 - SX1*SY0 - SX2*SY1;

            mips.gteReg[63] = 0;
            setMac0Flag(mips, OPZ);
            mips.gteReg[24] = (int)OPZ; // MAC0
            break;
        }
        case 0x0C: { // OP
            long IR1, IR2, IR3, D1, D2, D3, MAC1, MAC2, MAC3;

            IR1 = mips.gteReg[9];
            IR2 = mips.gteReg[10];
            IR3 = mips.gteReg[11];

            D1 = (short)(mips.gteReg[32]);
            D2 = (short)(mips.gteReg[34]);
            D3 = (short)(mips.gteReg[36]);
            MAC1 = (IR3 * D2 - IR2 * D3);
            MAC2 = (IR1 * D3 - IR3 * D1);
            MAC3 = (IR2 * D1 - IR1 * D2);

            mips.gteReg[63] = 0;
            
            setMacFlag(mips, 1, MAC1);
            setMacFlag(mips, 2, MAC2);
            setMacFlag(mips, 3, MAC3);

            MAC1 >>= sf;
            MAC2 >>= sf;
            MAC3 >>= sf;

            int IR1_new = limA(mips, (int)MAC1, 24, lm);
            int IR2_new = limA(mips, (int)MAC2, 23, lm);
            int IR3_new = limA(mips, (int)MAC3, 22, lm);

            mips.gteReg[9] = IR1_new;
            mips.gteReg[10] = IR2_new;
            mips.gteReg[11] = IR3_new;
            mips.gteReg[25] = (int)MAC1;
            mips.gteReg[26] = (int)MAC2;
            mips.gteReg[27] = (int)MAC3;
            break;
        }
        case 0x10: { // DPCS
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR); 
            Vector macColorVector = new Vector(mips, RGBC_VECTOR);
            Vector IR;

            mips.gteReg[63] = 0;
            Vector subtractedVector = farColorVector.subtractVector(macColorVector);
            Vector limit = new Vector(subtractedVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
            limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
            limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);
            limit = limit.multiplyScalar((long)(short)mips.gteReg[8]).addVector(macColorVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit = limit.shiftFraction(sf);

            IR = new Vector(mips, -1);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);

            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            break;
        }
        case 0x11: { // INTPL
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR); 
            Vector macColorVector = new Vector(readRegister(mips, 9) << 12, readRegister(mips, 10) << 12, readRegister(mips, 11) << 12);
            Vector IR;
            mips.gteReg[63] = 0;
            Vector subtractedVector = farColorVector.subtractVector(macColorVector);
            Vector limit = new Vector(subtractedVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
            limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
            limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);
            limit = limit.multiplyScalar((long)(short)mips.gteReg[8]).addVector(macColorVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit = limit.shiftFraction(sf);

            IR = new Vector(mips, -1);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);

            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            break;
        }
        case 0x12: { // MVMVA
            int mx = gteInstruction.mx();
            int vx = gteInstruction.vx();
            int tx = gteInstruction.tx();
            Vector v = new Vector(mips, V0_VECTOR + vx);
            Vector t = new Vector(mips, tx);
            Matrix m = new Matrix(mips, mx);
            Vector MAC;
            Vector IR;
            Vector result;

            mips.gteReg[63] = 0;
            if (tx == 2) {
                int IR1, IR2, IR3, MAC1, MAC2, MAC3;
                long resultX = t.data[0] + m.data[0] * v.data[0];
                long resultY = t.data[1] + m.data[3] * v.data[0];
                long resultZ = t.data[2] + m.data[6] * v.data[0];
                setMacFlag(mips, 1, resultX);
                setMacFlag(mips, 2, resultY);
                setMacFlag(mips, 3, resultZ);
                limA(mips, (int)(resultX >> sf), 24, false);
                limA(mips, (int)(resultY >> sf), 23, false);
                limA(mips, (int)(resultZ >> sf), 22, false);

                // Now do the buggy components
                resultX = m.data[1] * v.data[1];
                resultX = resultX + m.data[2] * v.data[2];
                setMacFlag(mips, 1, resultX);

                resultY = m.data[3+1] * v.data[1];
                resultY = resultY + m.data[3+2] * v.data[2];
                setMacFlag(mips, 2, resultY);

                resultZ = m.data[6+1] * v.data[1];
                resultZ = resultZ + m.data[6+2] * v.data[2];
                setMacFlag(mips, 3, resultZ);

                resultX = resultX >> sf;
                resultY = resultY >> sf;
                resultZ = resultZ >> sf;

                IR1 = limA(mips, (int)resultX, 24, lm);
                IR2 = limA(mips, (int)resultY, 23, lm);
                IR3 = limA(mips, (int)resultZ, 22, lm);
                MAC1 = (int)resultX;
                MAC2 = (int)resultY;
                MAC3 = (int)resultZ;
                mips.gteReg[9] = IR1;
                mips.gteReg[10] = IR2;
                mips.gteReg[11] = IR3;
                mips.gteReg[25] = MAC1;
                mips.gteReg[26] = MAC2;
                mips.gteReg[27] = MAC3;
            } else {
                result = m.multiply(v, t);
                result = result.shiftFraction(sf);
                mips.gteReg[25] = (int)result.data[0];
                mips.gteReg[26] = (int)result.data[1];
                mips.gteReg[27] = (int)result.data[2];
                mips.gteReg[9] = limA(mips, (int)(result.data[0]), 24, lm);
                mips.gteReg[10] = limA(mips, (int)(result.data[1]), 23, lm);
                mips.gteReg[11] = limA(mips, (int)(result.data[2]), 22, lm);
            }
            break;
        }
        case 0x13: { // NCDS
            Vector v = new Vector(mips, V0_VECTOR);
            Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
            Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
            Matrix LCm = new Matrix(mips, COLOR_MATRIX);
            Vector IR = new Vector();
            Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
            Vector lightResult = LLm.multiply(v, zero);
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

            mips.gteReg[63] = 0;

            lightResult = lightResult.shiftFraction(sf);
            IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

            Vector bkLightResult = LCm.multiply(IR, bk);
            bkLightResult = bkLightResult.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

            Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
            setMacFlag(mips, 1, colorVector.data[0]);
            setMacFlag(mips, 2, colorVector.data[1]);
            setMacFlag(mips, 3, colorVector.data[2]);

            Vector subtractedVector = farColorVector.subtractVector(colorVector);
            Vector limit = new Vector(subtractedVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
            limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
            limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);

            limit = limit.multiplyScalar((long)(short)mips.gteReg[8]);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit = limit.addVector(colorVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            break;
        }
        case 0x14: { // CDP
            Vector v = new Vector(mips, V0_VECTOR);
            Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
            Matrix LCm = new Matrix(mips, COLOR_MATRIX);
            Vector IR = new Vector(mips, IR_VECTOR);
            Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

            mips.gteReg[63] = 0;

            Vector bkLightResult = LCm.multiply(IR, bk);
            bkLightResult = bkLightResult.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

            Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
            setMacFlag(mips, 1, colorVector.data[0]);
            setMacFlag(mips, 2, colorVector.data[1]);
            setMacFlag(mips, 3, colorVector.data[2]);

            Vector subtractedVector = farColorVector.subtractVector(colorVector);
            Vector limit = new Vector(subtractedVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
            limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
            limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);

            limit = limit.multiplyScalar((long)(short)mips.gteReg[8]);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit = limit.addVector(colorVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            break;
        }
        case 0x16: { // NCDT
            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++) {
                Vector v = new Vector(mips, V0_VECTOR + i);
                Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
                Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
                Matrix LCm = new Matrix(mips, COLOR_MATRIX);
                Vector IR = new Vector();
                Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
                Vector lightResult = LLm.multiply(v, zero);
                Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

                lightResult = lightResult.shiftFraction(sf);
                IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

                Vector bkLightResult = LCm.multiply(IR, bk);
                setMacFlag(mips, 1, bkLightResult.data[0]);
                setMacFlag(mips, 2, bkLightResult.data[1]);
                setMacFlag(mips, 3, bkLightResult.data[2]);

                bkLightResult = bkLightResult.shiftFraction(sf);

                IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

                Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
                setMacFlag(mips, 1, colorVector.data[0]);
                setMacFlag(mips, 2, colorVector.data[1]);
                setMacFlag(mips, 3, colorVector.data[2]);

                Vector subtractedVector = farColorVector.subtractVector(colorVector);
                Vector limit = new Vector(subtractedVector);
                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);

                limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
                limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
                limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);
                limit = limit.multiplyScalar((long)(short)mips.gteReg[8]);
                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);

                limit = limit.addVector(colorVector);
                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);
                limit = limit.shiftFraction(sf);

                IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
                IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
                IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

                mips.gteReg[25] = (int)limit.data[0];
                mips.gteReg[26] = (int)limit.data[1];
                mips.gteReg[27] = (int)limit.data[2];
                mips.gteReg[9] = (int)IR.data[0];
                mips.gteReg[10] = (int)IR.data[1];
                mips.gteReg[11] = (int)IR.data[2];
                pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            }
            break;
        }
        case 0x1B: { // NCCS
            Vector v = new Vector(mips, V0_VECTOR);
            Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
            Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
            Matrix LCm = new Matrix(mips, COLOR_MATRIX);
            Vector IR = new Vector();
            Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
            Vector lightResult = LLm.multiply(v, zero);
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

            mips.gteReg[63] = 0;

            lightResult = lightResult.shiftFraction(sf);
            IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

            Vector bkLightResult = LCm.multiply(IR, bk);
            bkLightResult = bkLightResult.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

            Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
            Vector limit = new Vector(colorVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            break;
        }
        case 0x1C: { // CC
            Vector v = new Vector(mips, V0_VECTOR);
            Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
            Matrix LCm = new Matrix(mips, COLOR_MATRIX);
            Vector IR = new Vector(mips, IR_VECTOR);
            Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

            mips.gteReg[63] = 0;

            Vector bkLightResult = LCm.multiply(IR, bk);
            bkLightResult = bkLightResult.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

            Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
            Vector limit = new Vector(colorVector);
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            break;
        }
        case 0x1E: { // NCS
            Vector v = new Vector(mips, V0_VECTOR);
            Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
            Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
            Matrix LCm = new Matrix(mips, COLOR_MATRIX);
            Vector IR = new Vector();
            Vector lightResult = LLm.multiply(v, zero);

            mips.gteReg[63] = 0;

            lightResult = lightResult.shiftFraction(sf);
            IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

            Vector bkLightResult = LCm.multiply(IR, bk);
            bkLightResult = bkLightResult.shiftFraction(sf);

            IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
            IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
            IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);
            Vector limit = new Vector(bkLightResult);


            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            break;
        }
        case 0x20: { // NCT
            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++) {
                Vector v = new Vector(mips, V0_VECTOR + i);
                Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
                Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
                Matrix LCm = new Matrix(mips, COLOR_MATRIX);
                Vector IR = new Vector();
                Vector lightResult = LLm.multiply(v, zero);

                lightResult = lightResult.shiftFraction(sf);
                IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

                Vector bkLightResult = LCm.multiply(IR, bk);
                bkLightResult = bkLightResult.shiftFraction(sf);

                IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);
                Vector limit = new Vector(bkLightResult);

                mips.gteReg[25] = (int)limit.data[0];
                mips.gteReg[26] = (int)limit.data[1];
                mips.gteReg[27] = (int)limit.data[2];
                mips.gteReg[9] = (int)IR.data[0];
                mips.gteReg[10] = (int)IR.data[1];
                mips.gteReg[11] = (int)IR.data[2];
                pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            }
            break;
        }
        case 0x28: { // SQR
            Vector MAC, IR;
            
            IR = new Vector(mips, IR_VECTOR);
            IR = IR.multiplyVector(IR.data[0], IR.data[1], IR.data[2]);

            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++)
                IR.data[i] >>>= sf;

            MAC = new Vector(IR);
            IR.data[0] = limA(mips, (int)(IR.data[0]), 24, false);
            IR.data[1] = limA(mips, (int)(IR.data[1]), 23, false);
            IR.data[2] = limA(mips, (int)(IR.data[2]), 22, false);

            mips.gteReg[25] = (int)MAC.data[0];
            mips.gteReg[26] = (int)MAC.data[1];
            mips.gteReg[27] = (int)MAC.data[2];
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            break;
        }
        case 0x29: { // DCPL
            Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR); 
            Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
            Vector IR = new Vector(mips, IR_VECTOR);
            Vector macColorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);

            mips.gteReg[63] = 0;
            Vector subtractedVector = farColorVector.subtractVector(macColorVector);
            Vector limit = new Vector(subtractedVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
            limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
            limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);
            limit = limit.multiplyScalar((long)(short)mips.gteReg[8]).addVector(macColorVector);

            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);

            limit = limit.shiftFraction(sf);

            IR = new Vector(mips, -1);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);

            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            break;
        }
        case 0x2A: { // DPCT
            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++) {
                Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR); 
                Vector macColorVector = new Vector(mips, RGB0_VECTOR);

                Vector IR;
                Vector subtractedVector = farColorVector.subtractVector(macColorVector);
                Vector limit = new Vector(subtractedVector);
                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);

                limit.data[0] = limA(mips, (int)(limit.data[0] >> sf), 24, false);
                limit.data[1] = limA(mips, (int)(limit.data[1] >> sf), 23, false);
                limit.data[2] = limA(mips, (int)(limit.data[2] >> sf), 22, false);

                long a = limit.multiplyScalar((short)mips.gteReg[8]).data[0];
                limit = limit.multiplyScalar((long)(short)mips.gteReg[8]).addVector(macColorVector);

                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);

                limit = limit.shiftFraction(sf);

                IR = new Vector(mips, -1);
                IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
                IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
                IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

                pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
                mips.gteReg[9] = (int)IR.data[0];
                mips.gteReg[10] = (int)IR.data[1];
                mips.gteReg[11] = (int)IR.data[2];
                mips.gteReg[25] = (int)limit.data[0];
                mips.gteReg[26] = (int)limit.data[1];
                mips.gteReg[27] = (int)limit.data[2];
            }
            break;
        }
        case 0x2D: { // AVSZ3
            long ZSF3 = mips.gteReg[61];
            long SZ0 = mips.gteReg[17];
            long SZ1 = mips.gteReg[18];
            long SZ2 = mips.gteReg[19];
            long MAC0 = ZSF3 * (SZ0 + SZ1 + SZ2);

            mips.gteReg[63] = 0;
            setMac0Flag(mips, MAC0);
            int OTZ = limC(mips, (int)(MAC0 >> 12));

            mips.gteReg[24] = (int)MAC0;
            mips.gteReg[7] = OTZ;
            break;
        }
        case 0x2E: { // AVSZ4
            long ZSF4 = mips.gteReg[62];
            long SZ0 = mips.gteReg[16];
            long SZ1 = mips.gteReg[17];
            long SZ2 = mips.gteReg[18];
            long SZ3 = mips.gteReg[19];
            long MAC0 = ZSF4 * (SZ0 + SZ1 + SZ2 + SZ3);

            mips.gteReg[63] = 0;
            setMac0Flag(mips, MAC0);
            int OTZ = limC(mips, (int)(MAC0 >> 12));

            mips.gteReg[24] = (int)MAC0;
            mips.gteReg[7] = OTZ;
            break;
        }
        case 0x30: { // RTPT
            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++) {
                Vector tv = new Vector(mips, TRANSLATION_VECTOR);
                Vector mv = new Vector(mips, V0_VECTOR + i);
                Matrix mat = new Matrix(mips, ROTATION_MATRIX);
                Vector vector = mat.multiply(mv, tv);
                Vector result = vector.shiftFraction(sf);
                int MAC1 = (int)result.data[0];
                int MAC2 = (int)result.data[1];
                int MAC3 = (int)result.data[2];
                int IR1 = limA(mips, MAC1, 24, lm);
                int IR2 = limA(mips, MAC2, 23, lm);
                int IR3 = limA_SF(mips, MAC3, 22, lm, sf);
                long y = result.data[1];
                
                setMacFlag(mips, 1, vector.data[0]);
                setMacFlag(mips, 2, vector.data[1]);
                setMacFlag(mips, 3, vector.data[2]);

                int SZ = limC(mips, (int)(result.data[2] >> (12 - sf)));
                int division = gteDivide(mips, mips.gteReg[58], SZ);
                long SX = division * (long)(short)IR1 + (long)mips.gteReg[56];
                long SY = division * (long)(short)IR2 + (long)mips.gteReg[57];
                long P = division * (long)(short)mips.gteReg[59] + (long)mips.gteReg[60];
                setMac0Flag(mips, SX);
                setMac0Flag(mips, SY);
                if (i == 2)
                    setMac0Flag(mips, P);
                set_SXYFlag(mips, (int)(SX >> 16), false);
                set_SXYFlag(mips, (int)(SY >> 16), true);

                int MAC0;
                MAC0 = (int)SX;
                int SX2 = (int)clamp64(SX >> 16, -0x400, 0x3FF);
                MAC0 = (int)SY;
                int SY2 = (int)clamp64(SY >> 16, -0x400, 0x3FF);
                int SXY = (int)(SX2 & 0xFFFFL) | (int)(SY2 << 16);
                MAC0 = (int)P;            

                if (i == 2) {
                    int IR0 = limE(mips, P >> 12);
                    writeRegister(mips, 8, IR0);
                }
                writeRegister(mips, 9, IR1);
                writeRegister(mips, 10, IR2);
                writeRegister(mips, 11, IR3);

                // Set the MAC registers
                mips.gteReg[24] = MAC0;
                mips.gteReg[25] = MAC1;
                mips.gteReg[26] = MAC2;
                mips.gteReg[27] = MAC3;

                // Set the SXYZ registers
                writeRegister(mips, 15, SXY);
                mips.gteReg[16] = mips.gteReg[17];
                mips.gteReg[17] = mips.gteReg[18];
                mips.gteReg[18] = mips.gteReg[19];
                mips.gteReg[19] = SZ;
            }
            break;
        }
        case 0x3D: { // GPF
            Vector MAC = new Vector(mips, IR_VECTOR);
            Vector limit = (new Vector(MAC)).multiplyScalar((long)(short)mips.gteReg[8]);
            
            mips.gteReg[63] = 0;
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);
            Vector IR = new Vector(mips, -1);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            break;
        }
        case 0x3E: { // GPL
            Vector MAC = new Vector(mips.gteReg[25], mips.gteReg[26], mips.gteReg[27]);

            for (int i = 0; i < 3; i++)
                MAC.data[i] <<= sf;

            Vector IR = new Vector(mips, IR_VECTOR);
            Vector limit = (new Vector(IR)).multiplyScalar((long)(short)mips.gteReg[8]).addVector(MAC);

            mips.gteReg[63] = 0;
            setMacFlag(mips, 1, limit.data[0]);
            setMacFlag(mips, 2, limit.data[1]);
            setMacFlag(mips, 3, limit.data[2]);
            limit = limit.shiftFraction(sf);
            IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
            IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
            IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);
            pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            mips.gteReg[9] = (int)IR.data[0];
            mips.gteReg[10] = (int)IR.data[1];
            mips.gteReg[11] = (int)IR.data[2];
            mips.gteReg[25] = (int)limit.data[0];
            mips.gteReg[26] = (int)limit.data[1];
            mips.gteReg[27] = (int)limit.data[2];
            break;
        }
        case 0x3F: { // NCCT
            mips.gteReg[63] = 0;
            for (int i = 0; i < 3; i++) {
                Vector v = new Vector(mips, V0_VECTOR + i);
                Vector bk = new Vector(mips, BACKGROUND_COLOR_VECTOR);
                Matrix LLm = new Matrix(mips, LIGHT_MATRIX);
                Matrix LCm = new Matrix(mips, COLOR_MATRIX);
                Vector IR = new Vector();
                Vector RGBC = new Vector(mips, RGBC_VECTOR_SCALED_X4);
                Vector lightResult = LLm.multiply(v, zero);
                Vector farColorVector = new Vector(mips, FAR_COLOR_VECTOR);

                lightResult = lightResult.shiftFraction(sf);
                IR.data[0] = limA(mips, (int)(lightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(lightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(lightResult.data[2]), 22, lm);

                Vector bkLightResult = LCm.multiply(IR, bk);
                bkLightResult = bkLightResult.shiftFraction(sf);

                IR.data[0] = limA(mips, (int)(bkLightResult.data[0]), 24, lm);
                IR.data[1] = limA(mips, (int)(bkLightResult.data[1]), 23, lm);
                IR.data[2] = limA(mips, (int)(bkLightResult.data[2]), 22, lm);

                Vector colorVector = new Vector(IR.data[0] * RGBC.data[0], IR.data[1] * RGBC.data[1], IR.data[2] * RGBC.data[2]);
                Vector limit = new Vector(colorVector);
                setMacFlag(mips, 1, limit.data[0]);
                setMacFlag(mips, 2, limit.data[1]);
                setMacFlag(mips, 3, limit.data[2]);
                limit = limit.shiftFraction(sf);

                IR.data[0] = limA(mips, (int)limit.data[0], 24, lm);
                IR.data[1] = limA(mips, (int)limit.data[1], 23, lm);
                IR.data[2] = limA(mips, (int)limit.data[2], 22, lm);

                mips.gteReg[25] = (int)limit.data[0];
                mips.gteReg[26] = (int)limit.data[1];
                mips.gteReg[27] = (int)limit.data[2];
                mips.gteReg[9] = (int)IR.data[0];
                mips.gteReg[10] = (int)IR.data[1];
                mips.gteReg[11] = (int)IR.data[2];
                pushColorFIFO(mips, (int)limit.data[0] >> 4, (int)limit.data[1] >> 4, (int)limit.data[2] >> 4, mips.gteReg[6] >>> 24);
            }
            break;
        }
        default:
            System.out.println(String.format("UNIMPLEMENTED GTE CMD %08X", cmd));
            System.exit(1);
        }

        mips.PC += 4;
    }
}