package com.leo.bio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author leo
 * @create 2020-05-26 8:23
 */
public class SocketServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9000);
        int i = 0;
        while (true) {
            System.out.println("---Waiting connect---" + ++i);
            // 阻塞方法，没有客户端连接时主线程阻塞
            Socket socket = serverSocket.accept();
            System.out.println("\n" + "There is a client connected");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        handler(socket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            // handler(socket);
        }
    }

    private static void handler(Socket socket) throws IOException {
        System.out.println("Start -> thread id: " + Thread.currentThread().getId());
        byte[] bytes = new byte[1024];
        System.out.println("prepare reading");
        // 接收客户端数据，阻塞方法，没有数据可读时线程阻塞
        int read = socket.getInputStream().read(bytes);
        System.out.println("finished reading");
        if (read != -1) {
            System.out.println("accept data from a client: " + new String(bytes, 0, read));
            System.out.println("End -> thread id: " + Thread.currentThread().getId());
        }
        socket.getOutputStream().write("HelloClient".getBytes());
        socket.getOutputStream().flush();
    }

}
