package ro.ubm.sd.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class Protocol {

    private static final Gson GSON = new Gson();

    public static void writeHeader(DataOutputStream out, Header h) throws IOException {
        out.writeUTF(GSON.toJson(h));
        out.flush();
    }

    public static Header readHeader(DataInputStream in) throws IOException {
        String json = in.readUTF();
        Header h;
        try {
            h = GSON.fromJson(json, Header.class);
        } catch (JsonParseException e) {
            throw new IOException("antet invalid: " + json, e);
        }
        if (h == null || h.tip() == null) {
            throw new IOException("antet fara tip: " + json);
        }
        return h;
    }

    public static String checksum(Path file) throws IOException {
        MessageDigest sha = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), sha)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    public static String receiveBytes(InputStream in, long size, Path target) throws IOException {
        MessageDigest sha = sha256();
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        try (OutputStream out = Files.newOutputStream(target)) {
            while (remaining > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) {
                    throw new EOFException("conexiune inchisa dupa " + (size - remaining)
                            + " din " + size + " octeti");
                }
                out.write(buffer, 0, n);
                sha.update(buffer, 0, n);
                remaining -= n;
            }
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Protocol() {
    }
}
