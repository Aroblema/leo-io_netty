# IO & Netty
## 一、IO & IO模型
IO，无非涉及两个参与方，一个输入，一个输出。然而根据参与双方的设备类型，大体分为磁盘I/O和网络I/O。
不论何种IO类型，均为数据交互操作。
在讲述IO模型之前，同步(synchronous)IO、异步(asynchronous)IO、阻塞(blocking)IO和非阻塞(non-blocking)IO分别是什么，到底有什么区别？

== *首先从Linux背景环境下的network IO说起* ==
**参考文献：W．Richard Stevens著 "UNIX® Network Programming Volume 1, Third Edition: The Sockets Networking"，6.2 I/O Models**

在6.2章节中比较了5种IO Model：
- Blocking I/O Model
- Nonblocking I/O Model
- I/O Multiplexing Model
- Signal-Driven I/O Model
- Asynchronous I/O Model

由于Signal-Driven I/O Model在实际中并不常用，故只提及其余四种IO Model。

***说明：IO发生时涉及的对象和步骤***
对于一个network IO（以read举例），它涉及到两个系统对象，一个是调用这个IO的process(or thread)，另一个是系统内核(kernel)。当一个read操作发生，它会经历两个阶段：
1. 等待数据准备（waiting for the data to be ready）
2. 将数据从内核拷贝到进程中（copying data from the kernel to the process）

**这两点很重要，各IO Model的区别就在于这两个阶段有不同的情况**

### - Blocking I/O Model
在linux中，默认情况下所有的socket都是blocking的，一个典型的读操作流程如下图：
![BIO-01](.picture/BIO-01.png)
当用户进程调用了recvfrom这个系统调用，kernel就开始了IO第一个阶段：数据准备。对network IO来说，很多时候数据在一开始还没有到达（比如还没有收到一个完整的UDP包），这个时候kernel需要等待足够的数据到来。
此时在用户进程这边，整个进程会被阻塞。当kernel等到数据准备好了，它便会将数据从kernel中拷贝到用户内存，拷贝完成后kernel返回OK结果，用户进程才解除block状态重新运行起来。
所以，对于Blocking I/O Model的特点就是**在IO执行的两个阶段都被block了**。

### - Nonblocking I/O Model
linux下，可以通过设置socket使其变为non-blocking。当对一个non-blocking socket执行读操作时，流程如下：
![NonBIO](.picture/NonBIO-01.png)
从图中可以看出，当用户进程发出read操作时，如果kernel中的数据还没准备好，那么它并不会block用户进程，而是立刻返回一个error。从用户进程的角度来说，它发起一次read操作后并不需要等待，而是马上得到了一个结果。
用户进程判断结果是error时，它就知道数据还没有准备好，于是它可以再次发送read操作。一旦kernel中的数据准备好了，并且再次收到用户进程的system call，那么它马上就将数据拷贝到用户内存然后返回OK结果。
所以，对于Nonblocking I/O Model来说，用户进程需要不断主动询问kernel数据准备好了没有，也就是**在kernel准备数据的这段期间，用户进程是活动的，非阻塞的，它不断轮询调用recvfrom等待返回成功结果**。
此时我们应注意到，当kernel数据准备好并收到用户进程的system call时，在kernel拷贝数据到用户内存的这段时间里，用户进程是阻塞的，因为没有立刻收到kernel的返回结果。直到kernel数据拷贝完毕并返回OK，用户进程解除block重新运行。

### - I/O Multiplexing Model
IO多路复用。这个词可能比较陌生，常听闻Netty的NIO模型等等。首先说说Linux API提供的I/O复用方式：select、poll和epoll。
|select|poll|epoll|
|---|---|---|---|
|操作方式|遍历|遍历|回调|
|底层实现|数组|数组|哈希表|
|IO效率|每次调用都进行线性遍历，时间复杂度O(n)|每次调用都进行线性遍历，时间复杂度O(n)|事件通知方式，每当有IO事件就绪，系统注册的回调函数就会被调用，时间复杂度O(1)|
|最大连接|有上限|无上限|无上限|
这种IO方式也被称为event driven IO。它们的好处在于单个process就可以同时处理多个网络连接的IO，基本原理就是select/poll/epoll这个function会不断轮询所负责的所有socket，当某个socket有数据到达了就通知用户进程。流程如图：
![I/O Multiplexing](.picture/MultiplexingIO.png)
当用户进程调用了select，那么整个进程会被block，同时kernel会"监视"select负责的所有socket，当任何一个socket中的数据准备好了，select就会返回。这个时候用户进程再调用read操作，等待kernel将数据拷贝到用户进程。
在I/O Multiplexing Model中，实际对于每一个socket，一般都设置成non-blocking。但如上图所示，可以发现整个用户的process其实一直被block。需要注意的是**process是被select这个函数block了，而不是被socket IO给block的**。

### - Asynchronous I/O Model
异步IO。首先看一下它的流程：
![AIO](.picture/AIO-01.png)
用户进程发起read操作后，立刻就可以开始去做其他事情了。
另一方面，从kernel的角度，当它收到一个asynchronous read之后，首先它立刻返回，所以不会对用户进程产生任何block。
之后kernel会等待数据准备完成，然后将数据拷贝到用户内存，当这一切都完成后，kernel会给用户进程发送一个signal，告诉它read操作完成。
**对于Asynchronous I/O Model，用户进程在kernel等待数据和拷贝数据两个阶段都没有被block**。

### - Comparison of the I/O Models
上述4种I/O Model均介绍完毕，再**回顾最初的问题**：阻塞(blocking)和非阻塞(non-blocking)，同步(synchronous)和异步(asynchronous)的区别
- *blocking vs non-blocking*
前面其实已经很明确的说明了这两者的区别。Blocking I/O会一直block住对应的用户进程直到操作完成，而Nonblocking I/O在kernel准备数据期间，用户进程是活动的，不断轮询调用recvfrom等待返回成功结果。**有趣的是在kernel拷贝数据阶段它们的用户进程均被block**。
- *synchronous vs asynchronous*
在说明synchronous和asynchronous区别之前，需要先给出双方的定义。参考W．Richard Stevens给出的定义：
***A synchronous I/O operation causes the requesting process to be blocked until that I/O operation completes;***
***An asynchronous I/O operation does not cause the requesting process to be blocked;***
也就是：
同步I/O操作导致请求进程被阻塞，直到该I/O操作完成；
异步I/O操作不会导致请求进程被阻塞；
两者的区别在于**synchronous I/O做I/O operation时会导致process阻塞，而asynchronous I/O不会**。那么按照W．Richard Stevens的定义，前边讲述的**Blocking I/O、Nonblocking I/O、I/O Multiplexing都属于synchronous I/O**。
对于asynchronous I/O，当进程发起IO操作之后，就直接收到返回再也不理睬了，该干嘛干嘛直到kernel发送一个信号告知进程IO操作完成。**整个过程中，进程完全没有被block**。
---
综上，对各个I/O Model的比较如图所示：
![Comparison](.picture/Comparison.png)
至此，经过上面的介绍，我们会发现non-blocking IO和asynchronous IO的区别还是较为明显的。在non-blocking IO中，虽然进程大部分时间都不会被block，但需要进程主动check，且当数据准备完毕后，也需要进程主动调用recvfrom并等待kernel将数据拷贝至用户内存完成。
而asynchronous IO则完全不同，它就像是用户进程完全将IO操作任务交给了他人（kernel）完成，他人完成后发信号通知就行。用户进程不需要去检查IO操作的状态，也不需要等待kernel拷贝数据。

## 二、Java中的网络IO模型
== *以下主要讲述Java中的网络IO模型* ==

**Java共支持3种网络编程IO模式：BIO、NIO、AIO**
### - 同步、异步、阻塞和非阻塞
- 同步：执行一个操作后，等待结果，然后再继续执行后续的操作
- 异步：执行一个操作后，可以去执行其他的操作，然后等待通知再回来执行先前没有执行完的操作
- 阻塞：进程给CPU传达一个任务，一直等待CPU处理完成，再执行后续操作
- 非阻塞：进程给CPU传达任务后，继续处理后续的操作，隔段时间回来询问之前的操作是否完成（轮询）
### - BIO(Blocking IO)
同步阻塞模型，一个客户端对应一个处理线程
![BIO-02](.picture/BIO-02.png)
**应用场景：**
BIO适合用于连接数目较小且固定的架构，这种方式对服务器资源要求较高，但程序简单易懂

**缺点：**
1. IO代码中的read操作是阻塞的，如果连接不做数据读写会导致线程阻塞，浪费资源
2. 如果客户端连接很多，会导致服务器线程太多，压力太大

### - NIO(Non Blocking IO)
### - AIO(NIO 2.0)
