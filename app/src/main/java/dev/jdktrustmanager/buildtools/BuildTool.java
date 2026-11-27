package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.Tool;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Everything that differs between Maven, Ant, and Gradle. The screens, installer, and verifier are shared. */
public interface BuildTool {

    Tool tool();

    String displayName();

    /** Folder name prefix under {@code <root>\build-tools}, e.g. {@code maven} for {@code maven-3.9.9}. */
    String folderPrefix();

    String versionListUrl();

    List<ToolRelease> parse(String body) throws IOException;

    String zipUrl(String version);

    /** Checksum files to try, strongest first. Empty for Gradle, whose SHA-256 comes with the release. */
    List<Checksum> checksumUrls(String version);

    Optional<String> signatureUrl(String version);

    Optional<String> keysUrl();

    /** Launcher paths relative to the home; the first one that exists is used. */
    List<String> launchers();

    /** The flag that prints the version, e.g. {@code -v}. */
    String versionFlag();

    /** Home variables to set; the first is the one the PATH entry references. */
    List<String> homeVariables();

    String pathEntry();

    JavaRange javaRange(String version);
}
