package com.sainnt.net;

import com.sainnt.dto.SignInResult;
import com.sainnt.dto.SignUpResult;
import com.sainnt.files.FileRepresentation;
import com.sainnt.files.RemoteFileRepresentation;
import com.sainnt.net.handler.LoginHandler;
import com.sainnt.net.requests.CreateFolderRequest;
import com.sainnt.net.requests.DeleteFileRequest;
import com.sainnt.net.requests.ListFilesRequest;
import com.sainnt.net.requests.UploadFileRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

@Slf4j
public class CloudClient {
    private final Map<Long, RemoteFileRepresentation> idToRemoteFile = new HashMap<>();
    private boolean connected = false;
    private static CloudClient client;
    private final HashMap<String, File> fileUploadRequests = new HashMap<>();
    private EventLoopGroup workerGroup;
    private Request currentRequest;
    private boolean performingRequest;
    private final BlockingQueue<Request> requestQueue = new ArrayBlockingQueue<>(15);

    public synchronized static CloudClient getClient() {
        if (client == null) {
            client = new CloudClient();
            client.initConnection();
        }
        return client;
    }

    private Channel channel;
    private Task<Channel> connectTask;

    public void initConnection() {
        if (connected) {
            log.info("initConnection() declined, already connected");
            return;
        }
        workerGroup = new NioEventLoopGroup();
        connectTask = new Task<>() {
            @Override
            protected Channel call() throws Exception {
                Bootstrap b = new Bootstrap();                    // (1)
                b.group(workerGroup);                             // (2)
                b.channel(NioSocketChannel.class)
                        .remoteAddress(new InetSocketAddress("localhost", 9096));                // (3)
                b.option(ChannelOption.SO_KEEPALIVE, true);
                b.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
//                        handler = new OperationHandler();
//                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter());

                    }
                });
                ChannelFuture f = b.connect();
                Channel chn = f.channel();
                f.sync();
                return chn;
            }

            @Override
            protected void succeeded() {
                log.info("Connected successfully");
                channel = getValue();
                connected = true;
            }

            @Override
            protected void failed() {
                workerGroup.shutdownGracefully();
                Throwable e = getException();
                log.error("Connection error", e);
                connected = false;
            }
        };
        Thread thread = new Thread(connectTask);
        thread.setDaemon(false);
        thread.start();
    }

    public void closeConnection() {
        if (!connected) {
            log.info("Connection close declined,not connected");
            return;
        }
        workerGroup.shutdownGracefully();
    }

    public void authenticate(String login, String password) {
        if (!connected) {
            log.info("Login declined client not connected");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws InterruptedException {
                ByteBuf buf = channel.alloc().buffer(12 + login.length() + password.length());
                buf.writeInt(5);
                buf.writeInt(login.length());
                buf.writeBytes(login.getBytes(StandardCharsets.UTF_8));
                buf.writeInt(password.length());
                buf.writeBytes(password.getBytes(StandardCharsets.UTF_8));
                channel.writeAndFlush(buf).sync();
                return null;
            }

            @Override
            protected void failed() {
                log.error("Error during authentication:", getException());
                connected = false;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public void register(String login, String email, String password) {
        if (!connected) {
            log.info("Registration declined client not connected");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws InterruptedException {
                ByteBuf buf = channel.alloc().buffer(16 + login.length() + email.length() + password.length());
                buf.writeInt(6);
                buf.writeInt(login.length());
                buf.writeBytes(login.getBytes(StandardCharsets.UTF_8));
                buf.writeInt(email.length());
                buf.writeBytes(email.getBytes(StandardCharsets.UTF_8));
                buf.writeInt(password.length());
                buf.writeBytes(password.getBytes(StandardCharsets.UTF_8));
                channel.writeAndFlush(buf).sync();
                return null;
            }

            @Override
            protected void failed() {
                log.error("Error during registration:", getException());
                connected = false;
            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public void initLoginHandler(Consumer<SignInResult> signInResultConsumer, Consumer<SignUpResult> signUpResultConsumer) {
        if (!connected) {
            try {
                channel = connectTask.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        channel.pipeline().addLast(new LoginHandler(signInResultConsumer, signUpResultConsumer));
    }

    public void requestChildrenFiles(long id) {
        requestQueue.add(new ListFilesRequest(id));
        pollRequest();
    }

    private synchronized void pollRequest() {
        if (!performingRequest && !requestQueue.isEmpty()) {
            currentRequest = requestQueue.poll();
            performingRequest = true;
            currentRequest.perform(channel);
        }
    }

    public void createRemoteDirectory(long parentId, String name) {
        requestQueue.add(new CreateFolderRequest(name, parentId));
        pollRequest();
    }

    public void handleFilesRequest(long id, Collection<RemoteFileRepresentation> files) {
        if (currentRequest instanceof ListFilesRequest) {
            assert id == ((ListFilesRequest) currentRequest).getDirId();
            RemoteFileRepresentation parent = idToRemoteFile.get(id);
            ObservableList<FileRepresentation> filesDestination = parent.getChildren();
            filesDestination.clear();
            files.forEach(file -> {
                file.setParent(parent);
                filesDestination.add(file);
                idToRemoteFile.put(file.getId(), file);
            });
            completeRequest();
        } else
            System.out.println("Current request is not hfr");
    }

    private void completeRequest() {
        performingRequest = false;
        currentRequest = null;
        pollRequest();
    }

    public void uploadFile(long dirId, File file) {
        requestQueue.add(new UploadFileRequest(dirId, file));
        pollRequest();
    }

    public void handleFileUploadResponse(long dirId, String name) {
        File file = ((UploadFileRequest) currentRequest).getFile();
        FileRegion fileRegion = new DefaultFileRegion(file, 0, file.length());
        channel.writeAndFlush(fileRegion);
    }

    public void renameFileRequest(long id, String name) {
        // Not implemented on server side yet
    }

    public void deleteFileRequest(long id) {
        requestQueue.add(new DeleteFileRequest(id));
        pollRequest();
    }

    public void deleteDirectoryRequest(long id) {
        // Not implemented on server side yet
    }

    public void addRemoteFileRepresentation(RemoteFileRepresentation item) {
        idToRemoteFile.put(item.getId(), item);
    }

    public boolean isPerformingRequest() {
        return performingRequest;
    }

    public void fileUploadCompleted(long fileId) {
        if (currentRequest instanceof UploadFileRequest request) {
            RemoteFileRepresentation dir = idToRemoteFile.get(request.getDirId());
            dir
                    .getChildren()
                    .add(new RemoteFileRepresentation(fileId, dir, request.getFile().getName(), false));
            completeRequest();
        }
    }

    public void deleteFileCompleted() {
        if (currentRequest instanceof DeleteFileRequest request) {
            long deletedId = request.getId();
            RemoteFileRepresentation file = idToRemoteFile.get(deletedId);
            file.getParent().getChildren().remove(file);
            completeRequest();
        }
    }
}
