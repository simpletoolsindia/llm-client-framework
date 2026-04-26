package in.simpletools.llm.framework.example;

import com.google.gson.Gson;
import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.client.LLMStatus;
import in.simpletools.llm.framework.tool.LLMTool;
import in.simpletools.llm.framework.tool.ToolParam;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real weather tool demo for agent-style streaming UIs.
 *
 * <p>The demo uses Ollama for the model and Open-Meteo for live weather data.
 * Open-Meteo does not require an API key, so this example works well for quick
 * framework testing.</p>
 */
public class WeatherToolStreamingDemo {
    public static void main(String[] args) {
        String city = args.length > 0 ? String.join(" ", args) : "Jaipur";
        WeatherTools weatherTools = new WeatherTools();

        System.out.println("Starting weather tool streaming demo");
        System.out.println("Model: gemma4:latest");
        System.out.println("City: " + city);
        System.out.println();

        long startedAt = System.nanoTime();
        try (LLMClient client = LLMClient.ollama("gemma4:latest")) {
            client.registerTools(weatherTools);

            client.streamChatWithStatus(
                """
                Use the get_weather tool for city=%s.
                Tell the user the current temperature, wind speed, and weather condition.
                Keep the final answer concise and practical.
                """.formatted(city),
                chunk -> {
                    System.out.print(chunk);
                    System.out.flush();
                },
                status -> printStatus(status, startedAt)
            );
            System.out.println();
        }
    }

    private static void printStatus(LLMStatus status, long startedAt) {
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        switch (status.type()) {
            case CHAT_STARTED -> System.err.printf("[%4d ms] thinking%n", elapsedMs);
            case TOOL_CALL_REQUESTED -> System.err.printf("[%4d ms] tool requested: %s %s%n",
                elapsedMs, status.toolName(), status.arguments());
            case TOOL_EXECUTION_STARTED -> System.err.printf("[%4d ms] running tool: %s%n",
                elapsedMs, status.toolName());
            case TOOL_RESPONSE_VALIDATED -> System.err.printf("[%4d ms] tool result validated: %s%n",
                elapsedMs, status.toolName());
            case CONTINUATION_STARTED -> System.err.printf("[%4d ms] continuing with tool result%n", elapsedMs);
            case CHAT_COMPLETED -> System.err.printf("[%4d ms] completed%n", elapsedMs);
            case ERROR, TOOL_EXECUTION_FAILED -> System.err.printf("[%4d ms] error: %s%n",
                elapsedMs, status.message());
            default -> { }
        }
    }

    public static final class WeatherTools {
        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        private final Gson gson = new Gson();

        @LLMTool(name = "get_weather", description = "Get current live weather for a city using Open-Meteo")
        public Map<String, Object> getWeather(
            @ToolParam(name = "city", description = "City name, for example Jaipur") String city
        ) {
            long startedAt = System.nanoTime();
            try {
                Map<String, Object> place = geocode(city);
                if (place == null) {
                    return failure(city, "Could not find coordinates for city: " + city, startedAt);
                }

                double latitude = ((Number) place.get("latitude")).doubleValue();
                double longitude = ((Number) place.get("longitude")).doubleValue();
                String weatherUrl = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
                    + "&timezone=auto";

                Map<String, Object> weather = getJson(weatherUrl);
                @SuppressWarnings("unchecked")
                Map<String, Object> current = (Map<String, Object>) weather.get("current");
                if (current == null) {
                    return failure(city, "Weather API did not return current weather.", startedAt);
                }

                int weatherCode = ((Number) current.getOrDefault("weather_code", 0)).intValue();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("city", city);
                result.put("matched_location", place.get("name") + ", " + place.getOrDefault("country", ""));
                result.put("latitude", latitude);
                result.put("longitude", longitude);
                result.put("temperature_c", current.get("temperature_2m"));
                result.put("humidity_percent", current.get("relative_humidity_2m"));
                result.put("wind_speed_kmh", current.get("wind_speed_10m"));
                result.put("weather_code", weatherCode);
                result.put("condition", describeWeatherCode(weatherCode));
                result.put("observed_at", current.get("time"));
                result.put("api", "Open-Meteo");
                result.put("elapsed_ms", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
                return result;
            } catch (Exception e) {
                return failure(city, e.getMessage(), startedAt);
            }
        }

        private Map<String, Object> geocode(String city) throws Exception {
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = "https://geocoding-api.open-meteo.com/v1/search"
                + "?name=" + encodedCity
                + "&count=1&language=en&format=json";
            Map<String, Object> json = getJson(url);
            Object results = json.get("results");
            if (!(results instanceof List<?> list) || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> first)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> place = (Map<String, Object>) first;
            return place;
        }

        private Map<String, Object> getJson(String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> json = gson.fromJson(response.body(), Map.class);
            return json;
        }

        private Map<String, Object> failure(String city, String error, long startedAt) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("status", "failed");
            result.put("city", city);
            result.put("error", error);
            result.put("api", "Open-Meteo");
            result.put("elapsed_ms", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            return result;
        }

        private String describeWeatherCode(int code) {
            return switch (code) {
                case 0 -> "Clear sky";
                case 1, 2, 3 -> "Mainly clear to overcast";
                case 45, 48 -> "Fog";
                case 51, 53, 55 -> "Drizzle";
                case 56, 57 -> "Freezing drizzle";
                case 61, 63, 65 -> "Rain";
                case 66, 67 -> "Freezing rain";
                case 71, 73, 75 -> "Snowfall";
                case 77 -> "Snow grains";
                case 80, 81, 82 -> "Rain showers";
                case 85, 86 -> "Snow showers";
                case 95 -> "Thunderstorm";
                case 96, 99 -> "Thunderstorm with hail";
                default -> "Unknown weather condition";
            };
        }
    }
}
