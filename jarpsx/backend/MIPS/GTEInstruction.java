package jarpsx.backend.mips;

public class GTEInstruction {
    private int data;

    public GTEInstruction(int data) {
        this.data = data;
    }

    public int mx() {
        return (this.data >>> 17) & 3;
    }

    public int vx() {
        return (this.data >>> 15) & 3;
    }

    public int tx() {
        return (this.data >>> 13) & 3;
    }

    public int sf() {
        return ((data >>> 19) & 1) * 12;
    }
    
    public boolean lm() {
        return ((data >>> 10) & 1) == 1;
    }
    
    public int command() {
        return data & 0x3F;
    }
    
    public int getData() {
        return data;
    }

    public void setData(int data) {
        this.data = data;
    }
}