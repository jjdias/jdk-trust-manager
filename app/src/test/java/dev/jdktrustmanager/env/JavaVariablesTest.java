package dev.jdktrustmanager.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaVariablesTest {

    @Test
    void listsSetJavaVariablesAndOnlyJavaPathEntries() {
        String dump = """
                HKEY_CURRENT_USER\\Environment
                    JAVA_HOME    REG_SZ    C:\\Java\\temurin-21
                    Path    REG_EXPAND_SZ    %JAVA_HOME%\\bin;C:\\Users\\me\\cargo\\bin;C:\\Program Files\\Common Files\\Oracle\\Java\\javapath
                    TEMP    REG_EXPAND_SZ    %USERPROFILE%\\AppData\\Local\\Temp
                """;
        Map<String, String> user = RegistryEnvironment.entries(dump);
        assertEquals("C:\\Java\\temurin-21", JavaVariables.find(user, "java_home"));
        var rows = JavaVariables.list(user, Map.of("JDK_JAVA_OPTIONS", "-Dfile.encoding=UTF-8"), "C:\\Java");
        assertEquals("JAVA_HOME", rows.get(0).name());
        assertEquals("User", rows.get(0).scope());
        assertEquals("C:\\Java\\temurin-21", rows.get(0).value());
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("JDK_JAVA_OPTIONS") && row.scope().equals("System")));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("Path") && row.value().equals("%JAVA_HOME%\\bin")));
        assertTrue(rows.stream().anyMatch(row -> row.value().contains("javapath")));
        assertTrue(rows.stream().noneMatch(row -> row.value().contains("cargo") || row.value().contains("TEMP")));
    }
}
