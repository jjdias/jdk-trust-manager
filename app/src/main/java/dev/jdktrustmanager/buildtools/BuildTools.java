package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.buildtools.JavaRange.Step;
import dev.jdktrustmanager.model.Tool;
import dev.jdktrustmanager.model.ToolVersions;
import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The three tool descriptions. Java ranges come from each project's documentation: the Maven release history
 * (maven.apache.org/docs/history.html), the Ant manual's system requirements, and Gradle's compatibility matrix
 * (docs.gradle.org/current/userguide/compatibility.html, checked 2026-11-27 against Gradle 9.9.3).
 */
public final class BuildTools {

    public static final BuildTool MAVEN = new Maven();
    public static final BuildTool ANT = new Ant();
    public static final BuildTool GRADLE = new Gradle();
    public static final List<BuildTool> ALL = List.of(MAVEN, ANT, GRADLE);

    private BuildTools() {
    }

    public static BuildTool of(Tool tool) {
        return switch (tool) {
            case MAVEN -> MAVEN;
            case ANT -> ANT;
            case GRADLE -> GRADLE;
        };
    }

    private static List<ToolRelease> releases(BuildTool tool, List<String> versions) {
        List<ToolRelease> releases = new ArrayList<>();
        for (String version : versions) {
            releases.add(new ToolRelease(tool.tool(), version, tool.zipUrl(version), null, ToolVersions.isPreRelease(version)));
        }
        return releases;
    }

    private static final class Maven implements BuildTool {
        private static final String CENTRAL = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/";
        private static final List<Step> MIN = List.of(
                new Step("2.0", 4), new Step("2.1", 5), new Step("3.2", 6), new Step("3.3", 7), new Step("3.9", 8),
                new Step("4.0", 17));

        public Tool tool() { return Tool.MAVEN; }
        public String displayName() { return "Maven"; }
        public String folderPrefix() { return "maven"; }
        public String versionListUrl() { return CENTRAL + "maven-metadata.xml"; }
        public List<ToolRelease> parse(String body) throws IOException { return releases(this, MavenMetadataParser.versions(body)); }
        public String zipUrl(String version) { return CENTRAL + version + "/apache-maven-" + version + "-bin.zip"; }

        public List<Checksum> checksumUrls(String version) {
            String zip = zipUrl(version);
            List<Checksum> urls = new ArrayList<>();
            if (version.startsWith("3.6.")) {
                urls.add(new Checksum(Verification.SHA512, "https://archive.apache.org/dist/maven/maven-3/" + version
                        + "/binaries/apache-maven-" + version + "-bin.zip.sha512"));
            }
            urls.add(new Checksum(Verification.SHA512, zip + ".sha512"));
            urls.add(new Checksum(Verification.SHA1, zip + ".sha1"));
            urls.add(new Checksum(Verification.MD5, zip + ".md5"));
            return urls;
        }

        public Optional<String> signatureUrl(String version) { return Optional.of(zipUrl(version) + ".asc"); }
        public Optional<String> keysUrl() { return Optional.of("https://downloads.apache.org/maven/KEYS"); }
        public List<String> launchers() { return List.of("bin\\mvn.cmd", "bin\\mvn.bat"); }
        public String versionFlag() { return "-v"; }
        public List<String> homeVariables() { return List.of("MAVEN_HOME", "M2_HOME"); }
        public String pathEntry() { return "%MAVEN_HOME%\\bin"; }
        public JavaRange javaRange(String version) { return new JavaRange(JavaRange.lookup(MIN, version, 4), null); }
    }

    private static final class Ant implements BuildTool {
        private static final String ARCHIVE = "https://archive.apache.org/dist/ant/binaries/";
        private static final List<Step> MIN = List.of(new Step("1.8", 4), new Step("1.9", 5), new Step("1.10", 8));

        public Tool tool() { return Tool.ANT; }
        public String displayName() { return "Ant"; }
        public String folderPrefix() { return "ant"; }
        public String versionListUrl() { return ARCHIVE; }
        public List<ToolRelease> parse(String body) { return releases(this, AntListingParser.versions(body)); }
        public String zipUrl(String version) { return ARCHIVE + "apache-ant-" + version + "-bin.zip"; }

        public List<Checksum> checksumUrls(String version) {
            String zip = zipUrl(version);
            return List.of(new Checksum(Verification.SHA512, zip + ".sha512"), new Checksum(Verification.SHA1, zip + ".sha1"),
                    new Checksum(Verification.MD5, zip + ".md5"));
        }

        public Optional<String> signatureUrl(String version) { return Optional.of(zipUrl(version) + ".asc"); }
        public Optional<String> keysUrl() { return Optional.of("https://downloads.apache.org/ant/KEYS"); }
        public List<String> launchers() { return List.of("bin\\ant.bat"); }
        public String versionFlag() { return "-version"; }
        public List<String> homeVariables() { return List.of("ANT_HOME"); }
        public String pathEntry() { return "%ANT_HOME%\\bin"; }
        /** Ant before 1.8 ran on any Java still in use; 2 stands for "any". */
        public JavaRange javaRange(String version) { return new JavaRange(JavaRange.lookup(MIN, version, 2), null); }
    }

    private static final class Gradle implements BuildTool {
        private static final List<Step> MIN = List.of(new Step("2.0", 6), new Step("3.0", 7), new Step("5.0", 8), new Step("9.0", 17));
        /** First Gradle version able to run on each Java ("Support for running Gradle" column). */
        private static final List<Step> MAX = List.of(
                new Step("2.0", 8), new Step("4.3", 9), new Step("4.7", 10), new Step("5.0", 11), new Step("5.4", 12),
                new Step("6.0", 13), new Step("6.3", 14), new Step("6.7", 15), new Step("7.0", 16), new Step("7.3", 17),
                new Step("7.5", 18), new Step("7.6", 19), new Step("8.3", 20), new Step("8.5", 21), new Step("8.8", 22),
                new Step("8.10", 23), new Step("8.14", 24), new Step("9.1", 25), new Step("9.4", 26), new Step("9.8", null));

        public Tool tool() { return Tool.GRADLE; }
        public String displayName() { return "Gradle"; }
        public String folderPrefix() { return "gradle"; }
        public String versionListUrl() { return "https://services.gradle.org/versions/all"; }
        public List<ToolRelease> parse(String body) throws IOException { return GradleVersionsParser.releases(body); }
        public String zipUrl(String version) { return "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip"; }
        public List<Checksum> checksumUrls(String version) { return List.of(); }
        public Optional<String> signatureUrl(String version) { return Optional.empty(); }
        public Optional<String> keysUrl() { return Optional.empty(); }
        public List<String> launchers() { return List.of("bin\\gradle.bat"); }
        public String versionFlag() { return "--version"; }
        public List<String> homeVariables() { return List.of("GRADLE_HOME"); }
        public String pathEntry() { return "%GRADLE_HOME%\\bin"; }

        /** Gradle before 2.0 never supported running on Java 8, so its maximum is 7. */
        public JavaRange javaRange(String version) {
            return new JavaRange(JavaRange.lookup(MIN, version, 5), JavaRange.lookup(MAX, version, 7));
        }
    }
}
