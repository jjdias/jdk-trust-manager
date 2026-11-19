package dev.jdktrustmanager.install;

import java.io.IOException;
import java.nio.file.Path;

public final class Layout {

    private Layout() {
    }

    public static Path destination(Path root, String slug, int major) {
        if (slug == null || slug.isBlank() || slug.contains("..") || slug.indexOf('/') >= 0 || slug.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid slug: " + slug);
        }
        return root.resolve(slug + "-" + major).normalize();
    }

    public static void checkInsideRoot(Path root, Path candidate, Path bundledRuntime) throws IOException {
        Path rootNormal = root.toAbsolutePath().normalize();
        Path candidateNormal = candidate.toAbsolutePath().normalize();
        if (!candidateNormal.startsWith(rootNormal) || candidateNormal.equals(rootNormal)) {
            throw new IOException("Refusing path outside the JDK root: " + candidate);
        }
        if (candidateNormal.getFileName() != null && "trusted-certs".equals(candidateNormal.getFileName().toString())) {
            throw new IOException("Refusing the shared certificate folder");
        }
        if (bundledRuntime != null && candidateNormal.startsWith(bundledRuntime.toAbsolutePath().normalize())) {
            throw new IOException("Refusing the bundled runtime");
        }
    }
}
