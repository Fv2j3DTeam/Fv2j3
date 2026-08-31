package com.fv2j3.installer.core;

import java.util.List;
import java.util.Map;

public interface InstallProgress {
    void onStep(String name, int percent, String message);

    static InstallProgress noop() {
        return new InstallProgress() {
            @Override
            public void onStep(String name, int percent, String message) {
            }
        };
    }

    static InstallProgress console() {
        return new InstallProgress() {
            @Override
            public void onStep(String name, int percent, String message) {
                System.out.println(String.format("[%3d%%] %-30s %s", percent, name, message));
            }
        };
    }
}
