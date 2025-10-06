import java.nio.file.Path;
import java.nio.file.Paths;

import jarpsx.backend.Emulator;

import jarpsx.backend.mips.MIPS;
import jarpsx.backend.Scheduler;
import jarpsx.frontend.GUI;

public class MainApp {
    public static void main(String[] args) {
        Emulator emu = new Emulator();
        try {
            emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\games\\RidgeRacer\\ridgeracer.bin");
            // emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\cd\\hello_cd.bin");
            emu.loadBIOS(Paths.get("").toAbsolutePath().toString() + "\\data\\SCPH5501.BIN");
        } catch (Exception exception) {
            System.out.println("Can't load BIOS!");
            StackTraceElement[] elements = exception.getStackTrace();
            System.out.println("Stack trace and message: " + exception.getMessage());
            exception.printStackTrace();
        }

        GUI gui = new GUI(emu);

        while (true) {
            long start = System.nanoTime();
            emu.runFor(345000);
            long end = System.nanoTime();

            long elapsedNanoseconds = end - start;
            long msRequired = elapsedNanoseconds / 1000000;
            long us = elapsedNanoseconds / 1000;
            emu.stats.microsecondsRanPerFrame = us;
            if (msRequired <= 16) {
                emu.stats.microsecondsRan += (int) (((float)16 - (float)elapsedNanoseconds / 1000000f) * 1000f);
                try {
                    // Thread.sleep(16 - msRequired, 670000);
                } catch (Exception e) {
                }
            } else {
                emu.stats.microsecondsRan += us;
            }
        }
    }
}