package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    protected void sendText(HttpExchange exchange, String text, int statusCode) throws IOException {
        byte[] responseBytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    protected void sendError(HttpExchange exchange, String error, int statusCode) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error);
        sendText(exchange, gson.toJson(errorResponse), statusCode);
    }

    protected void sendErrorWithDetails(HttpExchange exchange, String error, String[] details, int statusCode) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error, details);
        sendText(exchange, gson.toJson(errorResponse), statusCode);
    }

    protected void sendNotFound(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, message, 404);
    }

    protected void sendBadRequest(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, message, 400);
    }

    protected void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, "Метод не поддерживается", 405);
    }

    protected void sendUnsupportedMediaType(HttpExchange exchange) throws IOException {
        sendError(exchange, "Неподдерживаемый тип содержимого", 415);
    }

    protected void sendUnprocessableEntity(HttpExchange exchange, String error, String[] details) throws IOException {
        sendErrorWithDetails(exchange, error, details, 422);
    }

    protected void sendCreated(HttpExchange exchange, String response) throws IOException {
        sendText(exchange, response, 201);
    }

    protected void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }

    protected void sendInternalServerError(HttpExchange exchange) throws IOException {
        sendError(exchange, "Внутренняя ошибка сервера", 500);
    }
}