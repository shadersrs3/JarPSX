import java.nio.file.Path;
import java.nio.file.Paths;

import jarpsx.backend.Emulator;

import jarpsx.backend.mips.*;
import jarpsx.backend.Scheduler;
import jarpsx.frontend.GUI;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class MainApp {
    public static void main(String[] args) {
        Emulator emu = new Emulator();
        try {
            emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\games\\RidgeRacer\\ridgeracer.bin");
            // emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\games\\mk2\\mk2.bin");
            // emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\games\\crash\\crash.bin");
            // emu.disk.loadBinary(Paths.get("").toAbsolutePath().toString() + "\\data\\executables\\cd\\hello_cd.bin");
            emu.loadBIOS(Paths.get("").toAbsolutePath().toString() + "\\data\\SCPH5501.BIN");
            /*
            int sfBit = 19;
            int command = 0x0C;
            int lmBit = 10;

            RandomAccessFile file = new RandomAccessFile(Paths.get("").toAbsolutePath().toString() + "\\data\\op.txt", "r");
            byte[] bytesofData = new byte[(int)file.length() + 1];
            file.read(bytesofData);
            String s = new String(bytesofData, StandardCharsets.UTF_8);
            bytesofData[(int)file.length()] = '\0';
            
            int offset = 0;
            for (int i = 0; i < 50; i++) {
                String testData = s.substring(offset, 2851+offset);
                testData = testData.substring(2+10, testData.length());
                System.out.printf("Test %d\n", i + 1);
                for (int x = 0; x < 64; x++) {
                    int index;
                    if (x >= 10) {
                        index = x * 22;
                    } else {
                        index = 9 + x * (21);
                    }
                    
                    String registerData = testData.substring(index + 2, index + 10).toUpperCase();
                    int data = Integer.parseUnsignedInt(registerData, 16);
                    GTEInterpreter.writeRegister(emu.mips, x, data);
                }

                int testoff = 64 * 22;
                String sfLm = testData.substring(testoff+3, testoff+13);
                
                int sf = -1, lm = -1;
                
                if (sfLm.equals("sf=0, lm=1")) {
                    sf = 0;
                    lm = 1;
                }

                if (sfLm.equals("sf=0, lm=0")) {
                    sf = 0;
                    lm = 0;
                }

                if (sfLm.equals("sf=1, lm=0")) {
                    sf = 1;
                    lm = 0;
                }

                if (sfLm.equals("sf=1, lm=1")) {
                    sf = 1;
                    lm = 1;
                }

                GTEInterpreter.execute(emu.mips, new GTEInstruction(sf << sfBit | lm << lmBit | command));
                testoff += 34;
                boolean failed = false;
                for (int x = 0; x < 64; x++) {
                    int index;
                    if (x >= 10) {
                        index = x * 22;
                    } else {
                        index = 9 + x * (21);
                    }
                    
                    String registerData = testData.substring(testoff+index+2, testoff+index + 10).toUpperCase();
                    int mine = GTEInterpreter.readRegister(emu.mips, x);
                    int theirs = Integer.parseUnsignedInt(registerData, 16);
                    if (mine != theirs) {
                        System.out.printf("BAD r%d EXPECTED %s MINE %08X\n", x, registerData, mine);
                        failed = true;
                    } else
                        System.out.printf("OK r%d = %s\n", x, registerData);
                }

                if (failed) {
                    System.out.printf("Bad shit\n");
                    System.exit(1);
                }

                offset += 2852;
            }
            file.close();
            */
        } catch (Exception exception) {
            System.out.println("Can't load BIOS!");
            StackTraceElement[] elements = exception.getStackTrace();
            System.out.println("Stack trace and message: " + exception.getMessage());
            exception.printStackTrace();
            System.exit(1);
        }

        // GUI gui = new GUI(emu);

        while (true) {
            long start = System.nanoTime();
            emu.runFor(346000);
            long end = System.nanoTime();

            long elapsedNanoseconds = end - start;
            long msRequired = elapsedNanoseconds / 1000000;
            long us = elapsedNanoseconds / 1000;
            emu.stats.microsecondsRanPerFrame = us;
            emu.stats.microsecondsRan += (int) (((float)16 - (float)elapsedNanoseconds / 1000000f) * 1000f);
            if (msRequired <= 16) {
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