package ro.ubm.sd.node;

import java.io.File;

public class StorageNode {
    public static void main(String[] args) {
        String id = null;
        int port = -1;
        String dir = null;
        boolean isCoordinator = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-id":
                case "--id":
                    if (i + 1 < args.length) id = args[++i];
                    break;
                case "-port":
                case "--port":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    break;
                case "-dir":
                case "--dir":
                    if (i + 1 < args.length) dir = args[++i];
                    break;
                case "-coordinator":
                case "--coordinator":
                    isCoordinator = true;
                    break;
            }
        }

        if (id == null || port == -1 || dir == null) {
            System.err.println("Utilizare: java ro.ubm.sd.node.StorageNode -id <id> -port <port> -dir <director> [--coordinator]");
            System.exit(1);
        }

        File storageDir = new File(dir);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String rol = isCoordinator ? "coordonator + nod de stocare" : "nod de stocare";
        System.out.printf("[%s] pornit: port %d, director %s, rol: %s%n", id, port, dir, rol);
    }
}
