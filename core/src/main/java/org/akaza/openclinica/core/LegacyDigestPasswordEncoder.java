package org.akaza.openclinica.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unsalted hex digest, byte-for-byte what Spring Security 3/4's
 * {@code ShaPasswordEncoder} / {@code Md5PasswordEncoder} produced with a
 * {@code null} salt (removed in Spring Security 5). OpenClinica stores these
 * hashes in {@code user_account.passwd} and compares them directly in places
 * (e.g. SOAP username tokens), so the format must not change.
 *
 * <p>Spring Security 5's {@code MessageDigestPasswordEncoder} is not a drop-in
 * replacement: it prefixes newly encoded values with a random {@code {salt}}.
 */
public class LegacyDigestPasswordEncoder implements PasswordEncoder {

    private final String algorithm;

    /** @param algorithm a {@link MessageDigest} algorithm, e.g. {@code "SHA-1"} or {@code "MD5"} */
    public LegacyDigestPasswordEncoder(String algorithm) {
        this.algorithm = algorithm;
        digest(""); // fail fast on an unknown algorithm
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return digest(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        byte[] expected = encodedPassword.getBytes(StandardCharsets.UTF_8);
        byte[] actual = encode(rawPassword).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String digest(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return new String(Hex.encode(md.digest(raw.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such algorithm [" + algorithm + "]", e);
        }
    }
}
