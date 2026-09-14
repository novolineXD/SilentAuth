package dev.silentauth.net;

import dev.silentauth.proxy.ProxyEntry;
import dev.silentauth.proxy.ProxySockets;
import dev.silentauth.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public final class GameProxyRelay {

    private static final int HANDSHAKE_TIMEOUT_MS = 15000;
    private static final int MAX_PACKET_SIZE = 32767;

    private static GameProxyRelay active;

    private final ProxyEntry proxy;
    private final String targetHost;
    private final int targetPort;
    private final ServerSocket listener;
    private volatile boolean running = true;

    private GameProxyRelay(ProxyEntry proxy, String targetHost, int targetPort, ServerSocket listener) {
        this.proxy = proxy;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.listener = listener;
    }

    public static synchronized GameProxyRelay start(ProxyEntry proxy, String targetHost, int targetPort)
            throws IOException {
        stopActive();
        ServerSocket listener = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        GameProxyRelay relay = new GameProxyRelay(proxy, targetHost, targetPort, listener);
        relay.acceptLoop();
        active = relay;
        Log.info("Relay for " + targetHost + ":" + targetPort + " listening on 127.0.0.1:" + relay.getLocalPort()
                + " through " + proxy.describe());
        return relay;
    }

    public static synchronized void stopActive() {
        if (active != null) {
            active.stop();
            active = null;
        }
    }

    public int getLocalPort() {
        return listener.getLocalPort();
    }

    public void stop() {
        running = false;
        try {
            listener.close();
        } catch (IOException ignored) {
            return;
        }
    }

    private void acceptLoop() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    Socket client;
                    try {
                        client = listener.accept();
                    } catch (IOException e) {
                        return;
                    }
                    handleAsync(client);
                }
            }
        }, "SilentAuth-Relay-Accept");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleAsync(final Socket client) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket upstream = null;
                try {
                    client.setTcpNoDelay(true);
                    upstream = ProxySockets.open(proxy, targetHost, targetPort, HANDSHAKE_TIMEOUT_MS);
                    bridge(client, upstream);
                } catch (IOException e) {
                    Log.warn("Relay connection failed: " + e.getMessage());
                    ProxySockets.closeQuietly(client);
                    ProxySockets.closeQuietly(upstream);
                }
            }
        }, "SilentAuth-Relay-Connection");
        thread.setDaemon(true);
        thread.start();
    }

    private void bridge(Socket client, Socket upstream) throws IOException {
        InputStream clientIn = client.getInputStream();
        OutputStream upstreamOut = upstream.getOutputStream();

        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        int length = readVarInt(clientIn, consumed);
        if (length <= 0 || length > MAX_PACKET_SIZE) {
            upstreamOut.write(consumed.toByteArray());
        } else {
            byte[] packet = readExactly(clientIn, length);
            byte[] rewritten = rewriteHandshake(packet);
            if (rewritten == null) {
                upstreamOut.write(consumed.toByteArray());
                upstreamOut.write(packet);
            } else {
                writeVarInt(upstreamOut, rewritten.length);
                upstreamOut.write(rewritten);
            }
        }
        upstreamOut.flush();

        pump(clientIn, upstreamOut, client, upstream);
        pump(upstream.getInputStream(), client.getOutputStream(), client, upstream);
    }

    private byte[] rewriteHandshake(byte[] packet) {
        try {
            Cursor cursor = new Cursor(packet);
            if (cursor.readVarInt() != 0x00) {
                return null;
            }
            int protocol = cursor.readVarInt();
            String address = cursor.readString();
            cursor.readUnsignedShort();
            int nextState = cursor.readVarInt();

            String suffix = "";
            int marker = address.indexOf('\0');
            if (marker >= 0) {
                suffix = address.substring(marker);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeVarInt(out, 0x00);
            writeVarInt(out, protocol);
            writeString(out, targetHost + suffix);
            out.write((targetPort >> 8) & 0xFF);
            out.write(targetPort & 0xFF);
            writeVarInt(out, nextState);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private void pump(final InputStream in, final OutputStream out, final Socket a, final Socket b) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[8192];
                try {
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (IOException ignored) {
                } finally {
                    ProxySockets.closeQuietly(a);
                    ProxySockets.closeQuietly(b);
                }
            }
        }, "SilentAuth-Relay-Pump");
        thread.setDaemon(true);
        thread.start();
    }

    private static int readVarInt(InputStream in, ByteArrayOutputStream echo) throws IOException {
        int result = 0;
        int shift = 0;
        while (shift < 35) {
            int current = in.read();
            if (current < 0) {
                throw new IOException("Stream ended inside a VarInt");
            }
            echo.write(current);
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IOException("VarInt is too long");
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        int remaining = value;
        while (true) {
            if ((remaining & 0xFFFFFF80) == 0) {
                out.write(remaining);
                return;
            }
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new IOException("Stream ended after " + offset + " of " + length + " bytes");
            }
            offset += read;
        }
        return buffer;
    }

    private static final class Cursor {

        private final byte[] data;
        private int index;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private int read() throws IOException {
            if (index >= data.length) {
                throw new IOException("Packet ended early");
            }
            return data[index++] & 0xFF;
        }

        private int readVarInt() throws IOException {
            int result = 0;
            int shift = 0;
            while (shift < 35) {
                int current = read();
                result |= (current & 0x7F) << shift;
                if ((current & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            throw new IOException("VarInt is too long");
        }

        private int readUnsignedShort() throws IOException {
            return (read() << 8) | read();
        }

        private String readString() throws IOException {
            int length = readVarInt();
            if (length < 0 || index + length > data.length) {
                throw new IOException("String length " + length + " does not fit the packet");
            }
            String value = new String(Arrays.copyOfRange(data, index, index + length), "UTF-8");
            index += length;
            return value;
        }
    }
}
