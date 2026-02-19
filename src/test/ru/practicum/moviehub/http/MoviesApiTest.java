package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoviesApiTest {
    private static final int PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;
    private static final Gson gson = new Gson();
    private static final int CURRENT_YEAR_PLUS_ONE = Year.now().getValue() + 1;

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, PORT);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    // ==================== GET /movies ====================

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest request = createGetRequest("/movies");
        HttpResponse<String> response = sendRequest(request);

        assertSuccessResponse(response, 200);
        assertContentType(response);

        List<Movie> movies = parseMovieList(response.body());
        assertTrue(movies.isEmpty(), "Список фильмов должен быть пустым");
    }

    @Test
    void getMovies_whenMoviesExist_returnsMoviesList() throws Exception {
        // Добавляем фильмы
        Movie movie1 = createAndAddMovie("Фильм 1", 2020);
        Movie movie2 = createAndAddMovie("Фильм 2", 2021);

        HttpRequest request = createGetRequest("/movies");
        HttpResponse<String> response = sendRequest(request);

        assertSuccessResponse(response, 200);
        assertContentType(response);

        List<Movie> movies = parseMovieList(response.body());
        assertEquals(2, movies.size(), "Должно быть 2 фильма");

        // Проверяем, что фильмы содержат правильные данные
        assertTrue(movies.stream().anyMatch(m -> m.getTitle().equals("Фильм 1") && m.getYear() == 2020));
        assertTrue(movies.stream().anyMatch(m -> m.getTitle().equals("Фильм 2") && m.getYear() == 2021));
    }

    // ==================== POST /movies ====================

    @Test
    void postMovie_whenValidData_createsMovie() throws Exception {
        Movie newMovie = new Movie(null, "Новый фильм", 2023);
        String requestBody = gson.toJson(newMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(201, response.statusCode(), "Статус код должен быть 201 Created");
        assertContentType(response);

        Movie createdMovie = gson.fromJson(response.body(), Movie.class);
        assertNotNull(createdMovie.getId(), "ID должен быть присвоен");
        assertEquals("Новый фильм", createdMovie.getTitle());
        assertEquals(2023, createdMovie.getYear());

        // Проверяем, что фильм действительно сохранился
        Movie savedMovie = store.getMovieById(createdMovie.getId());
        assertNotNull(savedMovie);
        assertEquals(createdMovie.getTitle(), savedMovie.getTitle());
    }

    @Test
    void postMovie_whenTitleEmpty_returns422WithDetails() throws Exception {
        Movie invalidMovie = new Movie(null, "", 2023);
        String requestBody = gson.toJson(invalidMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode(), "Статус код должен быть 422 Unprocessable Entity");
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "название не должно быть пустым"));
    }

    @Test
    void postMovie_whenTitleTooLong_returns422WithDetails() throws Exception {
        String longTitle = "a".repeat(101);
        Movie invalidMovie = new Movie(null, longTitle, 2023);
        String requestBody = gson.toJson(invalidMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "название должно быть не длиннее 100 символов"));
    }

    @Test
    void postMovie_whenYearTooEarly_returns422WithDetails() throws Exception {
        Movie invalidMovie = new Movie(null, "Старый фильм", 1887);
        String requestBody = gson.toJson(invalidMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "год должен быть между 1888 и " + CURRENT_YEAR_PLUS_ONE));
    }

    @Test
    void postMovie_whenYearTooLate_returns422WithDetails() throws Exception {
        Movie invalidMovie = new Movie(null, "Будущий фильм", CURRENT_YEAR_PLUS_ONE + 1);
        String requestBody = gson.toJson(invalidMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "год должен быть между 1888 и " + CURRENT_YEAR_PLUS_ONE));
    }

    @Test
    void postMovie_whenYearNull_returns422WithDetails() throws Exception {
        Movie invalidMovie = new Movie(null, "Фильм без года", null);
        String requestBody = gson.toJson(invalidMovie);

        HttpRequest request = createPostRequest("/movies", requestBody, "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "год должен быть указан"));
    }

    @Test
    void postMovie_whenInvalidContentType_returns415() throws Exception {
        Movie movie = new Movie(null, "Фильм", 2023);
        String requestBody = gson.toJson(movie);

        HttpRequest request = createPostRequest("/movies", requestBody, "text/plain");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(415, response.statusCode(), "Статус код должен быть 415 Unsupported Media Type");
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Неподдерживаемый тип содержимого", errorResponse.getError());
    }

    @Test
    void postMovie_whenEmptyBody_returns422() throws Exception {
        HttpRequest request = createPostRequest("/movies", "", "application/json");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(422, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Ошибка валидации", errorResponse.getError());

        String[] details = errorResponse.getDetails();
        assertTrue(details.length > 0);
        assertTrue(containsMessage(details, "Тело запроса не должно быть пустым"));
    }

    // ==================== GET /movies/{id} ====================

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        Movie movie = createAndAddMovie("Тестовый фильм", 2022);

        HttpRequest request = createGetRequest("/movies/" + movie.getId());
        HttpResponse<String> response = sendRequest(request);

        assertSuccessResponse(response, 200);
        assertContentType(response);

        Movie receivedMovie = gson.fromJson(response.body(), Movie.class);
        assertEquals(movie.getId(), receivedMovie.getId());
        assertEquals(movie.getTitle(), receivedMovie.getTitle());
        assertEquals(movie.getYear(), receivedMovie.getYear());
    }

    @Test
    void getMovieById_whenNotExists_returns404() throws Exception {
        HttpRequest request = createGetRequest("/movies/999");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(404, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", errorResponse.getError());
    }

    // ==================== DELETE /movies/{id} ====================

    @Test
    void deleteMovieById_whenExists_returns204() throws Exception {
        Movie movie = createAndAddMovie("Фильм для удаления", 2022);

        HttpRequest request = createDeleteRequest("/movies/" + movie.getId());
        HttpResponse<String> response = sendRequest(request);

        assertEquals(204, response.statusCode());

        // Проверяем, что фильм действительно удален
        assertNull(store.getMovieById(movie.getId()));
    }

    @Test
    void deleteMovieById_whenNotExists_returns404() throws Exception {
        HttpRequest request = createDeleteRequest("/movies/999");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(404, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Фильм не найден", errorResponse.getError());
    }

    // ==================== GET /movies?year=YYYY ====================

    @Test
    void getMoviesByYear_whenYearExists_returnsMovies() throws Exception {
        createAndAddMovie("Фильм 2020", 2020);
        createAndAddMovie("Фильм 2021", 2021);
        createAndAddMovie("Еще фильм 2020", 2020);

        HttpRequest request = createGetRequest("/movies?year=2020");
        HttpResponse<String> response = sendRequest(request);

        assertSuccessResponse(response, 200);
        assertContentType(response);

        List<Movie> movies = parseMovieList(response.body());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.getYear() == 2020));
    }

    @Test
    void getMoviesByYear_whenYearHasNoMovies_returnsEmptyArray() throws Exception {
        createAndAddMovie("Фильм 2020", 2020);

        HttpRequest request = createGetRequest("/movies?year=2021");
        HttpResponse<String> response = sendRequest(request);

        assertSuccessResponse(response, 200);
        assertContentType(response);

        List<Movie> movies = parseMovieList(response.body());
        assertTrue(movies.isEmpty());
    }

    @Test
    void getMoviesByYear_whenYearParamIsNotNumber_returns400() throws Exception {
        HttpRequest request = createGetRequest("/movies?year=abc");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", errorResponse.getError());
    }

    @Test
    void getMoviesByYear_whenYearParamEmpty_returns400() throws Exception {
        HttpRequest request = createGetRequest("/movies?year=");
        HttpResponse<String> response = sendRequest(request);

        assertEquals(400, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Некорректный параметр запроса — 'year'", errorResponse.getError());
    }

    // ==================== Общие тесты ====================

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> response = sendRequest(request);

        assertEquals(405, response.statusCode());
        assertContentType(response);

        ErrorResponse errorResponse = gson.fromJson(response.body(), ErrorResponse.class);
        assertEquals("Метод не поддерживается", errorResponse.getError());
    }

    // ==================== Вспомогательные методы ====================

    private HttpRequest createGetRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpRequest createPostRequest(String path, String body, String contentType) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpRequest createDeleteRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .DELETE()
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void assertSuccessResponse(HttpResponse<String> response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode(),
                "Статус код должен быть " + expectedStatus);
    }

    private void assertContentType(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType,
                "Content-Type должен быть application/json; charset=UTF-8");
    }

    private List<Movie> parseMovieList(String json) {
        return gson.fromJson(json, new TypeToken<List<Movie>>(){}.getType());
    }

    private Movie createAndAddMovie(String title, int year) {
        Movie movie = new Movie(null, title, year);
        return store.addMovie(movie);
    }

    private boolean containsMessage(String[] details, String message) {
        for (String detail : details) {
            if (detail.contains(message)) {
                return true;
            }
        }
        return false;
    }
}