package io.github.fv2j3dteam.api;

import java.util.List;

public interface ModRegistryView {
    List<ModInfo> mods();

    default ModInfo find(String id) {
        if (id == null) {
            return null;
        }
        return mods().stream().filter(mod -> id.equals(mod.id())).findFirst().orElse(null);
    }
}
