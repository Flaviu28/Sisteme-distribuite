package ro.ubm.sd.node;

import ro.ubm.sd.common.Protocol;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageNode {
    private final String id;
    private final int port;
    private final Path dir;
    private final int team;

    public StorageNode(String id, int port, String dirPath, int team) {
        this.id = id;
        this.port = port;
        this.dir = Paths.get(dirPath);
        this.team = team;
    }

    public void start() throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // Pornire serviciu de descoperire prin multicast
        DiscoveryService discovery = new DiscoveryService(id, port, team);
        discovery.start();

        System.out.printf("[%s] pornit: port TCP %d, director %s (echipa %d)%n", id, port, dir, team);

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.submit(() -> handleClient(clientSocket));
            }
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            String header = Protocol.readHeader(in);
            String[] tokens = header.split(" ");
            String command = tokens[0];

            if ("PUT".equals(command)) {
                handlePut(tokens, in, out);
            } else if ("GET".equals(command)) {
                handleGet(tokens, in, out);
            } else {
                Protocol.writeHeader(out, "ERROR comanda necunoscuta");
            }
        } catch (Exception e) {
            System.err.printf("[%s] Eroare la procesarea clientului: %s%n", id, e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handlePut(String[] tokens, DataInputStream in, DataOutputStream out) throws IOException {
        // PUT <nume> <dimensiune> <sha256>
        String name = tokens[1];
        long size = Long.parseLong(tokens[2]);
        String expectedChecksum = tokens[3];

        Path tmp = dir.resolve(name + ".part");
        String actualChecksum = Protocol.receiveBytes(in, size, tmp);

        if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
            Files.deleteIfExists(tmp);
            Protocol.writeHeader(out, "ERROR checksum diferit");
            return;
        }

        Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Protocol.writeHeader(out, "STORED " + actualChecksum);
        System.out.printf("[%s] fișier salvat cu succes: %s (%d octeți)%n", id, name, size);
    }

    private void handleGet(String[] tokens, DataInputStream in, DataOutputStream out) throws IOException {
        // GET <nume>
        String name = tokens[1];
        Path target = dir.resolve(name);

        if (!Files.exists(target) || Files.isDirectory(target)) {
            Protocol.writeHeader(out, "NOT_FOUND");
            return;
        }

        long size = Files.size(target);
        String sha = Protocol.checksum(target);

        Protocol.writeHeader(out, "FILE " + size + " " + sha);
        try (InputStream fileIn = Files.newInputStream(target)) {
            fileIn.transferTo(out);
        }
        out.flush();
        System.out.printf("[%s] fișier livrat: %s către client%n", id, name);
    }

    public static void main(String[] args) throws Exception {
        String id = null;
        int port = -1;
        String dir = null;
        int team = 1; // Valoare implicită pentru k

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-id": case "--id": id = args[++i]; break;
                case "-port": case "--port": port = Integer.parseInt(args[++i]); break;
                case "-dir": case "--dir": dir = args[++i]; break;
                case "-team": case "--team": team = Integer.parseInt(args[++i]); break;
            }
        }

        if (id == null || port == -1 || dir == null) {
            System.err.println("Utilizare: java ro.ubm.sd.node.StorageNode -id <id> -port <port> -dir <director> [-team <k>]");
            System.exit(1);
        }

        new StorageNode(id, port, dir, team).start();
    }
}
