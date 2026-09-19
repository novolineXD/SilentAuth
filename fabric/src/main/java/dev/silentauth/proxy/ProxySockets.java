package dev.silentauth.proxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;

public final class ProxySockets {

    private ProxySockets() {
    }

    public static Socket open(ProxyEntry proxy, String host, int port, int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(proxy.getHost(), proxy.getPort()), timeoutMs);
            switch (proxy.getType()) {
                case SOCKS5:
                    socks5(socket, proxy, host, port);
                    break;
                case SOCKS4:
                    socks4(socket, proxy, host, port);
                    break;
                case HTTP:
                default:
                    httpConnect(socket, proxy, host, port);
                    break;
            }
            socket.setSoTimeout(0);
            return socket;
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private static void socks5(Socket socket, ProxyEntry proxy, String host, int port) throws IOException {
        OutputStream out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());

        if (proxy.hasCredentials()) {
            out.write(new byte[] { 0x05, 0x02, 0x00, 0x02 });
        } else {
            out.write(new byte[] { 0x05, 0x01, 0x00 });
        }
        out.flush();

        int version = in.readUnsignedByte();
        int method = in.readUnsignedByte();
        if (version != 0x05) {
            throw new IOException("Proxy replied with SOCKS version " + version);
        }
        if (method == 0xFF) {
            throw new IOException("Proxy rejected every offered authentication method");
        }
        if (method == 0x02) {
            if (!proxy.hasCredentials()) {
                throw new IOException("Proxy wants a username and password");
            }
            byte[] user = proxy.getUsername().getBytes("UTF-8");
            byte[] pass = proxy.getPassword().getBytes("UTF-8");
            if (user.length > 255 || pass.length > 255) {
                throw new IOException("Proxy credentials are too long for SOCKS5");
            }
            byte[] request = new byte[3 + user.length + pass.length];
            int index = 0;
            request[index++] = 0x01;
            request[index++] = (byte) user.length;
            System.arraycopy(user, 0, request, index, user.length);
            index += user.length;
            request[index++] = (byte) pass.length;
            System.arraycopy(pass, 0, request, index, pass.length);
            out.write(request);
            out.flush();

            in.readUnsignedByte();
            if (in.readUnsignedByte() != 0x00) {
                throw new IOException("Proxy rejected the credentials");
            }
        } else if (method != 0x00) {
            throw new IOException("Proxy asked for unsupported authentication method " + method);
        }

        byte[] hostBytes = host.getBytes("UTF-8");
        if (hostBytes.length > 255) {
            throw new IOException("Hostname is too long for SOCKS5");
        }
        byte[] connect = new byte[7 + hostBytes.length];
        int index = 0;
        connect[index++] = 0x05;
        connect[index++] = 0x01;
        connect[index++] = 0x00;
        connect[index++] = 0x03;
        connect[index++] = (byte) hostBytes.length;
        System.arraycopy(hostBytes, 0, connect, index, hostBytes.length);
        index += hostBytes.length;
        connect[index++] = (byte) ((port >> 8) & 0xFF);
        connect[index] = (byte) (port & 0xFF);
        out.write(connect);
        out.flush();

        in.readUnsignedByte();
        int reply = in.readUnsignedByte();
        if (reply != 0x00) {
            throw new IOException("Proxy refused the connection: " + socks5Error(reply));
        }
        in.readUnsignedByte();
        int addressType = in.readUnsignedByte();
        if (addressType == 0x01) {
            in.skipBytes(4);
        } else if (addressType == 0x03) {
            in.skipBytes(in.readUnsignedByte());
        } else if (addressType == 0x04) {
            in.skipBytes(16);
        } else {
            throw new IOException("Proxy sent an unknown address type " + addressType);
        }
        in.skipBytes(2);
    }

    private static void socks4(Socket socket, ProxyEntry proxy, String host, int port) throws IOException {
        OutputStream out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());

        byte[] user = proxy.getUsername().getBytes("UTF-8");
        byte[] hostBytes = host.getBytes("UTF-8");
        byte[] request = new byte[9 + user.length + hostBytes.length + 1];
        int index = 0;
        request[index++] = 0x04;
        request[index++] = 0x01;
        request[index++] = (byte) ((port >> 8) & 0xFF);
        request[index++] = (byte) (port & 0xFF);
        request[index++] = 0x00;
        request[index++] = 0x00;
        request[index++] = 0x00;
        request[index++] = 0x01;
        System.arraycopy(user, 0, request, index, user.length);
        index += user.length;
        request[index++] = 0x00;
        System.arraycopy(hostBytes, 0, request, index, hostBytes.length);
        index += hostBytes.length;
        request[index] = 0x00;
        out.write(request);
        out.flush();

        in.readUnsignedByte();
        int status = in.readUnsignedByte();
        in.skipBytes(6);
        if (status != 0x5A) {
            throw new IOException("Proxy refused the connection: " + socks4Error(status));
        }
    }

    private static void httpConnect(Socket socket, ProxyEntry proxy, String host, int port) throws IOException {
        StringBuilder request = new StringBuilder();
        request.append("CONNECT ").append(host).append(':').append(port).append(" HTTP/1.1\r\n");
        request.append("Host: ").append(host).append(':').append(port).append("\r\n");
        if (proxy.hasCredentials()) {
            String raw = proxy.getUsername() + ":" + proxy.getPassword();
            request.append("Proxy-Authorization: Basic ")
                    .append(Base64.getEncoder().encodeToString(raw.getBytes("UTF-8")))
                    .append("\r\n");
        }
        request.append("Proxy-Connection: Keep-Alive\r\n\r\n");

        OutputStream out = socket.getOutputStream();
        out.write(request.toString().getBytes("UTF-8"));
        out.flush();

        String statusLine = readLine(socket);
        if (statusLine == null) {
            throw new IOException("Proxy closed the connection during CONNECT");
        }
        String[] parts = statusLine.split(" ");
        if (parts.length < 2 || !parts[1].equals("200")) {
            throw new IOException("Proxy refused CONNECT: " + statusLine.trim());
        }
        String line;
        do {
            line = readLine(socket);
        } while (line != null && !line.trim().isEmpty());
    }

    private static String readLine(Socket socket) throws IOException {
        StringBuilder sb = new StringBuilder();
        java.io.InputStream in = socket.getInputStream();
        int previous = -1;
        while (true) {
            int current = in.read();
            if (current < 0) {
                return sb.length() == 0 ? null : sb.toString();
            }
            if (previous == '\r' && current == '\n') {
                sb.setLength(Math.max(0, sb.length() - 1));
                return sb.toString();
            }
            sb.append((char) current);
            previous = current;
            if (sb.length() > 8192) {
                throw new IOException("Proxy sent an oversized response header");
            }
        }
    }

    private static String socks5Error(int code) {
        switch (code) {
            case 0x01: return "general failure";
            case 0x02: return "not allowed by ruleset";
            case 0x03: return "network unreachable";
            case 0x04: return "host unreachable";
            case 0x05: return "connection refused";
            case 0x06: return "TTL expired";
            case 0x07: return "command not supported";
            case 0x08: return "address type not supported";
            default: return "code " + code;
        }
    }

    private static String socks4Error(int code) {
        switch (code) {
            case 0x5B: return "request rejected or failed";
            case 0x5C: return "identd not reachable";
            case 0x5D: return "identd rejected the user id";
            default: return "code " + code;
        }
    }

    public static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            return;
        }
    }
}
