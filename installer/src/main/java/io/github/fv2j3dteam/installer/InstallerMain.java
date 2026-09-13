package io.github.fv2j3dteam.installer;

import io.github.fv2j3dteam.installer.core.InstallerEngine;
import io.github.fv2j3dteam.installer.ui.InstallerFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class InstallerMain {
    private static final Logger LOG = LoggerFactory.getLogger(InstallerMain.class);

    // ---- Constants (merged from InstallerConstants) ----
    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String FV2J3_VERSION_ID = "Fv2j3-" + MINECRAFT_VERSION;
    public static final String FV2J3_PROFILE_NAME = "Fv2j3 1.12.2";
    public static final String FV2J3_MAIN_CLASS = "io.github.fv2j3dteam.loader.runtime.Bootstrap";
    public static final String FV2J3_INSTALLER_VERSION = readVersion();

    public static Path getRuntimeDir() {
        String property = System.getProperty("fv2j3.installer.runtime.dir");
        if (property != null && !property.isBlank()) {
            return Paths.get(property).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".fv2j3", "installer-runtime").toAbsolutePath();
    }

    private static String readVersion() {
        String version = InstallerMain.class.getPackage().getImplementationVersion();
        if (version != null && !version.isBlank()) return version;
        return System.getProperty("fv2j3.installer.version", "0.1.0-SNAPSHOT");
    }

    private InstallerMain() {}

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
        new InstallerFrame().setVisible(true);
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
            io.github.fv2j3dteam.installer.util.SelfContainedExtractor.extractRuntime(getRuntimeDir());
            if (extractOnly) {
                System.out.println("Extracted runtime to: " + getRuntimeDir());
                return;
            }
            if (minecraftDir == null) {
                minecraftDir = InstallerEngine.detectMinecraftDirectory();
            }
            if (minecraftDir == null) {
                System.err.println("Minecraft directory not found. Use --minecraft-dir=<path>.");
                System.exit(2);
            }
            InstallerEngine engine = new InstallerEngine();
            var plan = engine.buildPlan(minecraftDir);
            var result = engine.execute(plan);
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
            io.github.fv2j3dteam.installer.InstallerSelfTest test = new io.github.fv2j3dteam.installer.InstallerSelfTest();
            var report = test.run();
            System.out.print(report.render());
            System.exit(report.allPassed() ? 0 : 1);
        } catch (Exception ex) {
            System.err.println("Self-test failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }
}