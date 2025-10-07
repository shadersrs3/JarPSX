package jarpsx.backend.component;

import java.awt.image.BufferedImage;

import jarpsx.backend.Emulator;
import jarpsx.backend.component.InterruptController;
import java.awt.image.DataBufferInt;

public class GPU {
    private Emulator emulator;
    private BufferedImage vram;
    private BufferedImage framebuffer;

    public static final int RENDER_NONE = 0b00;
    public static final int RENDER_POLYGON = 0b001;
    public static final int RENDER_LINE = 0b010;
    public static final int RENDER_RECTANGLE = 0b11;

    public static final int MEMORY_TRANSFER_VRAM_TO_VRAM = 0b100;
    public static final int MEMORY_TRANSFER_CPU_TO_VRAM = 0b101;
    public static final int MEMORY_TRANSFER_VRAM_TO_CPU = 0b110;
    public static final int MEMORY_TRANSFER_FILL_VRAM = 69;

    private int[] vramData;
    public int texpage;
    public int textureWindowSetting;
    public int maskBitSetting;
    public int dmaDirection;
    public int displayMode;
    public int displayAreaStart;
    public int horizontalScreenDisplayRange;
    public int verticalScreenDisplayRange;
    public int renderType;
    public int displayEnable;
    public int drawingAreaX1;
    public int drawingAreaX2;
    public int drawingAreaY1;
    public int drawingAreaY2;

    public PolygonInfo currentPolygonInfo;
    public RectangleInfo currentRectangleInfo;
    public int currentState;
    public int currentRenderCount;
    public int color;
    public int vertex;
    public int uv;
    public int rectangleWidth, rectangleHeight;

    public int destinationCoord;
    public int sourceCoord;
    public int widthHeight;

    public int targetXPosition;
    public int targetYPosition;
    public int initialXPosition;
    public int initialYPosition;
    public int currentXPosition;
    public int currentYPosition;
    public int sizeDecrement;
    public int destinationTargetXPosition;
    public int destinationTargetYPosition;
    public int destinationInitialXPosition;
    public int destinationInitialYPosition;
    public int destinationCurrentXPosition;
    public int destinationCurrentYPosition;

    public int vramToCpuTargetXPosition;
    public int vramToCpuTargetYPosition;
    public int vramToCpuInitialXPosition;
    public int vramToCpuInitialYPosition;
    public int vramToCpuCurrentXPosition;
    public int vramToCpuCurrentYPosition;
    public int vramToCpuSizeDecrement;

    public int drawOffsetX;
    public int drawOffsetY;

    public int GPUREAD;

    private class PolygonData {
        int color; int vertex; int uv;
        PolygonData(int color, int vertex, int uv) {
            this.color = color;
            this.vertex = vertex;
            this.uv = uv;
        }
    }

    private class PolygonInfo {
        PolygonData[] data;
        int index;
        boolean gouraudShading;
        boolean textured;
        boolean semiTransparent;
        boolean rawTexture;
        boolean fourVertices;
        int command;
        PolygonInfo(int data) {
            gouraudShading = ((data >>> 28) & 1) != 0;
            fourVertices = ((data >>> 27) & 1) != 0;
            textured = ((data >>> 26) & 1) != 0;
            semiTransparent = ((data >>> 25) & 1) != 0;
            rawTexture = ((data >>> 24) & 1) != 0;
            this.data = new PolygonData[4];
            command = data >>> 24;
            index = 0;
        }

        public void addPolygon(PolygonData data) {
            this.data[index] = data;
            index++;
        }
    }

    private class Rectangle {
        int color;
        int width, height;
        int uv;
        Rectangle(int color, int width, int height, int uv) {
            this.color = color;
            this.width = width;
            this.height = height;
            this.uv = uv;
        }
    }

    private class RectangleInfo {
        Rectangle data;
        boolean textured;
        boolean semiTransparent;
        boolean rawTexture;
        int size;
        int command;
        RectangleInfo(int data) {
            size = ((data >>> 27) & 3);
            textured = ((data >>> 26) & 1) != 0;
            semiTransparent = ((data >>> 25) & 1) != 0;
            rawTexture = ((data >>> 24) & 1) != 0;
            command = data >>> 24;
        }

        public void addRectangle(Rectangle data) {
            this.data = data;
        }
    }

    public GPU(Emulator emulator) {
        this.emulator = emulator;
        vram = new BufferedImage(1024, 512, BufferedImage.TYPE_INT_ARGB); // disgusting fucking piece of waste of memory but works anyway
        vramData = new int[1024 * 512];
        currentState = 0;
        GPUREAD = 0;
    }

    private int orient2D(int v1x, int v1y, int v2x, int v2y, int v3x, int v3y) {
        return (v2x - v1x) * (v3y - v1y) - (v3x - v1x) * (v2y - v1y);
    }
    
    public void drawPixel(int x, int y, int color) {
        if (x < 0 || x >= drawingAreaX2 || x >= 1024)
            return;
        if (y < 0 || y >= drawingAreaY2 || y >= 512) {
            return;
        }

        writeVram16(x, y, color);
    }

    public int lookupTexture(int texPageX, int texPageY, int u, int v, int pageColor, int clutAttribute) {
        texPageX *= 64;
        texPageY *= 256;
        int color = 0;
        int paletteAddressX = (clutAttribute & 0x3F);
        int paletteAddressY = (clutAttribute >>> 6) & 511;
        int textureAddressY;
        int textureAddressX;
        int word;
        int paletteIndex;

        switch (pageColor) {
        case 0:
            textureAddressY = texPageY + v;
            textureAddressX = texPageX + (u >> 2);
            word = readVram16(textureAddressX, textureAddressY);
            paletteIndex = (word >>> ((u & 3) * 4)) & 0xF;
            color = readVram16(paletteAddressX * 16 + paletteIndex, paletteAddressY);
            break;
        case 1:
            textureAddressY = texPageY + v;
            textureAddressX = texPageX + (u >> 1);
            word = readVram16(textureAddressX, textureAddressY);
            paletteIndex = (word >>> ((u & 1) * 8)) & 0xFF;
            color = readVram16(paletteAddressX * 16 + paletteIndex, paletteAddressY);
            break;
        case 2:
        case 3:
            textureAddressY = texPageY + v;
            textureAddressX = texPageX + u;
            color = readVram16(textureAddressX, textureAddressY);
            break;
        default:
            System.out.printf("Unimplemented page color %X\n", pageColor);
            System.exit(1);
            break;
        }
        
        return color;
    }

    private int signExtend(int data) {
        if ((data & (1 << 10)) != 0) {
            data = -1024;
        } else {
            data &= 0x3FF;
        }
        return data;
    }

    public void drawPolygon(PolygonInfo info, int index) {
        int texpageX = (info.data[1].uv >>> 16) & 0xF;
        int texpageY = (info.data[1].uv >>> (16+4)) & 0x1;
        int colorDepth = (info.data[1].uv >>> (16+7)) & 3;

        PolygonData v1 = info.data[index + 0];
        PolygonData v2 = info.data[index + 1];
        PolygonData v3 = info.data[index + 2];
        int v1x = signExtend((short)(v1.vertex & 0xFFFF));
        int v2x = signExtend((short)(v2.vertex & 0xFFFF));
        int v3x = signExtend((short)(v3.vertex & 0xFFFF));
        int v1y = signExtend((short)((v1.vertex >>> 16) & 0xFFFF));
        int v2y = signExtend((short)((v2.vertex >>> 16) & 0xFFFF));
        int v3y = signExtend((short)((v3.vertex >>> 16) & 0xFFFF));

        v1x += drawOffsetX;
        v2x += drawOffsetX;
        v3x += drawOffsetX;
        v1y += drawOffsetY;
        v2y += drawOffsetY;
        v3y += drawOffsetY;

        if (orient2D(v1x, v1y, v2x, v2y, v3x, v3y) < 0) {
            int tempX = v2x;
            int tempY = v2y;
            PolygonData temp = v2;
            
            v2x = v3x;
            v2y = v3y;
            v3x = tempX;
            v3y = tempY;
            v2 = v3;
            v3 = temp;
        }
                
        int v1s = v1.uv & 0xFF;
        int v2s = v2.uv & 0xFF;
        int v3s = v3.uv & 0xFF;
        int v1t = (v1.uv >> 8) & 0xFF;
        int v2t = (v2.uv >> 8) & 0xFF;
        int v3t = (v3.uv >> 8) & 0xFF;

        int minX = Integer.min(v1x, Integer.min(v2x, v3x));
        int maxX = Integer.max(v1x, Integer.max(v2x, v3x));
        int minY = Integer.min(v1y, Integer.min(v2y, v3y));
        int maxY = Integer.max(v1y, Integer.max(v2y, v3y));
    
        int A12 = v1y - v2y;
        int B12 = v2x - v1x;
        int A23 = v2y - v3y;
        int B23 = v3x - v2x;
        int A31 = v3y - v1y;
        int B31 = v1x - v3x;
        
        int w1Row = orient2D(v2x, v2y, v3x, v3y, minX, minY);
        int w2Row = orient2D(v3x, v3y, v1x, v1y, minX, minY);
        int w3Row = orient2D(v1x, v1y, v2x, v2y, minX, minY);
        int r1, r2, r3, b1, b2, b3, g1, g2, g3;
        if (info.gouraudShading) {
            r1 = v1.color & 0xFF;
            r2 = v2.color & 0xFF;
            r3 = v3.color & 0xFF;

            g1 = (v1.color >> 8) & 0xFF;
            g2 = (v2.color >> 8) & 0xFF;
            g3 = (v3.color >> 8) & 0xFF;

            b1 = (v1.color >> 16) & 0xFF;
            b2 = (v2.color >> 16) & 0xFF;
            b3 = (v3.color >> 16) & 0xFF;
        } else {
            r1 = info.data[0].color & 0xFF;
            r2 = info.data[0].color & 0xFF;
            r3 = info.data[0].color & 0xFF;
            g1 = (info.data[0].color >> 8) & 0xFF;
            g2 = (info.data[0].color >> 8) & 0xFF;
            g3 = (info.data[0].color >> 8) & 0xFF;
            b1 = (info.data[0].color >> 16) & 0xFF;
            b2 = (info.data[0].color >> 16) & 0xFF;
            b3 = (info.data[0].color >> 16) & 0xFF;
        }

        int divider = orient2D(v1x, v1y, v2x, v2y, v3x, v3y);
        boolean transparent = false;
        for (int y = minY; y <= maxY; y++) {
            int w1 = w1Row;
            int w2 = w2Row;
            int w3 = w3Row;
            for (int x = minX; x <= maxX; x++) {
                if ((w1 | w2 | w3) >= 0)
                {
                    int r = (int)(((float) r1 * w1 + (float) r2 * w2 + (float) r3 * w3) / (float)divider);
                    int g = (int)(((float) g1 * w1 + (float) g2 * w2 + (float) g3 * w3) / (float)divider);
                    int b = (int)(((float) b1 * w1 + (float) b2 * w2 + (float) b3 * w3) / (float)divider);

                    if (info.textured) {
                        int u = (int)(((float) v1s * w1 + (float) v2s * w2 + (float) v3s * w3) / divider);
                        int v = (int)(((float) v1t * w1 + (float) v2t * w2 + (float) v3t * w3) / divider);
                        int color = lookupTexture(texpageX, texpageY, u, v, colorDepth, info.data[0].uv >>> 16);
                        r = (color & 0x1F) << 3;
                        g = ((color >> 5) & 0x1F) << 3;
                        b = ((color >> 10) & 0x1F) << 3;
                        transparent = color == 0;
                    }

                    if (transparent == false) {
                        r /= 8;
                        g /= 8;
                        b /= 8;
                        int color = (r & 0x1F) | ((g & 0x1F) << 5) | ((b & 0x1F) << 10);
                        drawPixel(x, y, color);
                    }
                }
                w1 += A23;
                w2 += A31;
                w3 += A12;
            }
            w1Row += B23;
            w2Row += B31;
            w3Row += B12;
        }
    }

    public void present() {
        int[] vramBuffer = ((DataBufferInt)vram.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < 1024 * 512; i++) {
            int data = vramData[i];
            int argb = 0xFF_00_00_00;
            int red = data & 0x1F;
            int green = (data >>> 5) & 0x1F;
            int blue = (data >>> 10) & 0x1F;
            argb |= (red) << 16 | (green) << 8 | (blue);
            vramBuffer[i] = 0xFF00_0000 | red << (16+3) | (green << (8+3)) | (blue << (0+3));
        }
    }

    public int readVram4(int x, int y) {
        boolean upper = (x & 1) != 0;
        int data = readVram8(x >> 1, y);

        if (upper) {
            data >>= 4;
        } else {
            data &= 0xF;
        }
        return data;
    }

    public int readVram8(int x, int y) {
        int offset = (x >> 1) + (y << 8);
        int data = vramData[offset];
        if ((x & 1) == 1)
            data >>= 8;
        return data & 0xFF;
    }

    public void writeVram8(int x, int y, int value) {
        int offset = (x >> 1) + (y << 8);
        int data = vramData[offset];
        if ((x & 1) == 0) {
            vramData[offset] = (data & 0xFF00) | (value & 0xFF);
        } else {
            vramData[offset] = (data & 0xFF) | ((value & 0xFF) << 8);
        }
    }

    public int readVram16(int x, int y) {
        return vramData[y * 1024 + x];
    }

    public void writeVram16(int x, int y, int value) {
        vramData[y * 1024 + x] = value & 0xFFFF;
    }

    public int readVram32(int x, int y) {
        return 0;
    }

    public void writeVram32(int x, int y, int value) {
        int offset = ((x & ~3) << 1) + (y << 8);
        int _x = x & 3;
        x &= ~3;
        switch (_x) {
        case 0:
            vramData[offset] = value & 0xFFFF;
            vramData[offset + 1] = (value >>> 16) & 0xFFFF;
            break;
        case 1:
            writeVram16(x * 2 + 1, y, value);
            writeVram16(x * 2 + 2, y, (value >>> 16));
            break;
        case 2:
            writeVram16(x * 2 + 2, y, value);
            writeVram16(x * 2 + 3, y, (value >>> 16));
            break;
        case 3:
            writeVram16(x * 2 + 3, y, value);
            writeVram16(x * 2 + 4, y, (value >>> 16));
            break;
        }
    }
    
    public int readGpuStat() {
        int dma = 0;
        switch (renderType) {
        case MEMORY_TRANSFER_CPU_TO_VRAM:
            dma |= (1 << 26) | (1 << 27) | (1 << 28);
            break;
        case MEMORY_TRANSFER_VRAM_TO_CPU:
            dma |= (1 << 26) | (1 << 27) | (1 << 28);
            break;
        default:
            dma |= (1 << 26) | (1 << 27) | (1 << 28);
        }

        dma |= 1 << 31;
        return (texpage & 0x3FF) | dmaDirection << 29 | dma;
    }
    
    public int readGpuRead() {
        int data;
        if (--vramToCpuSizeDecrement >= 0) {
            data = readVram16(vramToCpuCurrentXPosition, vramToCpuCurrentYPosition) | readVram16(vramToCpuCurrentXPosition + 1, vramToCpuCurrentYPosition) << 16;
            vramToCpuCurrentXPosition += 2;
            if (vramToCpuCurrentXPosition >= vramToCpuTargetXPosition) {
                vramToCpuCurrentXPosition = vramToCpuInitialXPosition;
                ++vramToCpuCurrentYPosition;
            }

            if (vramToCpuSizeDecrement == 0)
                renderType = 0;
            return data;
        }

        return GPUREAD;
    }
  
    public void writeGp0(int data) {
        int command = data >>> 24;

        switch (renderType) {
        case RENDER_POLYGON:
            // System.out.printf("Render polygon\n");
            int renderCount = currentRenderCount;
            switch (currentState) {
            case 1:
                vertex = data;
                if (currentPolygonInfo.textured || currentPolygonInfo.rawTexture) {
                    currentState = 2;
                } else {
                    currentRenderCount--;
                    if (currentPolygonInfo.gouraudShading) {
                        currentState = 3;
                    }
                }
                break;
            case 2:
                uv = data;
                currentRenderCount--;
                if (currentPolygonInfo.gouraudShading) {
                    currentState = 3;
                } else {
                    currentState = 1;
                }

                break;
            case 3:
                color = data;
                currentState = 1;
                break;
            }

            boolean addPolygonData = renderCount != currentRenderCount;
            if (addPolygonData)
                currentPolygonInfo.addPolygon(new PolygonData(color, vertex, uv));

            if (currentRenderCount == 0) {
                color = 0;
                vertex = 0;
                uv = 0;
                renderType = 0;

                drawPolygon(currentPolygonInfo, 0);
                if (currentPolygonInfo.fourVertices)
                    drawPolygon(currentPolygonInfo, 1);
            }
            return;
        case MEMORY_TRANSFER_CPU_TO_VRAM:
            switch (currentState) {
            case 1:
                initialXPosition = (data & 0x3FF);
                initialYPosition = ((data >>> 16) & 0x1FF);
                currentState = 2;
                break;
            case 2: {
                int xsiz = data & 0xFFFF;
                int ysiz = data >>> 16;
                xsiz = ((xsiz - 1) & 0x3FF) + 1;
                ysiz = ((ysiz - 1) & 0x1FF) + 1;
                int sizeDecrement = xsiz * ysiz;
                if ((sizeDecrement & 1) != 0)
                    sizeDecrement = (sizeDecrement + 1) >>> 1;
                else
                    sizeDecrement >>>= 1;
                
                this.sizeDecrement = sizeDecrement;
                targetXPosition = initialXPosition + xsiz;
                targetYPosition = initialYPosition + ysiz;
                currentXPosition = initialXPosition;
                currentYPosition = initialYPosition;
                currentState = 3;
                break;
            }
            case 3:
                if (--sizeDecrement >= 0) {
                    writeVram16(currentXPosition, currentYPosition, data);
                    writeVram16(currentXPosition + 1, currentYPosition, ((data >>> 16) & 0xFFFF));
                    currentXPosition += 2;
                    if (currentXPosition >= targetXPosition) {
                        currentXPosition = initialXPosition;
                        if (++currentYPosition >= targetYPosition) {
                        }
                    }
                } else {
                    renderType = 0;
                }
                break;
            }
            return;
        case MEMORY_TRANSFER_VRAM_TO_VRAM:
            switch (currentState) {
            case 1:
                initialXPosition = (data & 0x3FF);
                initialYPosition = ((data >>> 16) & 0x1FF);
                currentState = 2;
                break;
            case 2:
                destinationInitialXPosition = (data & 0x3FF);
                destinationInitialYPosition = ((data >>> 16) & 0x1FF);
                currentState = 3;
                break;
            case 3:
                int xsiz = data & 0xFFFF;
                int ysiz = data >>> 16;
                xsiz = ((xsiz - 1) & 0x3FF) + 1;
                ysiz = ((ysiz - 1) & 0x1FF) + 1;
                int sizeDecrement = xsiz * ysiz;
                if ((sizeDecrement & 1) != 0)
                    sizeDecrement = (sizeDecrement + 1) >>> 1;
                else
                    sizeDecrement >>>= 1;
                renderType = 0;
                break;
            }
            return;
        case MEMORY_TRANSFER_VRAM_TO_CPU:
            switch (currentState) {
            case 1:
                vramToCpuInitialXPosition = (data & 0x3FF);
                vramToCpuInitialYPosition = ((data >>> 16) & 0x1FF);
                currentState = 2;
                break;
            case 2: {
                int xsiz = data & 0xFFFF;
                int ysiz = data >>> 16;
                xsiz = ((xsiz - 1) & 0x3FF) + 1;
                ysiz = ((ysiz - 1) & 0x1FF) + 1;
                int sizeDecrement = xsiz * ysiz;
                if ((sizeDecrement & 1) != 0)
                    sizeDecrement = (sizeDecrement + 1) >>> 1;
                else
                    sizeDecrement >>>= 1;

                vramToCpuSizeDecrement = sizeDecrement;
                vramToCpuTargetXPosition = vramToCpuInitialXPosition + xsiz;
                vramToCpuTargetYPosition = vramToCpuInitialYPosition + ysiz;
                vramToCpuCurrentXPosition = vramToCpuInitialXPosition;
                vramToCpuCurrentYPosition = vramToCpuInitialYPosition;
                renderType = 0;
                break;
            }
            }
            return;
        case MEMORY_TRANSFER_FILL_VRAM:
            switch (currentState) {
            case 1:
                currentState = 2;
                break;
            case 2:
                renderType = 0;
                break;
            }
            break;
        case RENDER_RECTANGLE: {
            boolean ableToDraw = false;
            switch (currentState) {
            case 1:
                vertex = data;
                if (currentRectangleInfo.textured) {
                    currentState = 2;
                } else {
                    if (currentRectangleInfo.size == 0) {
                        currentState = 3;
                    } else {
                        ableToDraw = true;
                    }
                }
                break;
            case 2:
                if (currentRectangleInfo.size != 0) {
                    ableToDraw = true;
                } else {
                    currentState = 3;
                }
                break;
            case 3:
                ableToDraw = true;
                break;
            }
            
            if (ableToDraw) {
                // System.out.printf("UNIMPLEMENTED DRAW RECTANGLE\n");
                renderType = 0;
            }
            return;
        }
        }

        switch (data >>> 29) {
        case RENDER_POLYGON:
            currentPolygonInfo = new PolygonInfo(data);
            if (currentPolygonInfo.fourVertices) {
                currentRenderCount = 4;
            } else {
                currentRenderCount = 3;
            }

            currentState = 1;
            this.renderType = RENDER_POLYGON;
            color = data & 0xFFFFFF;
            return;
        case RENDER_RECTANGLE:
            this.renderType = RENDER_RECTANGLE;
            currentRectangleInfo = new RectangleInfo(data);
            currentState = 1;
            return;
        case MEMORY_TRANSFER_CPU_TO_VRAM:
            this.renderType = MEMORY_TRANSFER_CPU_TO_VRAM;
            currentState = 1;
            return;
        case MEMORY_TRANSFER_VRAM_TO_VRAM:
            this.renderType = MEMORY_TRANSFER_VRAM_TO_VRAM;
            currentState = 1;
            return;
        case MEMORY_TRANSFER_VRAM_TO_CPU:
            this.renderType = MEMORY_TRANSFER_VRAM_TO_CPU;
            currentState = 1;
            return;
        }

        switch (command) {
        case 0x02:
            renderType = MEMORY_TRANSFER_FILL_VRAM;
            currentState = 1;
            break;
        case 0x0C:
        case 0x00: // nop
            break;
        case 0x01:
            break;
        case 0xE1: // Texpage gpustat relies on this
            texpage = data;
            break;
        case 0xE2: // texture window
            textureWindowSetting = data;
            break;
        case 0xE3: // set drawing area top left
            drawingAreaX1 = data & 1023;
            drawingAreaY1 = (data >> 10) & 511;
            break;
        case 0xE4: // set drawing area bottom right
            drawingAreaX2 = data & 1023;
            drawingAreaY2 = (data >> 10) & 511;
            break;
        case 0xE5: // drawing offset
            drawOffsetX = ((short)((data & 0x7FF) << 4)) >> 4;
            drawOffsetY = ((short)(((data >>> 11) & 0x7FF) << 4)) >> 4;
            break;
        case 0xE6: // mask bit setting gpu stat relies on this
            maskBitSetting = data;
            break;
        default:
            System.out.printf("Unimplemented GP0 command %02X\n", command);
            //System.exit(1);
        }
    }
    
    public void writeGp1(int data) {
        int command = data >>> 24;
        switch (command) {
        case 0x00:
            System.out.printf("RESET GPU UNIMPLEMENTED\n");
            break;
        case 0x01: // reset command buffer
            renderType = 0;
            break;
        case 0x02: // acknowledge irq gpustat relies on this
            break;
        case 0x03: // gpustat relies on this
            displayEnable = data & 1;
            break;
        case 0x04: // Dma direction gpustat relies on this
            dmaDirection = data & 3;
            break;
        case 0x05: // Start of display area in VRAM
            displayAreaStart = data;
            break;
        case 0x06:
            horizontalScreenDisplayRange = data;
            break;
        case 0x07:
            verticalScreenDisplayRange = data;
            break;
        case 0x08:
            displayMode = data;
            break;
        case 0x10:
            GPUREAD = 0;
            break;
        default:
            System.out.printf("Unimplemented GP1 command %02X\n", command);
            System.exit(1);
        }
        // System.out.printf("write gp1 %08X\n", command);
    }
        
    public BufferedImage getVram() {
        return vram;
    }

    public BufferedImage getFramebuffer() {
        return framebuffer;
    }
}