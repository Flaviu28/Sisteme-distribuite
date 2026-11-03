package ro.ubm.sd.client;

import ro.ubm.sd.common.Protocol;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Client {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        try {
            switch (command) {
                case "nodes" -> handleNodes(args);
                case "put" -> handlePut(args);
                case "get" -> handleGet(args);
                default -> printUsage();
            }
        } catch (Exception e) {
            System.err.println("Eroare client: " + e.getMessage());
        }
    }

    private static void handleNodes(String[] args) throws IOException {
        int team = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        InetAddress group = InetAddress.getByName(Protocol.MULTICAST_IP_PREFIX + team);
        int port = Protocol.MULTICAST_PORT;

        Set<String> discovered = new HashSet<>();
        try (MulticastSocket socket = new MulticastSocket(port)) {
            SocketAddress groupAddress = new InetSocketAddress(group, port);
            NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
            socket.joinGroup(groupAddress, netIf);
            socket.setSoTimeout(3000); // ascultă 3 secunde

            long start = System.currentTimeMillis();
            byte[] buf = new byte[1024];

            System.out.println("Căutare noduri pe grupul multicast (3 secunde)...");
            while (System.currentTimeMillis() - start < 3000) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    String[] p = msg.split(";");
                    if (p.length == 3 && p[0].equals("HELLO")) {
                        String nodeInfo = String.format("%s@%s:%s", p[1], packet.getAddress().getHostAddress(), p[2]);
                        if (discovered.add(nodeInfo)) {
                            System.out.println(nodeInfo);
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                    break;
                }
            }
        }
    }

    private static void handlePut(String[] args) throws IOException {
        // put <host:port> <cale_fisier>
        if (args.length < 3) {
            System.err.println("Utilizare: java Client put <host:port> <cale_fisier>");
            return;
        }

        String[] target = args[1].split(":");
        String host = target[0];
        int port = Integer.parseInt(target[1]);
        Path file = Paths.get(args[2]);

        if (!Files.exists(file)) {
            System.err.println("Fișierul nu există: " + file);
            return;
        }

        String sha = Protocol.checksum(file);
        long size = Files.size(file);
        String fileName = file.getFileName().toString();

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            Protocol.writeHeader(out, "PUT " + fileName + " " + size + " " + sha);

            try (InputStream fileIn = Files.newInputStream(file)) {
                fileIn.transferTo(out);
            }
            out.flush();

            String response = Protocol.readHeader(in);
            System.out.println(response);
        }
    }

    private static void handleGet(String[] args) throws IOException {
        // get <host:port> <nume_fisier> <destinatie>
        if (args.length < 4) {
            System.err.println("Utilizare: java Client get <host:port> <nume_fisier> <director_destinatie>");
            return;
        }

        String[] target = args[1].split(":");
        String host = target[0];
        int port = Integer.parseInt(target[1]);
        String fileName = args[2];
        Path destDir = Paths.get(args[3]);

        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            Protocol.writeHeader(out, "GET " + fileName);
            String response = Protocol.readHeader(in);

            if (response.startsWith("FILE")) {
                String[] parts = response.split(" ");
                long size = Long.parseLong(parts[1]);
                String expectedChecksum = parts[2];

                Path destFile = destDir.resolve(fileName);
                String actualChecksum = Protocol.receiveBytes(in, size, destFile);

                if (actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                    System.out.printf("descărcat %s, checksum OK%n", destFile);
                } else {
                    System.err.println("EROARE: Checksum invalid!");
                }
            } else {
                System.out.println(response); // NOT_FOUND sau ERROR
            }
        }
    }

    private static void printUsage() {
        System.out.println("Comenzi disponibile:");
        System.out.println("  nodes [numar_echipa]");
        System.out.println("  put <host:port> <cale_fisier>");
        System.out.println("  get <host:port> <nume_fisier> <director_destinatie>");
    }
}
