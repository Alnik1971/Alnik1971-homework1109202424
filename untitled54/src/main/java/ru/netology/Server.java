package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server implements Runnable {

    protected int port;
    protected List<String> validPaths;
    protected Thread runningThread= null;
    protected final ExecutorService threadingPool = Executors.newFixedThreadPool(64);
    protected List<NameValuePair> quaryParams;

    public Server(int port, List<String> validPaths) {
        this.port = port;
        this.validPaths = validPaths;
        System.out.println("Сервер запущен!");
    }

    public void run() {
        synchronized(this){
            this.runningThread = Thread.currentThread();
        }
        // Перенесли все сюда, чтобы сокеты не закрывались
        try (final var serverSocket = new ServerSocket(this.port)) {
            while (true) {
                try (final var socket = serverSocket.accept()) {
                    this.threadingPool.execute(() -> {
                        try {
                            handling(socket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    // Добавили ожидание завершения действия
                    this.threadingPool.awaitTermination(1, TimeUnit.SECONDS);
                    // Закрыли сокет
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handling(Socket socket) throws IOException {
        try (
                final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                socket.close();
            }

            final var path = parts[1].split("\\?")[0];
            System.out.println(path);

            if (parts[1].split("\\?").length == 2) {
                final var params = parts[1].split("\\?")[1];
                this.quaryParams = URLEncodedUtils.parse(params, StandardCharsets.UTF_8);

            }

            System.out.println(getQuaryParam("login"));
            System.out.println(getQuaryParams());


            if (!this.validPaths.contains(path)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                socket.close();
            }

            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
            System.out.println("DONE");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<NameValuePair> getQuaryParam(String name) {
        if (this.quaryParams != null){
            return this.quaryParams.stream().filter(p -> p.getName().equals(name)).collect(Collectors.toList());
        }
        else {
//            throw new Exception("No params");
            return null;
        }
    }

    public List<NameValuePair> getQuaryParams()  {
        if (this.quaryParams != null){
            return this.quaryParams;
        }
        else {
            return null;
        }
    }

}