package com.leo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author leo
 * @create 2020-05-28 17:20
 */
public class NIOClient {

    // 通道管理器
    private Selector selector;

    public static void main(String[] args) throws IOException {
        NIOClient client = new NIOClient();
        client.initClient("127.0.0.1", 9000);
        client.connect();
    }

    /**
     * 初始化客户端
     *
     * @param ip
     * @param port
     * @throws IOException
     */
    private void initClient(String ip, int port) throws IOException {
        // 获得一个socket通道
        SocketChannel channel = SocketChannel.open();
        // 设置通道非阻塞
        channel.configureBlocking(false);
        // 获得一个通道管理器
        this.selector = Selector.open();
        // 连接服务器（方法执行并没有实现连接，还需要调用finishConnect()才能完成连接）
        channel.connect(new InetSocketAddress(ip, port));
        // 将通道管理器和该通道绑定，并为该通道注册SelectionKey.OP_CONNECT事件
        channel.register(selector, SelectionKey.OP_CONNECT);
    }

    /**
     * 采用轮询的方式监听selector上是否有需要处理的事件
     *
     * @throws IOException
     */
    private void connect() throws IOException {
        // 轮询访问selector
        while (true) {
            this.selector.select();
            // 获取迭代器遍历SelectionKey集合
            Iterator<SelectionKey> it = this.selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                // 删除本次已处理的key，防止下次select重复处理
                it.remove();
                // 连接事件发生
                if (key.isConnectable()) {
                    SocketChannel channel = (SocketChannel) key.channel();
                    // 如果正在连接，则完成连接
                    if (channel.isConnectionPending()) {
                        channel.finishConnect();
                    }
                    // 设置非阻塞
                    channel.configureBlocking(false);
                    // 可以给服务端发送消息
                    ByteBuffer buffer = ByteBuffer.wrap("HelloServer".getBytes());
                    channel.write(buffer);
                    // 在和服务端连接成功后，为了可以接收服务端消息，需要给通道设置读权限
                    channel.register(this.selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) { // 获得可读事件
                    read(key);
                }
            }
        }
    }

    /**
     * 读取服务端发送的消息
     *
     * @param key
     * @throws IOException
     */
    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int len = channel.read(buffer);
        if (len != -1) {
            System.out.println("read data sending by the server: " + new String(buffer.array(), 0, len));
        }
    }

}
