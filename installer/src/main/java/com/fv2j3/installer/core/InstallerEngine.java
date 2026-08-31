package com.fv2j3.installer.core;

import com.fv2j3.installer.util.SelfContainedExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class InstallerEngine {
    private static final Logger LOG = LoggerFactory.getLogger(InstallerEngine.class);

    public InstallationPlan buildPlan(Path minecraftHome) {
        return InstallationPlan.builder(minecraftHome)
                .extractRuntime(true)
                .installClient(true)
                .integrateLauncher(true)
                .createModsDir(true)
                .build();
    }

    public InstallationResult execute(InstallationPlan plan) {
        InstallationResult.Builder result = InstallationResult.builder()
                .minecraftHome(plan.minecraftHome())
                .runtimeDir(plan.runtimeDir());
        try {
            MinecraftDetector.MinecraftDirectory mcDir = MinecraftDetector.analyze(plan.minecraftHome());
            if (!mcDir.isValid()) {
                return result.success(false)
                        .failureReason("Minecraft 1.12.2 not found at " + plan.minecraftHome()
                                + ". Please launch Minecraft 1.12.2 once using the official Minecraft Launcher.")
                        .build();
            }
            result.addStep("Minecraft 1.12.2 detected: " + mcDir.path());

            if (plan.extractRuntime()) {
                SelfContainedExtractor.extractRuntime(plan.runtimeDir());
                result.addStep("Runtime extracted to " + plan.runtimeDir());
            }
            List<Path> libs = BootstrapInstaller.installBootstrap(plan.runtimeDir());
            result.addStep("Bootstrap libraries installed: " + libs.size());

            if (plan.createModsDir()) {
                Files.createDirectories(plan.modsDir());
                result.addStep("Mods directory ensured: " + plan.modsDir());
            }
            result.modsDir(plan.modsDir());

            if (plan.installClient()) {
                VersionJsonGenerator.VersionJson vjson = VersionJsonGenerator.generate(
                        plan.minecraftHome(), plan.runtimeDir());
                result.fv2j3VersionDir(vjson.directory());
                result.fv2j3VersionJson(vjson.versionJson());
                result.addStep("Fv2j3 version JSON written: " + vjson.versionJson());
            }

            if (plan.integrateLauncher()) {
                LauncherProfileIntegrator.ProfileResult profile = LauncherProfileIntegrator.integrate(
                        plan.minecraftHome());
                if (profile.success()) {
                    result.launcherIntegrated(true);
                    result.addStep("Launcher profile integrated: " + profile.profileName());
                } else {
                    result.addWarning("Launcher integration failed: " + profile.error());
                }
            }

            return result.success(true).build();
        } catch (IOException ex) {
            LOG.error("Installation failed", ex);
            return result.success(false)
                    .failureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        } catch (RuntimeException ex) {
            LOG.error("Installation failed", ex);
            return result.success(false)
                    .failureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage())
                    .build();
        }
    }

    public InstallationResult executeHeadless(Path minecraftHome) {
        InstallationPlan plan = buildPlan(minecraftHome);
        return execute(plan);
    }
}
