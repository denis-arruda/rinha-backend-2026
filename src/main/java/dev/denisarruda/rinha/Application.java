package dev.denisarruda.rinha;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Application {

    static final System.Logger LOGGER = System.getLogger(Application.class.getName());
    static final String FRAUD_SCORE_RESPONSE = "{\"approved\":false,\"fraud_score\":0.8}";

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ready", Application::ready);
        server.createContext("/fraud-score", Application::fraudScore);
        server.start();
        LOGGER.log(System.Logger.Level.INFO, "Server started on port 8080");
    }

    static void ready(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    static void fraudScore(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        var requestBody = new String(exchange.getRequestBody().readAllBytes());
        var input = FraudRequestParser.parse(requestBody);
        var features = Normalizer.normalize(input);
        var body = FRAUD_SCORE_RESPONSE.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
