package com.fv2j3.installer;

import com.fv2j3.installer.core.InstallationPlan;
import com.fv2j3.installer.core.InstallationResult;
import com.fv2j3.installer.core.InstallerConstants;
import com.fv2j3.installer.core.InstallerEngine;
import com.fv2j3.installer.core.MinecraftDetector;
import com.fv2j3.installer.ui.InstallerFrame;
import com.fv2j3.installer.util.SelfContainedExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class InstallerMain {
    private static final Logger LOG = LoggerFactory.getLogger(InstallerMain.class);

    private InstallerMain() {
    }

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            String first = args[0];
            if ("--headless".equals(first) || "--cli".equals(first)) {
                runHeadless(args);
                return;
            }
            if ("--self-test".equals(first)) {
                runSelfTest();
                return;
            }
        }
        installLookAndFeel();
        SwingUtilities.invokeLater(InstallerMain::launchGui);
    }

    private static void installLookAndFeel() {
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            LOG.warn("Could not install system look and feel: {}", ex.getMessage());
        }
    }

    private static void launchGui() {
        InstallerFrame frame = new InstallerFrame();
        frame.setVisible(true);
    }

    private static void runHeadless(String[] args) {
        Path minecraftDir = null;
        boolean extractOnly = false;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--minecraft-dir".equals(a) && i + 1 < args.length) {
                minecraftDir = Paths.get(args[++i]);
            } else if (a.startsWith("--minecraft-dir=")) {
                minecraftDir = Paths.get(a.substring("--minecraft-dir=".length()));
            } else if ("--extract".equals(a)) {
                extractOnly = true;
            }
        }
        try {
            SelfContainedExtractor.extractRuntime(InstallerConstants.getRuntimeDir());
            if (extractOnly) {
                System.out.println("Extracted runtime to: " + InstallerConstants.getRuntimeDir());
                return;
            }
            if (minecraftDir == null) {
                minecraftDir = MinecraftDetector.detectMinecraftDirectory();
            }
            if (minecraftDir == null) {
                System.err.println("Minecraft directory not found. Use --minecraft-dir=<path>.");
                System.exit(2);
            }
            InstallerEngine engine = new InstallerEngine();
            InstallationPlan plan = engine.buildPlan(minecraftDir);
            InstallationResult result = engine.execute(plan);
            System.out.println(result.summary());
            System.exit(result.success() ? 0 : 1);
        } catch (Exception ex) {
            System.err.println("Installer failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSelfTest() {
        try {
            InstallerSelfTest test = new InstallerSelfTest();
            InstallerSelfTest.SelfTestReport report = test.run();
            System.out.print(report.render());
            System.exit(report.allPassed() ? 0 : 1);
        } catch (Exception ex) {
            System.err.println("Self-test failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
