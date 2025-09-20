package jarpsx.backend.mips;

public class Instruction {
    private int data;

    public Instruction(int data) {
        this.data = data;
    }

    public int rs() {
        return (data >>> 21) & 0x1F;
    }

    public int rt() {
        return (data >>> 16) & 0x1F;
    }

    public int rd() {
        return (data >>> 11) & 0x1F;
    }

    public int opcode() {
        return (data >>> 26) & 0x3F;
    }
    
    public int imm5() {
        return (data >>> 6) & 0x1F;
    }

    public int functor() {
        return data & 0x3F;
    }
    
    public int target() {
        return data & 0x3FFFFFF;
    }
    
    public int signedImmediate() {
        return (int)(short)(data & 0xFFFF);
    }
  
    public int unsignedImmediate() {
        return data & 0xFFFF;
    }
  
    public int getData() {
        return data;
    }
    
    public void setData(int data) {
        this.data = data;
    }
}