package jarpsx.backend;

import java.io.RandomAccessFile;
import jarpsx.backend.IDisk;

public class Disk implements IDisk {
    private String currentDirectoryPath;
    private RandomAccessFile file;
    
    public Disk() {
        currentDirectoryPath = "";
        file = null;
    }

    public boolean loadBinary(String path) {
        try {
            RandomAccessFile file = new RandomAccessFile(path, "r");
            this.file = file;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean readData(byte[] readData, long offset, int size) {
        if (file == null)
            return false;

        try {
            file.seek(offset);
            file.read(readData, 0, size);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
}