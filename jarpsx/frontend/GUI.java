package jarpsx.frontend;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.*;
import java.awt.event.*;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Graphics;
import java.awt.Color;
import java.util.TimerTask;
import java.util.Timer;
import java.time.Duration;
import java.lang.Thread;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Container;
import javax.swing.border.TitledBorder;

import javax.swing.text.*;
import jarpsx.backend.Emulator;
import jarpsx.backend.mips.Disassembler;
import javax.swing.plaf.basic.BasicScrollBarUI;

import javax.swing.event.*;

import jarpsx.backend.Emulator;

class GUI {
    private JFrame frame;
    private JFrame ttyLog;
    private JTextArea ttyLogTextArea;
    private JFrame cpuDebugger;
    private JPanel currentRegisterPanel;

    public Emulator emulator;
    int ctr = 0;

    private static final int WINDOW_WIDTH = 640, WINDOW_HEIGHT = 480;

    class MinThumbScrollBarUI extends BasicScrollBarUI {
        private final Dimension minSize;
        public MinThumbScrollBarUI(Dimension minSize) { this.minSize = minSize; }

        @Override
        protected Dimension getMinimumThumbSize() {
            return minSize;
        }
    }

    class GamePanel extends JPanel {
        Timer backgroundRepaintTimer;
        GamePanel() {
            super();
            Timer backgroundRepaintTimer = new Timer("Paint Component Update");
        
            backgroundRepaintTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    repaint();
                }
            }, 100, 16);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            // super.paintComponent(g);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

            int NEWLINE = 15;
            int currentLine = 15;
            
            g.setColor(new Color(100, 100, 255, 255));
            if (ctr < 100)
                ttyLogTextArea.append(String.format("hello aaaaaaaaaaaaaaaaaaa %d\n", ++ctr));
            
            g.drawString(String.format("Ran for %d secs", emulator.stats.microsecondsRan / 1000000), 5, currentLine);
            currentLine += NEWLINE;
            g.drawString(String.format("Emulator elapsed us: %.3fus", (float)emulator.stats.microsecondsRanPerFrame), 5, currentLine);
            currentLine += NEWLINE;
            g.drawString("Latest Raised exception: None", 5, currentLine);
        }
    }

    class MenuItem extends JMenuItem implements ActionListener {
        MenuItem(String str) { super(str); }

        public void actionPerformed(ActionEvent e) {
            switch (e.getActionCommand()) {
            case "Load Game":
                System.out.println("Load game called");
                break;
            case "CPU Debugger":
                cpuDebugger.setVisible(true);
                break;
            case "TTY Log":
                ttyLog.setVisible(true);
                break;
            }
        }
    }

    class LimitedDocument extends PlainDocument {
        private int max;

        public LimitedDocument(int max) {
            this.max = max;
        }

        @Override
        public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
            if (str == null) return;

            if (getLength() + str.length() <= max) {
                super.insertString(offset, str.toUpperCase(), attr);
            }
        }
    }

    class DebuggerRegisterField extends JTextField implements ActionListener {
        DebuggerRegisterField(String str) { super(str); }

        public void actionPerformed(ActionEvent e) {
            System.out.println("action performed");
        }
    }

    class DebuggerDisassemblyView extends JScrollPane implements AdjustmentListener {
        class DebuggerMouseListener extends MouseAdapter {
            private boolean latch;
            private int address;
            JLabel label;

            DebuggerMouseListener(JLabel label) {
                latch = false;
                address = 0;
                this.label = label;
            }

            public void setAddress(int address) {
                this.address = address;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() % 2 == 0) {
                    if (latch == false) {
                        System.out.println(String.format("%08X", address));
                        label.setBackground(Color.red);
                        label.setForeground(Color.black);
                        latch = true;
                    } else {
                        label.setBackground(getBackgroundColor());
                        label.setForeground(getForegroundColor());
                        latch = false;
                    }
                }
            }
        }
        class LabelViewer {
            private JLabel[] label;
            private int defaultScrollValue;
            private int currentScrollValue;
            private int startLabelIndex;
            private int heightByLabel;

            LabelViewer(int labelCount, int defaultScrollValue, int startLabelIndex, int heightByLabel) {
                this.defaultScrollValue = defaultScrollValue;
                currentScrollValue = 0;
                this.startLabelIndex = startLabelIndex;
                this.heightByLabel = heightByLabel;
                label = new JLabel[labelCount];
            }

            public void setLabel(int index, JLabel label) {
                this.label[index] = label;
            }

            public void scroll(int amount) {
                if (amount < 100)
                    return;
                currentScrollValue += amount - defaultScrollValue;
            }
            
            public void gotoPC(int address) {
                currentScrollValue = (-heightByLabel / 2 + 1) * 8;
                updateTextRegionByStartLabelIndex(address);
            }

            public void updateTextRegionByStartLabelIndex(int address) {
                for (int i = startLabelIndex; i < label.length && i < startLabelIndex + heightByLabel; i++) {
                    int currentAddress = (address - startLabelIndex * 4) + i * 4 + currentScrollValue / 8 * 4;
                    String instruction = getDisassembledInstruction(currentAddress);

                    ((DebuggerMouseListener)label[i].getMouseListeners()[0]).setAddress(currentAddress);
                    label[i].setText(instruction);
                }
            }
        }

        private int currentAddress;
        private LabelViewer labelViewer;
        private int followPC;
        
        DebuggerDisassemblyView(int x, int y, int width, int height) {
            super(new JPanel(), JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            // 220, 35, 480, 600
            initializePanelView((JPanel) this.getViewport().getView(), x, y, width, height);
            setBounds(x, y, width, height);
            setBorder(BorderFactory.createTitledBorder("Disassembly"));
            getVerticalScrollBar().setValue(4000);
            getVerticalScrollBar().addAdjustmentListener(this);
            followPC = 0xBFC00000;
        }
                
        public static Color getBackgroundColor() {
            return new Color(0xb8, 0xdc, 0xee);
        }
        
        public static Color getForegroundColor() {
            return new Color(0x00, 0x00, 0x00);
        }
        
        private void initializePanelView(JPanel disassemblerPanel, int x, int y, int width, int height) {
            disassemblerPanel.setLayout(null);
            disassemblerPanel.setBounds(x, y, width, height);
            disassemblerPanel.setBackground(getBackgroundColor());
            disassemblerPanel.setPreferredSize(new Dimension(800, 10000));
            
            labelViewer = new LabelViewer(0x100, 4000, 0xC7, 30);
            for (int i = 0; i < 0x100; i++) {
                JLabel disassemblerLabel;
                String instruction;
                disassemblerLabel = new JLabel("");
                disassemblerLabel.setBackground(getBackgroundColor());
                disassemblerLabel.setForeground(getForegroundColor());
                disassemblerLabel.setOpaque(true);
                disassemblerLabel.setBounds(5, 20 + i * 20, width - 50, 20);

                disassemblerLabel.addMouseListener(new DebuggerMouseListener(disassemblerLabel));

                Font font = disassemblerLabel.getFont();
                Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
                disassemblerLabel.setFont(boldFont);
                disassemblerPanel.add(disassemblerLabel);
                labelViewer.setLabel(i, disassemblerLabel);
            }

            currentAddress = 0xBFC00000;
        }
        
        public void adjustmentValueChanged(AdjustmentEvent e) {
            int scrollAmount = getVerticalScrollBar().getValue();
            labelViewer.updateTextRegionByStartLabelIndex(followPC);
            labelViewer.scroll(scrollAmount);
            getVerticalScrollBar().setValue(4000);
            // System.out.println(scrollPane.getVerticalScrollBar().getValue());
        }
    }

    private String getDisassembledInstruction(int addr) {
        String instruction;
        int data = 0;
        boolean invalidInstruction = true;
        try {
            data = emulator.memory.readInt(addr);
            invalidInstruction = false;
        } catch (RuntimeException runtimeException) {
            data = -1;
        } finally {
            if (invalidInstruction == true) {
                instruction = String.format("UNKNOWN ADDRESS 0x%08X", addr);
            } else {
                instruction = String.format("0x%08X: ", addr) + Disassembler.disassemble(data, addr, true);
            }
        }
        return "  " + instruction;
    }

    public void showMainUI() {
        try { UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel"); } catch (Exception e) {} // hack change to nimbus for linux/macos
        // try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); } catch (Exception e) {} // hack change to nimbus for linux/macos
        frame = new JFrame("JarPSX Emulator");

        JMenuBar menuBar;
        JMenu menu;
        MenuItem menuItem;

        menuBar = new JMenuBar();

        // File has menu items as this: Load Game, Reset, Back to main menu
        menu = new JMenu("File");

        menuItem = new MenuItem("Load Game");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuItem = new MenuItem("Reset");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuItem = new MenuItem("Back to main menu");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuBar.add(menu);

        // Debug has menu items as this: CPU Debugger, Memory Viewer, TTY Log
        menu = new JMenu("Debug");

        menuItem = new MenuItem("CPU Debugger");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuItem = new MenuItem("Memory Viewer");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuItem = new MenuItem("TTY Log");
        menuItem.addActionListener(menuItem);
        menu.add(menuItem);

        menuBar.add(menu);

        // About menu is about my emulator
        menu = new JMenu("About");
        menu.add(new JLabel("Copyright (C) shaders 2025 - JarPSX emulator"));
        menuBar.add(menu);

        frame.setJMenuBar(menuBar);
        frame.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        GamePanel gamePanel = new GamePanel();
        frame.add(gamePanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                ttyLog.dispose();
            }
        });

        ttyLog = new JFrame("TTY Log");
        ttyLogTextArea = new JTextArea();
        ttyLogTextArea.setEditable(false);
        ttyLogTextArea.setLineWrap(true);
        ttyLogTextArea.setRows(30);
        ttyLogTextArea.setColumns(50);
        ttyLogTextArea.setBackground(Color.black);
        ttyLogTextArea.setCaretColor(Color.white);
        ttyLogTextArea.setForeground(Color.white);
        ttyLogTextArea.setFont(ttyLogTextArea.getFont().deriveFont(15f));

        JScrollPane scroll = new JScrollPane(ttyLogTextArea,  JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        ttyLog.add(scroll);

        ttyLog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ttyLog.pack();

        ttyLog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                ttyLog.setVisible(false);
            }
        });

        cpuDebugger = new JFrame("CPU Debugger");
        cpuDebugger.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        cpuDebugger.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                cpuDebugger.setVisible(false);
            }
        });


        JPanel panel = new JPanel();

        JButton continueButton = new JButton("Continue");
        JButton pauseButton = new JButton("Pause");
        JButton stepButton = new JButton("Step in");
        JButton stepOverButton = new JButton("Step over");
        JButton stepOutButton = new JButton("Step out");
        JButton gotoPCButton = new JButton("Goto PC");
        JButton setPCButton = new JButton("Set PC");

        continueButton.setBounds(5, 3, 90, 30);
        pauseButton.setBounds(5*2+91, 3, 90, 30);
        stepButton.setBounds(5*3+91*2, 3, 90, 30);
        stepOverButton.setBounds(5*4+91*3, 3, 90, 30);
        stepOutButton.setBounds(5*5+91*4, 3, 90, 30);
        gotoPCButton.setBounds(5*6+91*5, 3, 90, 30);
        setPCButton.setBounds(5*7+91*6, 3, 90, 30);

        panel.add(continueButton);
        panel.add(pauseButton);
        panel.add(stepButton);
        panel.add(stepOverButton);
        panel.add(stepOutButton);
        panel.add(gotoPCButton);
        panel.add(setPCButton);


        JPanel mainRegisterPanel = new JPanel();
        mainRegisterPanel.setBounds(0, 0, 220, 720 + 160);
        mainRegisterPanel.setLayout(null);

        String[] regTypes = { "GPR", "COP0", "COP2 Lower", "COP2 Upper", "HI/LO" };
        JComboBox registerTypeBox = new JComboBox<String>(regTypes);
        registerTypeBox.setSelectedIndex(0);
        registerTypeBox.setBounds(5, 35, 200, 25); 

        registerTypeBox.addActionListener(action -> {
            String registerBoxType = (String)((JComboBox)action.getSource()).getSelectedItem();

            if (currentRegisterPanel != null) {
                panel.remove(currentRegisterPanel);
                currentRegisterPanel = null;
            }

            switch (registerBoxType) {
            case "GPR":
                currentRegisterPanel = mainRegisterPanel;
                break;
            case "COP0":
                break;
            }

            if (currentRegisterPanel != null) {
                panel.add(currentRegisterPanel);
            }

            panel.revalidate();
            panel.repaint();
        });


        for (int i = 0; i < 33; i++) {
            JButton registerName = new JButton(Disassembler.registerNames[i].toUpperCase() + " / " + Disassembler.aliasedRegisterNames[i].toUpperCase());
            registerName.setBounds(5, 65 + i * 23, 115, 23);

            
            JLabel textField;
            if (i == 32) {
                textField = new JLabel(String.format("   AAAAAAAA", i << 24));
            } else
                textField = new JLabel(String.format("   %08X", i << 24));
            textField.setBounds(125, 65 + i * 23, 80, 23);
            Font font = textField.getFont();
            Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
            textField.setFont(boldFont);
            textField.setOpaque(true);
            textField.setBackground(Color.white);
            textField.setForeground(Color.red);

            mainRegisterPanel.add(registerName);
            mainRegisterPanel.add(textField);
        }
 
        currentRegisterPanel = mainRegisterPanel;

        panel.setLayout(null);
        panel.add(registerTypeBox);
        panel.add(mainRegisterPanel);
        panel.add(new DebuggerDisassemblyView(220, 35, 480, 600));
        panel.revalidate();
        panel.repaint();

        cpuDebugger.add(panel);
        cpuDebugger.remove(mainRegisterPanel);

        cpuDebugger.setResizable(false);
        cpuDebugger.setPreferredSize(new Dimension(720, 720+160));
        cpuDebugger.setMinimumSize(new Dimension(720, 720+160));
        cpuDebugger.pack();
        cpuDebugger.setVisible(true);
    }
}