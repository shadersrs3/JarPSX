package jarpsx.backend;

public interface IDisk {
    public boolean readData(byte[] readData, long offset, int size);
}