package io.github.bholeykabhakt.extension.zangibackup;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Streaming password encryption for backup files: chunked AES-256-GCM, key from PBKDF2-HMAC-SHA256.
 *
 * <pre>
 * header  = "ZBK1" | ver(1) | kdf(1) | iterations(4) | salt(16) | noncePrefix(8) | chunkSize(4)
 * chunk   = len(4, high bit = final) | AES-GCM(plain[len]) incl. 16-byte tag
 * nonce   = noncePrefix | chunkIndex(4)      AAD = header | chunkIndex(4) | final(1)
 * </pre>
 * A stream must end with exactly one final chunk, so truncation, reordering and appended data are
 * all detected. Single-shot GCM would buffer a whole multi-GB media archive on decrypt; chunks don't.
 * Keys are cached per (salt, iterations): a media mirror shares one salt, so it costs one derivation.
 */
public final class ZCrypto {
    static final byte[] MAGIC = {'Z', 'B', 'K', '1'};
    static final int VERSION = 1;
    static final int KDF_PBKDF2_SHA256 = 1;
    static final int ITERATIONS = 150_000;
    static final int CHUNK = 1 << 20;
    static final int HEADER_LEN = 4 + 1 + 1 + 4 + 16 + 8 + 4;
    static final int TAG_LEN = 16;
    static final int FINAL = 0x80000000;

    private static final SecureRandom RNG = new SecureRandom();
    private static final Map<String, SecretKey> KEYS = new HashMap<>();

    private ZCrypto() {}

    public static final class WrongPasswordException extends IOException {
        WrongPasswordException() { super("Wrong password, or the file is damaged"); }
    }

    public static byte[] newSalt() {
        byte[] s = new byte[16];
        RNG.nextBytes(s);
        return s;
    }

    static synchronized SecretKey key(char[] password, byte[] salt, int iterations) throws IOException {
        String id = Arrays.toString(salt) + "/" + iterations + "/" + String.valueOf(password);
        SecretKey k = KEYS.get(id);
        if (k != null) return k;
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            k = new SecretKeySpec(raw, "AES");
            KEYS.put(id, k);
            return k;
        } catch (GeneralSecurityException e) {
            throw new IOException("Key derivation unavailable on this device", e);
        }
    }

    public static synchronized void forgetKeys() {
        KEYS.clear();
    }

    /** Encrypts with a fresh random salt. */
    public static OutputStream encrypt(OutputStream out, char[] password) throws IOException {
        return encrypt(out, password, newSalt());
    }

    /** Encrypts with the given salt (lets many files share one derived key). */
    public static OutputStream encrypt(OutputStream out, char[] password, byte[] salt) throws IOException {
        return new EncOut(out, key(password, salt, ITERATIONS), salt);
    }

    public static InputStream decrypt(InputStream in, char[] password) throws IOException {
        return new DecIn(in, password);
    }

    /** True if the stream starts with the backup magic (doesn't consume it if markSupported). */
    public static boolean looksEncrypted(byte[] head) {
        return head != null && head.length >= 4 && head[0] == MAGIC[0] && head[1] == MAGIC[1]
                && head[2] == MAGIC[2] && head[3] == MAGIC[3];
    }

    static byte[] aad(byte[] header, int index, boolean last) {
        ByteBuffer b = ByteBuffer.allocate(header.length + 5);
        b.put(header).putInt(index).put((byte) (last ? 1 : 0));
        return b.array();
    }

    static byte[] nonce(byte[] prefix, int index) {
        return ByteBuffer.allocate(12).put(prefix).putInt(index).array();
    }

    private static final class EncOut extends FilterOutputStream {
        private final SecretKey key;
        private final byte[] header;
        private final byte[] prefix = new byte[8];
        private final byte[] buf = new byte[CHUNK];
        private int len;
        private int index;
        private boolean closed;

        EncOut(OutputStream out, SecretKey key, byte[] salt) throws IOException {
            super(out);
            this.key = key;
            RNG.nextBytes(prefix);
            ByteBuffer h = ByteBuffer.allocate(HEADER_LEN);
            h.put(MAGIC).put((byte) VERSION).put((byte) KDF_PBKDF2_SHA256).putInt(ITERATIONS)
                    .put(salt).put(prefix).putInt(CHUNK);
            header = h.array();
            out.write(header);
        }

        @Override public void write(int b) throws IOException {
            if (len == CHUNK) flushChunk(false);
            buf[len++] = (byte) b;
        }

        @Override public void write(byte[] b, int off, int n) throws IOException {
            while (n > 0) {
                if (len == CHUNK) flushChunk(false);
                int c = Math.min(n, CHUNK - len);
                System.arraycopy(b, off, buf, len, c);
                len += c;
                off += c;
                n -= c;
            }
        }

        private void flushChunk(boolean last) throws IOException {
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LEN * 8, nonce(prefix, index)));
                c.updateAAD(aad(header, index, last));
                byte[] ct = c.doFinal(buf, 0, len);
                out.write(ByteBuffer.allocate(4).putInt(len | (last ? FINAL : 0)).array());
                out.write(ct);
            } catch (GeneralSecurityException e) {
                throw new IOException("Encryption failed", e);
            }
            index++;
            len = 0;
        }

        @Override public void flush() throws IOException {
            out.flush();
        }

        @Override public void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                flushChunk(true);
                out.flush();
            } finally {
                Arrays.fill(buf, (byte) 0);
                out.close();
            }
        }
    }

    private static final class DecIn extends InputStream {
        private final DataInputStream in;
        private final SecretKey key;
        private final byte[] header = new byte[HEADER_LEN];
        private final byte[] prefix = new byte[8];
        private final int chunkSize;
        private byte[] plain = new byte[0];
        private int pos;
        private int index;
        private boolean done;

        DecIn(InputStream raw, char[] password) throws IOException {
            in = new DataInputStream(raw);
            try {
                in.readFully(header);
            } catch (EOFException e) {
                throw new IOException("Not a Zangi backup file");
            }
            ByteBuffer h = ByteBuffer.wrap(header);
            byte[] magic = new byte[4];
            h.get(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("Not a Zangi backup file");
            int ver = h.get(), kdf = h.get(), iters = h.getInt();
            if (ver != VERSION || kdf != KDF_PBKDF2_SHA256) throw new IOException("Unsupported backup format");
            if (iters < 10_000 || iters > 10_000_000) throw new IOException("Corrupt backup header");
            byte[] salt = new byte[16];
            h.get(salt);
            h.get(prefix);
            chunkSize = h.getInt();
            if (chunkSize <= 0 || chunkSize > (16 << 20)) throw new IOException("Corrupt backup header");
            key = key(password, salt, iters);
            fill();                                      // first chunk: verifies the password now
        }

        private boolean fill() throws IOException {
            if (done) return false;
            int word;
            try {
                word = in.readInt();
            } catch (EOFException e) {
                throw new IOException("Backup file is truncated");
            }
            boolean last = (word & FINAL) != 0;
            int len = word & ~FINAL;
            if (len > chunkSize) throw new IOException("Corrupt backup chunk");
            byte[] ct = new byte[len + TAG_LEN];
            try {
                in.readFully(ct);
            } catch (EOFException e) {
                throw new IOException("Backup file is truncated");
            }
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LEN * 8, nonce(prefix, index)));
                c.updateAAD(aad(header, index, last));
                plain = c.doFinal(ct);
            } catch (javax.crypto.AEADBadTagException e) {
                throw new WrongPasswordException();
            } catch (GeneralSecurityException e) {
                throw new IOException("Decryption failed", e);
            }
            pos = 0;
            index++;
            if (last) {
                done = true;
                if (in.read() != -1) throw new IOException("Unexpected data after the end of the backup");
            }
            return true;
        }

        @Override public int read() throws IOException {
            while (pos >= plain.length) if (!fill()) return -1;
            return plain[pos++] & 0xff;
        }

        @Override public int read(byte[] b, int off, int n) throws IOException {
            if (n == 0) return 0;
            while (pos >= plain.length) if (!fill()) return -1;
            int c = Math.min(n, plain.length - pos);
            System.arraycopy(plain, pos, b, off, c);
            pos += c;
            return c;
        }

        @Override public int available() {
            return plain.length - pos;
        }

        @Override public void close() throws IOException {
            in.close();
        }
    }

    /** Small helper for tests/diagnostics: encrypt a byte array. */
    static byte[] encryptBytes(byte[] data, char[] password) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStream os = encrypt(bos, password);
        os.write(data);
        os.close();
        return bos.toByteArray();
    }
}
