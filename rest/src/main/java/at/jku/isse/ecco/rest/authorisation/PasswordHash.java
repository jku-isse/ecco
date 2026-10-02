package at.jku.isse.ecco.rest.authorisation;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2 password hashes, written as {@code pbkdf2-sha256$<iterations>$<salt>$<hash>}
 * (salt and hash in Base64), so a users file never holds a password.
 */
public final class PasswordHash {

    private static final String PREFIX = "pbkdf2-sha256";
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private PasswordHash() {
    }

    public static String hash(char[] password) {
        return hash(password, ITERATIONS);
    }

    static String hash(char[] password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** @throws IllegalArgumentException if {@code encoded} is not a hash written by {@link #hash} */
    public static boolean matches(char[] password, String encoded) {
        String[] parts = encoded.split("\\$");
        if (parts.length != 4 || !parts[0].equals(PREFIX))
            throw new IllegalArgumentException("not a " + PREFIX + " password hash");
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
