package ro.ubm.sd.node;

import ro.ubm.sd.common.Protocol;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryService {
    public record Member(String id, InetAddress address, int port, long lastSeen) {}

    private final String selfId;
    private final int tcpPort;
    private final InetAddress group;
    private final int multicastPort = Protocol.MULTICAST_PORT;
    private final Map<String, Member> members = new ConcurrentHashMap<>();

    public DiscoveryService(String selfId, int tcpPort, int teamNumber) throws IOException {
        this.selfId = selfId;
        this.tcpPort = tcpPort;
        this.group = InetAddress.getByName(Protocol.MULTICAST_IP_PREFIX + teamNumber);
    }

    public void start() {
        // Firul 1: Emitere HELLO la fiecare 2 secunde
        Thread senderThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = String.format("HELLO;%s;%d", selfId, tcpPort);
                byte[] data = message.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, group, multicastPort);

                while (!Thread.currentThread().isInterrupted()) {
                    socket.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                System.err.println("[" + selfId + "] Eroare Discovery Sender: " + e.getMessage());
            }
        });
        senderThread.setDaemon(true);
        senderThread.start();

        // Firul 2: Ascultare mesaje pe grupul Multicast
        Thread receiverThread = new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(multicastPort)) {
                SocketAddress groupAddress = new InetSocketAddress(group, multicastPort);
                NetworkInterface netIf = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
                socket.joinGroup(groupAddress, netIf);

                byte[] buffer = new byte[1024];
                while (!Thread.currentThread().isInterrupted()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    String[] p = msg.split(";");

                    if (p.length != 3 || !p[0].equals("HELLO") || p[1].equals(selfId)) {
                        continue; // Ignoră pachetele invalide sau anunțul propriu
                    }

                    String otherId = p[1];
                    int otherPort = Integer.parseInt(p[2]);
                    InetAddress otherAddr = packet.getAddress();

                    Member m = new Member(otherId, otherAddr, otherPort, System.currentTimeMillis());
                    if (members.put(otherId, m) == null) {
                        System.out.printf("[%s] nod descoperit: %s@%s:%d (cunosc acum %d)%n",
                                selfId, otherId, otherAddr.getHostAddress(), otherPort, members.size());
                    }
                }
            } catch (Exception e) {
                System.err.println("[" + selfId + "] Eroare Discovery Receiver: " + e.getMessage());
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public Map<String, Member> getMembers() {
        return members;
    }
}
