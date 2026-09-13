package io.github.fv2j3dteam.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Fv2j3LoaderTest {
    @Test
    void defaultLoaderStartsInCreatedState() {
        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));

        assertEquals(LoaderState.CREATED, loader.state());
    }

    @Test
    void normalLifecycleTransitionsAreAllowed() throws LoaderInitializationException {
        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));

        loader.initialize();
        assertEquals(LoaderState.INITIALIZED, loader.state());

        loader.start();
        assertEquals(LoaderState.RUNNING, loader.state());

        loader.stop();
        assertEquals(LoaderState.STOPPED, loader.state());
    }

    @Test
    void illegalStateTransitionsAreRejected() {
        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));

        assertThrows(IllegalStateException.class, loader::start);
    }

    @Test
    void environmentContainsExpectedMetadata() {
        LoaderEnvironment environment = LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT");

        assertEquals("1.12.2", environment.minecraftVersion());
        assertEquals("0.1.0-SNAPSHOT", environment.fv2j3Version());
        assertNotNull(environment.javaVersion());
        assertNotNull(environment.operatingSystem());
        assertNotNull(environment.architecture());
    }

    @Test
    void javaVersionDetectionReportsCurrentTarget() {
        LoaderEnvironment environment = LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT");

        assertTrue(environment.isJava26());
    }

    @Test
    void initializationFailureTransitionsToFailedState() {
        LoaderEnvironment invalidEnvironment = new LoaderEnvironment(
                "17.0.5",
                "Linux",
                "amd64",
                "1.12.2",
                "0.1.0-SNAPSHOT",
                LoaderSide.BOTH
        );
        Fv2j3Loader loader = new Fv2j3Loader(invalidEnvironment);

        LoaderInitializationException exception = assertThrows(LoaderInitializationException.class, loader::initialize);

        assertEquals(LoaderState.FAILED, loader.state());
        assertTrue(exception.getMessage().contains("Java 26"));
    }

    @Test
    void loggerRecordsMessages() {
        InMemoryLoaderLogger logger = new InMemoryLoaderLogger();
        logger.info("startup");
        logger.warn("warning");

        assertTrue(logger.messages().stream().anyMatch(message -> message.contains("startup")));
        assertTrue(logger.messages().stream().anyMatch(message -> message.contains("warning")));
    }

    private static final class InMemoryLoaderLogger implements LoaderLogger {
        private final java.util.List<String> messages = new java.util.ArrayList<>();

        @Override
        public void debug(String message) {
            messages.add("DEBUG: " + message);
        }

        @Override
        public void info(String message) {
            messages.add("INFO: " + message);
        }

        @Override
        public void warn(String message) {
            messages.add("WARN: " + message);
        }

        @Override
        public void error(String message) {
            messages.add("ERROR: " + message);
        }

        java.util.List<String> messages() {
            return messages;
        }
    }
}
