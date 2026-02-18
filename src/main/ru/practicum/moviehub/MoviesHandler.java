package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equalsIgnoreCase("GET")) {
            if (path.equals("/movies") || path.equals("/movies/")) {
                handleGetAllMovies(exchange);
            } else {
                // Здесь будет обработка GET /movies/{id}
                sendBadRequest(exchange, "Некорректный путь");
            }
        } else {
            sendMethodNotAllowed(exchange);
        }
    }

    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getAllMovies();
        String response = gson.toJson(movies);
        sendText(exchange, response, 200);
    }
}