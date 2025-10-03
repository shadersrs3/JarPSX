import java.nio.file.Path;
import java.nio.file.Paths;

import jarpsx.backend.Emulator;

import jarpsx.backend.mips.MIPS;
import jarpsx.backend.Scheduler;

public class MainApp {
    public static void main(String[] args) {
        Emulator emu = new Emulator();
        try {
            emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\games\\RidgeRacer\\ridgeracer.bin");
            emu.loadBIOS(Paths.get("").toAbsolutePath().toString() + "\\data\\SCPH1001.BIN");
        } catch (Exception exception) {
            System.out.println("Can't load BIOS!");
            StackTraceElement[] elements = exception.getStackTrace();
            System.out.println("Stack trace and message: " + exception.getMessage());
            exception.printStackTrace();
        }

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
                //Thread.sleep(16 - msRequired, 670000);
            } else {
                emu.stats.microsecondsRan += us;
            }
        }

        /*
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GUI gui = new GUI();
                gui.emulator = emu;
                gui.showMainUI();
            }
        });
        */
    }
}