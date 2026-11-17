package ro.ubm.sd.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ro.ubm.sd.common.Args;
import ro.ubm.sd.common.Header;
import ro.ubm.sd.common.Protocol;


public final class StorageNode {

    private static final long MAX_SIZE = 1L << 30; // 1 GB, see docs/cerinte.md
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String id;
    private final Path dir;
    private final Set<String> uploading = ConcurrentHashMap.newKeySet();

    private StorageNode(String id, Path dir) {
        this.id = id;
        this.dir = dir;
    }

    public static void main(String[] args) throws IOException {
        Args a = new Args(args);

        String id = a.require("id");
        int port = Integer.parseInt(a.require("port"));
        Path dir = Path.of(a.require("dir"));
        boolean coordinator = a.has("coordinator");

        Files.createDirectories(dir);

        StorageNode node = new StorageNode(id, dir);
        try (ServerSocket server = new ServerSocket(port)) {
            String rol = coordinator ? "coordonator" : "nod de stocare";
            node.log("pornit: port %d, director %s, rol: %s", port, dir, rol);
            node.serve(server);
        }
    }

    private void serve(ServerSocket server) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        while (true) {
            Socket s = server.accept();
            pool.execute(() -> handle(s));
        }
    }

    private void handle(Socket s) {
        try (s) {
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            Header h = Protocol.readHeader(in);
            switch (h.tip()) {
                case "PUT" -> handlePut(h, in, out);
                case "GET" -> handleGet(h, out);
                default -> {
                    Protocol.writeHeader(out, Header.error("tip necunoscut: " + h.tip()));
                    log("%s -> ERROR tip necunoscut", h.tip());
                }
            }
        } catch (IOException e) {
            log("conexiune %s: %s", s.getRemoteSocketAddress(), e.getMessage());
        }
    }

    private void handlePut(Header h, DataInputStream in, DataOutputStream out) throws IOException {
        String nume = h.nume();
        Long size = h.dimensiune();
        if (size == null || size < 0 || size > MAX_SIZE) {
            // without a valid size we can't skip the body, so the connection just closes
            Protocol.writeHeader(out, Header.error("dimensiune invalida (maxim 1 GB)"));
            log("PUT %s -> ERROR dimensiune invalida: %s", nume, size);
            return;
        }
        if (!validName(nume) || h.checksum() == null) {
            refuse(in, out, size, "antet PUT invalid: " + nume);
            return;
        }
        if (!uploading.add(nume)) {
            refuse(in, out, size, "fisier in curs de incarcare: " + nume);
            return;
        }
        try {
            Path target = dir.resolve(nume);
            if (Files.exists(target)) {
                refuse(in, out, size, "fisier existent: " + nume);
                return;
            }
            Path tmp = dir.resolve(nume + ".part");
            String actual;
            try {
                actual = Protocol.receiveBytes(in, size, tmp);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
            if (!actual.equals(h.checksum())) {
                Files.delete(tmp);
                Protocol.writeHeader(out, Header.error("checksum diferit"));
                log("PUT %s -> ERROR checksum diferit", nume);
                return;
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            Protocol.writeHeader(out, Header.stored(actual));
            log("PUT %s (%d octeti) -> STORED", nume, size);
        } finally {
            uploading.remove(nume);
        }
    }

    // Reply first, then drain the body, so the client can finish sending and read the reply.
    private void refuse(DataInputStream in, DataOutputStream out, long size, String motiv) throws IOException {
        Protocol.writeHeader(out, Header.error(motiv));
        in.skipNBytes(size);
        log("PUT -> ERROR %s", motiv);
    }

    private void handleGet(Header h, DataOutputStream out) throws IOException {
        String nume = h.nume();
        if (!validName(nume)) {
            Protocol.writeHeader(out, Header.error("nume invalid"));
            log("GET %s -> ERROR nume invalid", nume);
            return;
        }
        Path f = dir.resolve(nume);
        if (!Files.isRegularFile(f)) {
            Protocol.writeHeader(out, Header.notFound());
            log("GET %s -> NOT_FOUND", nume);
            return;
        }
        long size = Files.size(f);
        Protocol.writeHeader(out, Header.file(size, Protocol.checksum(f)));
        try (InputStream data = Files.newInputStream(f)) {
            data.transferTo(out);
        }
        out.flush();
        log("GET %s (%d octeti) -> FILE", nume, size);
    }

    // Names come from the network: reject anything that could escape dir or clash with .part files.
    private static boolean validName(String nume) {
        return nume != null && !nume.isBlank() && !nume.equals(".") && !nume.equals("..")
                && !nume.endsWith(".part")
                && nume.chars().noneMatch(c -> c < 32 || "/\\:".indexOf(c) >= 0);
    }

    private void log(String fmt, Object... args) {
        System.out.printf("%s [%s] %s%n", LocalTime.now().format(TIME), id, String.format(fmt, args));
    }
}
