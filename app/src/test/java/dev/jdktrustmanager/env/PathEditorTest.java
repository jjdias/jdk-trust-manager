package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PathEditorTest {

    @Test
    void prependsJavaHomeDropsManagedRootAndKeepsOracle() {
        String current = "C:\\Java\\temurin-17\\bin;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath;C:\\Windows";
        String edited = PathEditor.edit(current, "C:\\Java", false);
        assertEquals("%JAVA_HOME%\\bin;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath;C:\\Windows", edited);
        assertEquals(edited, PathEditor.edit(edited, "C:\\Java", false));
        String removed = PathEditor.edit(current, "C:\\Java", true);
        assertTrue(removed.startsWith("%JAVA_HOME%\\bin;C:\\Windows"));
        String snippet = ShellSnippets.powershell(Path.of("C:\\Java\\temurin-21"), 21);
        assertTrue(snippet.contains("C:\\Java\\temurin-21"));
        assertTrue(snippet.contains("21"));
    }
}
