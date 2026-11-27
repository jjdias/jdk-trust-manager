package dev.jdktrustmanager.buildtools;

import dev.jdktrustmanager.model.Verification;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Checksums {

    private Checksums() {
    }

    public static String hex(Path file, Verification kind) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm(kind));
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * The first hex run of the right length in a checksum file, lower-cased. Handles {@code hash},
     * {@code hash  name}, {@code hash *name}, and {@code MD5 (name) = hash}.
     */
    public static Optional<String> extract(String fileText, Verification kind) {
        int length = switch (kind) {
            case SHA512 -> 128;
            case SHA256 -> 64;
            case SHA1 -> 40;
            case MD5 -> 32;
            default -> -1;
        };
        if (length < 0 || fileText == null) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile("(?<![0-9A-Fa-f])[0-9A-Fa-f]{" + length + "}(?![0-9A-Fa-f])").matcher(fileText);
        return matcher.find() ? Optional.of(matcher.group().toLowerCase(Locale.ROOT)) : Optional.empty();
    }

    static String label(Verification kind) {
        return switch (kind) {
            case SHA512 -> "SHA-512";
            case SHA256 -> "SHA-256";
            case SHA1 -> "SHA-1";
            case MD5 -> "MD5";
            case SIGNATURE -> "signature";
            case NONE -> "none";
            case UNRECORDED -> "unrecorded";
        };
    }

    private static String algorithm(Verification kind) {
        return switch (kind) {
            case SHA512 -> "SHA-512";
            case SHA256 -> "SHA-256";
            case SHA1 -> "SHA-1";
            case MD5 -> "MD5";
            default -> throw new IllegalArgumentException("Not a checksum: " + kind);
        };
    }
}
