package dev.jdktrustmanager.buildtools;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

/**
 * Checks a detached OpenPGP signature ({@code .asc}) over a file against an Apache {@code KEYS} file.
 * Key expiry is deliberately not checked: old releases were signed while their keys were valid.
 */
public final class SignatureCheck {

    private static final BouncyCastleProvider BC = new BouncyCastleProvider();
    private static final String BEGIN = "-----BEGIN PGP PUBLIC KEY BLOCK-----";
    private static final String END = "-----END PGP PUBLIC KEY BLOCK-----";

    /** {@code keyId} is the signature's issuer as 16 upper-case hex digits. */
    public record Result(boolean valid, String keyId, boolean signerKnown) {
    }

    private SignatureCheck() {
    }

    public static Result verify(Path file, byte[] armoredSignature, byte[] armoredKeys) throws IOException {
        PGPSignature signature = signature(armoredSignature);
        String keyId = String.format("%016X", signature.getKeyID());
        PGPPublicKey key = find(rings(armoredKeys), signature.getKeyID());
        if (key == null) {
            return new Result(false, keyId, false);
        }
        try {
            signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BC), key);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    signature.update(buffer, 0, read);
                }
            }
            return new Result(signature.verify(), keyId, true);
        } catch (PGPException ex) {
            return new Result(false, keyId, true);
        }
    }

    private static PGPSignature signature(byte[] armored) throws IOException {
        try {
            Object object = new JcaPGPObjectFactory(PGPUtil.getDecoderStream(new ByteArrayInputStream(armored))).nextObject();
            if (object instanceof PGPCompressedData compressed) {
                object = new JcaPGPObjectFactory(compressed.getDataStream()).nextObject();
            }
            if (object instanceof PGPSignatureList list && !list.isEmpty()) {
                return list.get(0);
            }
        } catch (IOException | PGPException | RuntimeException ex) {
            throw new IOException("Not an OpenPGP signature", ex);
        }
        throw new IOException("Not an OpenPGP signature");
    }

    /** Every armored key block in the file; blocks Bouncy Castle can't read (very old key formats) are skipped. */
    private static List<PGPPublicKeyRing> rings(byte[] armoredKeys) {
        String text = new String(armoredKeys, StandardCharsets.US_ASCII);
        List<PGPPublicKeyRing> rings = new ArrayList<>();
        int start = text.indexOf(BEGIN);
        while (start >= 0) {
            int end = text.indexOf(END, start);
            if (end < 0) {
                break;
            }
            byte[] block = text.substring(start, end + END.length()).getBytes(StandardCharsets.US_ASCII);
            try {
                new JcaPGPPublicKeyRingCollection(PGPUtil.getDecoderStream(new ByteArrayInputStream(block)))
                        .getKeyRings().forEachRemaining(rings::add);
            } catch (IOException | PGPException | RuntimeException ignored) {
                // One unreadable developer key doesn't make the whole KEYS file useless.
            }
            start = text.indexOf(BEGIN, end);
        }
        return rings;
    }

    /** Primary keys and subkeys alike. */
    private static PGPPublicKey find(List<PGPPublicKeyRing> rings, long keyId) {
        for (PGPPublicKeyRing ring : rings) {
            PGPPublicKey key = ring.getPublicKey(keyId);
            if (key != null) {
                return key;
            }
        }
        return null;
    }
}
