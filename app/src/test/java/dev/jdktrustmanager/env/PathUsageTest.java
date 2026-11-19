package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathUsageTest {

    @Test
    void blocksTheHomeNamedByJavaHomeOrAPathEntry() {
        String home = "C:\\Java\\temurin-21";
        String other = "C:\\Java\\temurin-11";
        String path = "%JAVA_HOME%\\bin;C:\\Windows";
        assertTrue(PathUsage.uses(home, home, path, "", "%SystemRoot%\\system32"));
        assertFalse(PathUsage.uses(other, home, path, "", "%SystemRoot%\\system32"));
        assertTrue(PathUsage.uses(other, home, "C:\\Java\\temurin-11\\bin;" + path));
        assertTrue(PathUsage.uses(other, home, path, "", "C:\\Java\\temurin-11\\bin"));
        assertFalse(PathUsage.uses("C:\\Java\\temurin-2", "C:\\Java\\temurin-21", "C:\\Java\\temurin-21\\bin"));
        String raw = "\r\nHKEY_CURRENT_USER\\Environment\r\n    Path    REG_EXPAND_SZ    %JAVA_HOME%\\bin;C:\\Windows\r\n\r\n";
        assertEquals("%JAVA_HOME%\\bin;C:\\Windows", RegistryEnvironment.value(raw));
    }
}
