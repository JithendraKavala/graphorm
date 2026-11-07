package com.graphorm.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Version provider for the GraphORM CLI.
 */
public final class GraphOrmVersionProvider implements IVersionProvider {

    private static final String DEFAULT_VERSION = "0.1.0-SNAPSHOT";

    @Override
    public String[] getVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            version = DEFAULT_VERSION;
        }
        return new String[]{
                "GraphORM CLI version " + version,
                "JVM: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")
        };
    }
}
