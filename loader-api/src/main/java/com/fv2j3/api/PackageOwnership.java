package com.fv2j3.api;

public record PackageOwnership(String packageName, String owner, boolean modCanOverride) {
    public PackageOwnership {
        packageName = packageName == null || packageName.isBlank() ? "unknown" : packageName;
        owner = owner == null || owner.isBlank() ? "unknown" : owner;
    }

    public static PackageOwnership of(String packageName, String owner, boolean modCanOverride) {
        return new PackageOwnership(packageName, owner, modCanOverride);
    }
}
