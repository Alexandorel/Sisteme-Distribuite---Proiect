package ro.ubm.sd.client;

import ro.ubm.sd.common.Args;

/**
 * Client cu interfata in terminal. La Etapa 1 doar porneste: citeste adresa
 * coordonatorului din argumente si afiseaza comenzile disponibile.
 */
public final class Client {

    public static void main(String[] args) {
        Args a = new Args(args);
        String coordinator = a.require("coordinator");

        System.out.printf(
                "[client] coordonator configurat: %s; comenzi: put, get, ls, rm, nodes, exit%n",
                coordinator);
    }

    private Client() {
    }
}
