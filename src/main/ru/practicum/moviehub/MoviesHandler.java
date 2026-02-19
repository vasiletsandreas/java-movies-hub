package ru.practicum.moviehub.http;

import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private static final Pattern ID_PATTERN = Pattern.compile("^/movies/(\\d+)$");
    private static final Pattern MOVIES_PATH_PATTERN = Pattern.compile("^/movies/?$");
    private static final int MIN_YEAR = 1888;
    private static final int MAX_YEAR = Year.now().getValue() + 1;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        try {
            switch (method) {
                case "GET":
                    handleGetRequest(exchange, path, query);
                    break;
                case "POST":
                    handlePostRequest(exchange, path);
                    break;
                case "DELETE":
                    handleDeleteRequest(exchange, path);
                    break;
                default:
                    sendMethodNotAllowed(exchange);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            sendInternalServerError(exchange);
        }
    }

    private void handleGetRequest(HttpExchange exchange, String path, String query) throws IOException {
        if (MOVIES_PATH_PATTERN.matcher(path).matches()) {
            if (query != null && query.startsWith("year=")) {
                handleGetMoviesByYear(exchange, query);
            } else {
                handleGetAllMovies(exchange);
            }
        } else {
            java.util.regex.Matcher matcher = ID_PATTERN.matcher(path);
            if (matcher.matches()) {
                handleGetMovieById(exchange, matcher.group(1));
            } else {
                sendBadRequest(exchange, "Некорректный путь");
            }
        }
    }

    private void handlePostRequest(HttpExchange exchange, String path) throws IOException {
        if (!MOVIES_PATH_PATTERN.matcher(path).matches()) {
            sendBadRequest(exchange, "Некорректный путь для POST запроса");
            return;
        }

        // Проверка Content-Type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendUnsupportedMediaType(exchange);
            return;
        }

        // Чтение тела запроса
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        if (body.trim().isEmpty()) {
            sendUnprocessableEntity(exchange, "Ошибка валидации",
                    new String[]{"Тело запроса не должно быть пустым"});
            return;
        }

        try {
            Movie movie = gson.fromJson(body, Movie.class);

            // Валидация
            List<String> validationErrors = validateMovie(movie);

            if (!validationErrors.isEmpty()) {
                sendUnprocessableEntity(exchange, "Ошибка валидации",
                        validationErrors.toArray(new String[0]));
                return;
            }

            // Добавление фильма
            Movie createdMovie = store.addMovie(movie);
            String response = gson.toJson(createdMovie);
            sendCreated(exchange, response);

        } catch (JsonSyntaxException e) {
            sendUnprocessableEntity(exchange, "Ошибка валидации",
                    new String[]{"Некорректный JSON формат"});
        }
    }

    private void handleDeleteRequest(HttpExchange exchange, String path) throws IOException {
        java.util.regex.Matcher matcher = ID_PATTERN.matcher(path);
        if (matcher.matches()) {
            handleDeleteMovieById(exchange, matcher.group(1));
        } else {
            sendBadRequest(exchange, "Некорректный путь для DELETE запроса");
        }
    }

    private void handleGetAllMovies(HttpExchange exchange) throws IOException {
        List<Movie> movies = store.getAllMovies();
        String response = gson.toJson(movies);
        sendText(exchange, response, 200);
    }

    private void handleGetMovieById(HttpExchange exchange, String idStr) throws IOException {
        try {
            int id = Integer.parseInt(idStr);
            Movie movie = store.getMovieById(id);

            if (movie != null) {
                String response = gson.toJson(movie);
                sendText(exchange, response, 200);
            } else {
                sendNotFound(exchange, "Фильм не найден");
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный ID");
        }
    }

    private void handleDeleteMovieById(HttpExchange exchange, String idStr) throws IOException {
        try {
            int id = Integer.parseInt(idStr);
            boolean deleted = store.deleteMovieById(id);

            if (deleted) {
                sendNoContent(exchange);
            } else {
                sendNotFound(exchange, "Фильм не найден");
            }
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный ID");
        }
    }

    private void handleGetMoviesByYear(HttpExchange exchange, String query) throws IOException {
        String yearParam = query.substring(5); // Убираем "year="

        if (yearParam.isEmpty()) {
            sendBadRequest(exchange, "Некорректный параметр запроса — 'year'");
            return;
        }

        try {
            int year = Integer.parseInt(yearParam);
            List<Movie> movies = store.getMoviesByYear(year);
            String response = gson.toJson(movies);
            sendText(exchange, response, 200);
        } catch (NumberFormatException e) {
            sendBadRequest(exchange, "Некорректный параметр запроса — 'year'");
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie == null) {
            errors.add("Тело запроса не должно быть пустым");
            return errors;
        }

        // Проверка title
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название должно быть не длиннее 100 символов");
        }

        // Проверка year
        if (movie.getYear() == null) {
            errors.add("год должен быть указан");
        } else if (movie.getYear() < MIN_YEAR || movie.getYear() > MAX_YEAR) {
            errors.add(String.format("год должен быть между %d и %d", MIN_YEAR, MAX_YEAR));
        }

        return errors;
    }
}