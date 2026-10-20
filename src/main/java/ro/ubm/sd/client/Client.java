package ro.ubm.sd.client;

public class Client {
    public static void main(String[] args) {
        String coordinator = null;

        for (int i = 0; i < args.length; i++) {
            if ((args[i].equals("-coordinator") || args[i].equals("--coordinator")) && i + 1 < args.length) {
                coordinator = args[++i];
            }
        }

        if (coordinator == null) {
            System.err.println("Utilizare: java ro.ubm.sd.client.Client -coordinator <host:port>");
            System.exit(1);
        }

        System.out.printf("[client] coordonator configurat: %s; comenzi: put, get, ls, rm, nodes, exit%n", coordinator);
    }
}
