package dev.jdktrustmanager.buildtools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;

/** Test-only OpenPGP keys and detached signatures, so signature tests never touch the network. */
final class PgpTestKeys {

    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    final PGPSecretKeyRing secret;
    final PGPPublicKeyRing pub;

    private PgpTestKeys(PGPSecretKeyRing secret, PGPPublicKeyRing pub) {
        this.secret = secret;
        this.pub = pub;
    }

    /** A key created at {@code created}; {@code expiresAfterSeconds} 0 means no expiry; {@code withSigningSubkey} adds one. */
    static PgpTestKeys generate(String name, Instant created, long expiresAfterSeconds, boolean withSigningSubkey) throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        PGPKeyPair master = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, rsa.generateKeyPair(), Date.from(created));
        PGPDigestCalculator sha1 = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PGPSignatureSubpacketGenerator hashed = new PGPSignatureSubpacketGenerator();
        hashed.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
        if (expiresAfterSeconds > 0) {
            hashed.setKeyExpirationTime(false, expiresAfterSeconds);
        }
        PGPKeyRingGenerator generator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, master,
                name + " <" + name.toLowerCase() + "@example.test>", sha1, hashed.generate(), null,
                new JcaPGPContentSignerBuilder(master.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider(BC),
                null);
        if (withSigningSubkey) {
            PGPKeyPair sub = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, rsa.generateKeyPair(), Date.from(created));
            PGPSignatureSubpacketGenerator subHashed = new PGPSignatureSubpacketGenerator();
            subHashed.setKeyFlags(false, KeyFlags.SIGN_DATA);
            generator.addSubKey(sub, subHashed.generate(), null);
        }
        return new PgpTestKeys(generator.generateSecretKeyRing(), generator.generatePublicKeyRing());
    }

    /** The key that signs: the subkey when there is one, otherwise the primary. */
    PGPSecretKey signingKey() {
        PGPSecretKey chosen = null;
        for (var iterator = secret.getSecretKeys(); iterator.hasNext(); ) {
            chosen = iterator.next();
        }
        return chosen;
    }

    String signingKeyId() {
        return String.format("%016X", signingKey().getKeyID());
    }

    byte[] sign(Path file, Instant when) throws Exception {
        PGPSecretKey key = signingKey();
        PGPPrivateKey privateKey = key.extractPrivateKey(null);
        PGPSignatureGenerator generator = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(key.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider(BC),
                key.getPublicKey());
        generator.init(PGPSignature.BINARY_DOCUMENT, privateKey);
        PGPSignatureSubpacketGenerator subpackets = new PGPSignatureSubpacketGenerator();
        subpackets.setSignatureCreationTime(false, Date.from(when));
        subpackets.setIssuerKeyID(false, key.getKeyID());
        generator.setHashedSubpackets(subpackets.generate());
        generator.update(Files.readAllBytes(file));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = new ArmoredOutputStream(out)) {
            generator.generate().encode(armor);
        }
        return out.toByteArray();
    }

    /** An Apache-style KEYS file: several armored blocks with text between them. */
    static byte[] keysFile(List<PgpTestKeys> keys) throws IOException {
        StringBuilder text = new StringBuilder("This file contains the PGP keys of various developers.\n\n");
        for (PgpTestKeys key : keys) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArmoredOutputStream armor = new ArmoredOutputStream(out)) {
                key.pub.encode(armor);
            }
            text.append("pub   rsa2048 2020-01-01 [SC]\n      ").append(String.format("%016X", key.pub.getPublicKey().getKeyID()))
                    .append("\n\n").append(out.toString(StandardCharsets.US_ASCII)).append("\n");
        }
        return text.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
