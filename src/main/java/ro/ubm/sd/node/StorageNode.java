package ro.ubm.sd.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import ro.ubm.sd.common.Args;

/**
 * Nod de stocare. La Etapa 1 doar porneste: citeste argumentele, creeaza
 * directorul de date daca lipseste si afiseaza configuratia primita.
 *
 * Rolul de coordonator este un flag (--coordinator), nu o clasa separata,
 * ca in Etapa 5 orice nod sa poata deveni coordonator cu acelasi cod.
 */
public final class StorageNode {

    public static void main(String[] args) throws IOException {
        Args a = new Args(args);

        String id = a.require("id");
        int port = Integer.parseInt(a.require("port"));
        Path dir = Path.of(a.require("dir"));
        boolean coordinator = a.has("coordinator");

        Files.createDirectories(dir);

        String rol = coordinator ? "coordonator" : "nod de stocare";
        System.out.printf("[%s] pornit: port %d, director %s, rol: %s%n", id, port, dir, rol);
    }

    private StorageNode() {
    }
}
