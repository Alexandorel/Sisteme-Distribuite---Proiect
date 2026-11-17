package ro.ubm.sd.common;


public record Header(String tip, String nume, Long dimensiune, String checksum, String motiv) {

    /** client -> nod */
    public static Header put(String nume, long dimensiune, String checksum) {
        return new Header("PUT", nume, dimensiune, checksum, null);
    }

    /** nod -> client */
    public static Header stored(String checksum) {
        return new Header("STORED", null, null, checksum, null);
    }

    public static Header error(String motiv) {
        return new Header("ERROR", null, null, null, motiv);
    }

    /** client -> nod */
    public static Header get(String nume) {
        return new Header("GET", nume, null, null, null);
    }

    /** nod -> client */
    public static Header file(long dimensiune, String checksum) {
        return new Header("FILE", null, dimensiune, checksum, null);
    }

    public static Header notFound() {
        return new Header("NOT_FOUND", null, null, null, null);
    }
}
