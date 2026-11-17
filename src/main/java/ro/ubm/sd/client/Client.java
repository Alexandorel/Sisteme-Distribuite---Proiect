package ro.ubm.sd.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

import ro.ubm.sd.common.Header;
import ro.ubm.sd.common.Protocol;

public final class Client {

    private static final int CONNECT_TIMEOUT_MS = 3000;

    public static void main(String[] args) {
        System.out.println("[client] comenzi: put <host:port> <fisier>, get <host:port> <nume> <destinatie>, exit");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            System.out.flush();
            if (!sc.hasNextLine()) {
                return;
            }
            String[] p = sc.nextLine().trim().split("\\s+");
            try {
                switch (p[0]) {
                    case "" -> { }
                    case "exit" -> {
                        return;
                    }
                    case "put" -> {
                        if (p.length != 3) {
                            System.out.println("folosire: put <host:port> <fisier>");
                        } else {
                            put(p[1], Path.of(p[2]));
                        }
                    }
                    case "get" -> {
                        if (p.length != 4) {
                            System.out.println("folosire: get <host:port> <nume> <destinatie>");
                        } else {
                            get(p[1], p[2], Path.of(p[3]));
                        }
                    }
                    default -> System.out.println("comanda necunoscuta: " + p[0]);
                }
            } catch (IOException | RuntimeException e) {
                System.out.println("eroare: " + e.getMessage());
            }
        }
    }

    private static void put(String node, Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            System.out.println("fisier inexistent: " + file);
            return;
        }
        String sha = Protocol.checksum(file);
        try (Socket s = connect(node)) {
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());
            Protocol.writeHeader(out, Header.put(file.getFileName().toString(), Files.size(file), sha));
            try (InputStream data = Files.newInputStream(file)) {
                data.transferTo(out);
            }
            out.flush();
            System.out.println(describe(Protocol.readHeader(in)));
        }
    }

    private static void get(String node, String nume, Path destDir) throws IOException {
        try (Socket s = connect(node)) {
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());
            Protocol.writeHeader(out, Header.get(nume));
            Header r = Protocol.readHeader(in);
            if (!r.tip().equals("FILE")) {
                System.out.println(describe(r));
                return;
            }
            if (r.dimensiune() == null || r.checksum() == null) {
                throw new IOException("raspuns FILE incomplet");
            }
            Files.createDirectories(destDir);
            Path target = destDir.resolve(nume);
            Path tmp = destDir.resolve(nume + ".part");
            String actual;
            try {
                actual = Protocol.receiveBytes(in, r.dimensiune(), tmp);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
            if (!actual.equals(r.checksum())) {
                Files.delete(tmp);
                System.out.println("checksum gresit, fisierul descarcat a fost sters");
                return;
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("descarcat " + target + ", checksum OK");
        }
    }

    private static Socket connect(String hostPort) throws IOException {
        int i = hostPort.lastIndexOf(':');
        if (i < 0) {
            throw new IllegalArgumentException("adresa trebuie sa fie host:port, nu " + hostPort);
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(hostPort.substring(0, i), Integer.parseInt(hostPort.substring(i + 1))),
                CONNECT_TIMEOUT_MS);
        return s;
    }

    private static String describe(Header r) {
        return switch (r.tip()) {
            case "STORED" -> "STORED " + r.checksum();
            case "ERROR" -> "ERROR " + r.motiv();
            default -> r.tip();
        };
    }

    private Client() {
    }
}
