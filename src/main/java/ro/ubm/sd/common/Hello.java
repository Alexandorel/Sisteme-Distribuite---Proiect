package ro.ubm.sd.common;

/** UDP multicast: {"tip":"HELLO","id":"n2","port":7002}. */
public record Hello(String tip, String id, Integer port) {

    public static Hello of(String id, int port) {
        return new Hello("HELLO", id, port);
    }
}
