package com.leo.bio;

import java.io.IOException;
import java.net.Socket;

/**
 * @author leo
 * @create 2020-05-26 8:46
 */
public class SocketClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("127.0.0.1", 9000);
        socket.getOutputStream().write("HelloServer".getBytes());
        socket.getOutputStream().flush();
        byte[] bytes = new byte[1024];
        int read = socket.getInputStream().read(bytes);
        System.out.println("receive data from the server: " + new String(bytes, 0, read));
        socket.close();
    }
}
