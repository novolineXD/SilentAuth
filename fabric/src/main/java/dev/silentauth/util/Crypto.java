package dev.silentauth.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class Crypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    private Crypto(SecretKey key) {
        this.key = key;
    }

    public static Crypto forDirectory(File directory) {
        File keyFile = new File(directory, "key.bin");
        try {
            byte[] material;
            if (keyFile.isFile() && keyFile.length() >= 16) {
                material = read(keyFile);
            } else {
                material = new byte[keyLength()];
                new SecureRandom().nextBytes(material);
                write(keyFile, material);
            }
            return new Crypto(new SecretKeySpec(material, "AES"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialise the local key file", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_LENGTH) {
                return "";
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] payload = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(payload), "UTF-8");
        } catch (Exception e) {
            Log.warn("Stored value could not be decrypted, treating it as empty");
            return "";
        }
    }

    private static int keyLength() {
        try {
            return Cipher.getMaxAllowedKeyLength("AES") >= 256 ? 32 : 16;
        } catch (Exception e) {
            return 16;
        }
    }

    private static byte[] read(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return Arrays.copyOf(buffer, offset);
        } finally {
            in.close();
        }
    }

    private static void write(File file, byte[] data) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        JsonStore.ownerOnly(file);
    }
}
