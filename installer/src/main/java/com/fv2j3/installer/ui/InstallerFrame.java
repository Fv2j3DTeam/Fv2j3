package com.fv2j3.installer.ui;

import com.fv2j3.installer.core.InstallationPlan;
import com.fv2j3.installer.core.InstallationResult;
import com.fv2j3.installer.core.InstallerConstants;
import com.fv2j3.installer.core.InstallerEngine;
import com.fv2j3.installer.core.MinecraftDetector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class InstallerFrame extends JFrame {
    private final JButton browseButton;
    private final JTextArea minecraftPathField;
    private final JCheckBox installClientCheck;
    private final JCheckBox installServerCheck;
    private final JCheckBox createModsDirCheck;
    private final JButton installButton;
    private final JButton cancelButton;
    private final JProgressBar progressBar;
    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private Path selectedMinecraftPath;
    private volatile boolean installationCancelled;
    private boolean serverMode = false;

    public InstallerFrame() {
        super("Fv2j3 Installer");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLocationByPlatform(true);
        browseButton = new JButton();
        minecraftPathField = new JTextArea();
        installClientCheck = new JCheckBox();
        installServerCheck = new JCheckBox();
        createModsDirCheck = new JCheckBox();
        installButton = new JButton();
        cancelButton = new JButton();
        progressBar = new JProgressBar();
        logArea = new JTextArea();
        statusLabel = new JLabel();
        versionLabel = new JLabel();
        initComponents();
        pack();
        setMinimumSize(getSize());
        detectInitialMinecraft();
    }

    private void initComponents() {
        Container content = getContentPane();
        content.setLayout(new BorderLayout(10, 10));
        ((javax.swing.JComponent) content).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel header = createHeader();
        content.add(header, BorderLayout.NORTH);

        JPanel center = createCenter();
        content.add(center, BorderLayout.CENTER);

        JPanel footer = createFooter();
        content.add(footer, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        JLabel title = new JLabel("Fv2j3 Installer", SwingConstants.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        JLabel subtitle = new JLabel("Minecraft 1.12.2 Mod Loader", SwingConstants.CENTER);
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        subtitle.setForeground(Color.DARK_GRAY);
        panel.add(subtitle, BorderLayout.CENTER);

        versionLabel.setText(
                "Version " + InstallerConstants.FV2J3_INSTALLER_VERSION + " | Minecraft " + InstallerConstants.MINECRAFT_VERSION
        );
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        versionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        versionLabel.setForeground(Color.GRAY);
        panel.add(versionLabel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createCenter() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                new EmptyBorder(10, 10, 10, 10)
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);

        JLabel sectionLabel = new JLabel("Installation");
        sectionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.add(sectionLabel, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 0, 2, 5);
        JLabel typeLabel = new JLabel("Installation type:");
        typeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(typeLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(4, 0, 2, 0);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        installClientCheck.setText("Client");
        installClientCheck.setSelected(true);
        installClientCheck.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        installClientCheck.addActionListener(e -> {
            installClientCheck.setSelected(true);
            installServerCheck.setSelected(false);
            serverMode = false;
        });
        typePanel.add(installClientCheck);
        installServerCheck.setText("Server");
        installServerCheck.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        installServerCheck.addActionListener(e -> {
            installClientCheck.setSelected(false);
            installServerCheck.setSelected(true);
            serverMode = true;
        });
        typePanel.add(installServerCheck);
        panel.add(typePanel, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 2, 5);
        JLabel pathLabel = new JLabel("Minecraft directory:");
        pathLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(pathLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(4, 0, 2, 0);
        minecraftPathField.setEditable(false);
        minecraftPathField.setLineWrap(true);
        minecraftPathField.setWrapStyleWord(true);
        minecraftPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane pathScroll = new JScrollPane(minecraftPathField);
        pathScroll.setPreferredSize(new Dimension(350, 50));
        panel.add(pathScroll, gbc);

        gbc.gridy++;
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.insets = new Insets(2, 0, 6, 0);
        browseButton.setText("Browse...");
        browseButton.addActionListener(e -> browseMinecraft());
        panel.add(browseButton, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(4, 0, 0, 0);
        JCheckBox installMainCheck = new JCheckBox("Install Fv2j3 (create version JSON and launcher profile)", true);
        installMainCheck.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        installMainCheck.setEnabled(false);
        panel.add(installMainCheck, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(2, 0, 0, 0);
        createModsDirCheck.setText("Create mods directory if it does not exist");
        createModsDirCheck.setSelected(true);
        createModsDirCheck.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        panel.add(createModsDirCheck, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 0, 4, 0);
        JSeparator separator = new JSeparator();
        panel.add(separator, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 0, 0);
        statusLabel.setText("Ready");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        statusLabel.setForeground(Color.DARK_GRAY);
        panel.add(statusLabel, gbc);

        return panel;
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        progressBar.setStringPainted(true);
        progressBar.setString("Ready to install");
        panel.add(progressBar, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(450, 120));
        panel.add(logScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancelButton.setText("Cancel");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelInstallation());
        buttonPanel.add(cancelButton);

        installButton.setText("Install");
        installButton.setPreferredSize(new Dimension(100, 28));
        installButton.addActionListener(e -> startInstallation());
        buttonPanel.add(installButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void detectInitialMinecraft() {
        Path detected = MinecraftDetector.detectMinecraftDirectory();
        if (detected != null && Files.isDirectory(detected)) {
            selectedMinecraftPath = detected;
            minecraftPathField.setText(detected.toString());
            updateMinecraftStatus();
        } else {
            minecraftPathField.setText("(Not detected — click Browse to select)");
        }
    }

    private void browseMinecraft() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Minecraft Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        Path current = selectedMinecraftPath;
        if (current != null) {
            chooser.setCurrentDirectory(current.toFile());
        } else {
            Path home = Paths.get(System.getProperty("user.home"));
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    chooser.setCurrentDirectory(Paths.get(appData).toFile());
                }
            } else {
                chooser.setCurrentDirectory(home.toFile());
            }
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedMinecraftPath = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            minecraftPathField.setText(selectedMinecraftPath.toString());
            updateMinecraftStatus();
        }
    }

    private void updateMinecraftStatus() {
        if (selectedMinecraftPath == null) {
            statusLabel.setText("No directory selected");
            statusLabel.setForeground(Color.RED);
            installButton.setEnabled(false);
            return;
        }
        MinecraftDetector.MinecraftDirectory dir = MinecraftDetector.analyze(selectedMinecraftPath);
        if (dir.isValid()) {
            statusLabel.setText("Minecraft 1.12.2 detected — ready to install");
            statusLabel.setForeground(new Color(0, 128, 0));
            installButton.setEnabled(true);
        } else if (dir.path() != null && dir.hasVersionsDirectory()) {
            statusLabel.setText("Minecraft found but 1.12.2 is not installed");
            statusLabel.setForeground(Color.ORANGE);
            installButton.setEnabled(false);
        } else {
            statusLabel.setText("Not a valid Minecraft directory");
            statusLabel.setForeground(Color.RED);
            installButton.setEnabled(false);
        }
    }

    private void startInstallation() {
        if (selectedMinecraftPath == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a Minecraft directory.",
                    "No Directory Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        MinecraftDetector.MinecraftDirectory dir = MinecraftDetector.analyze(selectedMinecraftPath);
        if (!dir.hasVersion112()) {
            JOptionPane.showMessageDialog(this,
                    "Minecraft 1.12.2 was not found in the selected directory.\n\n" +
                            "Please launch Minecraft 1.12.2 at least once using the official Minecraft Launcher\n" +
                            "before installing Fv2j3.",
                    "Minecraft 1.12.2 Not Found",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!dir.hasVersionJson() || !dir.hasVersionJar()) {
            JOptionPane.showMessageDialog(this,
                    "Minecraft 1.12.2 installation appears incomplete (missing version files).\n\n" +
                            "Please reinstall Minecraft 1.12.2 using the official Minecraft Launcher.",
                    "Incomplete Installation",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        String installType = serverMode ? "Fv2j3 Server" : "Fv2j3 Client";
        int confirm = JOptionPane.showConfirmDialog(this,
                installType + " will be installed to:\n" + selectedMinecraftPath + "\n\n" +
                        "This will create a new version profile in the Minecraft Launcher.\n" +
                        "Your existing worlds, mods, and settings will not be affected.",
                "Confirm Installation",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        performInstallation();
    }

    private void performInstallation() {
        installButton.setEnabled(false);
        browseButton.setEnabled(false);
        cancelButton.setEnabled(true);
        installationCancelled = false;
        logArea.setText("");

        Thread installThread = new Thread(() -> {
            try {
                runInstallation();
            } finally {
                SwingUtilities.invokeLater(this::onInstallationComplete);
            }
        }, "fv2j3-installation");
        installThread.start();
    }

    private void runInstallation() {
        try {
            updateProgress(0, "Starting installation...");
            appendLog("Starting Fv2j3 installation...");
            appendLog("Minecraft directory: " + selectedMinecraftPath);
            appendLog("Mode: " + (serverMode ? "Server" : "Client"));
            appendLog("");

            InstallationPlan plan = InstallationPlan.builder(selectedMinecraftPath)
                    .installClient(true)
                    .createModsDir(createModsDirCheck.isSelected())
                    .build();

            InstallerEngine engine = new InstallerEngine();
            InstallationResult result = engine.execute(plan);

            if (result.success()) {
                appendLog("");
                appendLog("=== Installation completed ===");
                for (String step : result.steps()) {
                    appendLog("[OK] " + step);
                }
                if (!result.warnings().isEmpty()) {
                    appendLog("");
                    for (String w : result.warnings()) {
                        appendLog("[WARN] " + w);
                    }
                }
                updateProgress(100, "Installation complete");
                final String modsDir = result.modsDir() != null ? result.modsDir().toString() : "(unknown)";
                SwingUtilities.invokeLater(() -> {
                    String installType = serverMode ? "Server" : "Client";
                    JOptionPane.showMessageDialog(this,
                            "Fv2j3 " + installType + " has been successfully installed!\n\n" +
                                    "Open the Minecraft Launcher and select \"Fv2j3 1.12.2\" from the Installations tab.\n\n" +
                                    "Place your mods in:\n" + modsDir,
                            "Installation Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } else {
                appendLog("");
                appendLog("=== Installation FAILED ===");
                appendLog(result.failureReason() != null ? result.failureReason() : "Unknown error");
                updateProgress(0, "Installation failed");
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Installation failed:\n\n" + result.failureReason(),
                            "Installation Failed",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        } catch (Exception ex) {
            appendLog("");
            appendLog("=== Installation FAILED ===");
            appendLog("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
            updateProgress(0, "Installation failed");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Installation failed:\n\n" + ex.getMessage(),
                        "Installation Failed",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void onInstallationComplete() {
        installButton.setEnabled(true);
        browseButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    private void cancelInstallation() {
        installationCancelled = true;
        appendLog("Installation cancelled by user.");
        updateProgress(0, "Cancelled");
        onInstallationComplete();
    }

    private void updateProgress(int percent, String status) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressBar.setString(status);
        });
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
