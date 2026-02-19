package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public Movie addMovie(Movie movie) {
        int id = idGenerator.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public Movie getMovieById(int id) {
        return movies.get(id);
    }

    public boolean deleteMovieById(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .collect(Collectors.toList());
    }

    public void clear() {
        movies.clear();
        idGenerator.set(1);
    }
}