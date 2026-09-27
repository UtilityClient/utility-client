package dev.utilityclient.license;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Stores the buyer's key and decides whether the client may be used.
 *
 * <p>The check is deliberately server side. A key list compiled into the jar would be
 * meaningless, because anyone can unzip the file and delete the branch that reads it, so
 * validity is confirmed against the key API and cached locally only to avoid asking on
 * every launch.
 *
 * <p>There is an offline grace window. If the API cannot be reached the client keeps working
 * for {@link #GRACE_MILLIS} after the last successful check, so a server hiccup or a night
 * without internet never locks a paying customer out of their own game.
 */
public final class LicenseManager {
    public enum Tier {
        NONE("Unlicensed"),
        DAY("1 Day"),
        WEEK("1 Week"),
        MONTH("1 Month"),
        PERMANENT("Lifetime");

        private final String displayName;

        Tier(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** Snapshot of the current licence, safe to read from the render thread. */
    public record Status(boolean valid, Tier tier, long expiresAt, boolean checking, String detail) {
        public static final Status NONE =
                new Status(false, Tier.NONE, 0L, false, "No key entered");

        public boolean expired() {
            return !valid && tier != Tier.NONE && expiresAt > 0L && System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Base URL of the key API. Point this at your Cloudflare Worker.
     * It can also be overridden without rebuilding using the
     * {@code -Dutilityclient.api=https://your-worker.workers.dev} JVM argument.
     */
    public static final String DEFAULT_ENDPOINT = "https://utilityclient-keys.utilityclient.workers.dev";

    /** How long a successful check is trusted before asking the API again. */
    private static final long CACHE_MILLIS = Duration.ofHours(6).toMillis();

    /** How long the client keeps working after the last successful check, if the API is down. */
    private static final long GRACE_MILLIS = Duration.ofHours(72).toMillis();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Object LOCK = new Object();
    private static Path path;

    private static String key = "";
    private static Tier tier = Tier.NONE;
    private static long expiresAt;
    private static long lastVerified;
    private static boolean checking;
    private static String detail = "No key entered";

    private LicenseManager() {
    }

    /* ------------------------------------------------------------------ state */

    public static String endpoint() {
        String override = System.getProperty("utilityclient.api");
        if (override != null && !override.isBlank()) {
            return trimSlash(override.trim());
        }
        return trimSlash(DEFAULT_ENDPOINT);
    }

    public static boolean endpointConfigured() {
        return !endpoint().contains("YOUR-SUBDOMAIN");
    }

    public static String key() {
        synchronized (LOCK) {
            return key;
        }
    }

    /** Last four characters only, so the full key is never drawn on screen. */
    public static String maskedKey() {
        synchronized (LOCK) {
            if (key.isEmpty()) {
                return "----";
            }
            if (key.length() <= 4) {
                return key;
            }
            return "..." + key.substring(key.length() - 4);
        }
    }

    public static Status status() {
        synchronized (LOCK) {
            boolean valid = isValidLocked();
            return new Status(valid, tier, expiresAt, checking, detail);
        }
    }

    /** Convenience for render code that only needs a yes or no. */
    public static boolean isLicensed() {
        return status().valid();
    }

    private static boolean isValidLocked() {
        if (tier == Tier.NONE || key.isEmpty()) {
            return false;
        }
        return tier == Tier.PERMANENT || System.currentTimeMillis() < expiresAt;
    }

    /* ------------------------------------------------------------------ load/save */

    public static void load() {
        synchronized (LOCK) {
            path = FabricLoader.getInstance().getConfigDir().resolve("utility-client-license.json");
            if (!Files.exists(path)) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                key = optString(json, "key", "");
                tier = parseTier(optString(json, "tier", "NONE"));
                expiresAt = optLong(json, "expiresAt", 0L);
                lastVerified = optLong(json, "lastVerified", 0L);
                detail = optString(json, "detail", tier == Tier.NONE ? "No key entered" : "Loaded from disk");
            } catch (IOException | RuntimeException exception) {
                System.err.println("[Utility Client] Could not read licence file: " + exception.getMessage());
            }
        }
    }

    private static void saveLocked() {
        if (path == null) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("tier", tier.name());
        json.addProperty("expiresAt", expiresAt);
        json.addProperty("lastVerified", lastVerified);
        json.addProperty("detail", detail);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException exception) {
            System.err.println("[Utility Client] Could not write licence file: " + exception.getMessage());
        }
    }

    /* ------------------------------------------------------------------ actions */

    /** Stores a key and starts a check. Returns false if the input is obviously unusable. */
    public static boolean submit(String candidate) {
        String cleaned = candidate == null ? "" : candidate.trim();
        if (cleaned.isEmpty()) {
            synchronized (LOCK) {
                detail = "Enter a key";
            }
            return false;
        }
        synchronized (LOCK) {
            key = cleaned;
            tier = Tier.NONE;
            expiresAt = 0L;
            lastVerified = 0L;
            detail = "Checking...";
        }
        validateNow();
        return true;
    }

    public static void clear() {
        synchronized (LOCK) {
            key = "";
            tier = Tier.NONE;
            expiresAt = 0L;
            lastVerified = 0L;
            detail = "No key entered";
            saveLocked();
        }
    }

    /** Re-checks the stored key, ignoring the cache. Safe to call from a background thread. */
    public static void validateNow() {
        String currentKey;
        synchronized (LOCK) {
            if (key.isEmpty()) {
                return;
            }
            if (checking) {
                return;
            }
            checking = true;
            detail = "Checking...";
            currentKey = key;
        }

        String url = endpoint() + "/validate?key=" + encode(currentKey);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        CompletableFuture
                .supplyAsync(() -> probe(request))
                .thenAccept(result -> applyResult(result, currentKey))
                .exceptionally(throwable -> {
                    fail("Could not reach the key server", currentKey);
                    return null;
                });
    }

    /** Called every client tick. Refreshes the cache when it has gone stale. */
    public static void tick() {
        Status current = status();
        if (!current.checking() && !key().isEmpty() && isStale(current)) {
            validateNow();
        }
    }

    private static boolean isStale(Status current) {
        boolean timeLeft = current.tier() != Tier.PERMANENT && current.expiresAt() > 0L
                && System.currentTimeMillis() < current.expiresAt();
        if (timeLeft && System.currentTimeMillis() - lastVerified < CACHE_MILLIS) {
            return false;
        }
        return System.currentTimeMillis() - lastVerified >= CACHE_MILLIS;
    }

    private record Result(boolean ok, boolean valid, Tier tier, long expiresAt, String message) {
    }

    private static Result probe(HttpRequest request) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new Result(false, false, Tier.NONE, 0L, "Key server replied " + response.statusCode());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean valid = optBoolean(json, "valid", false);
            Tier parsedTier = parseTier(optString(json, "tier", "NONE"));
            long expiry = optLong(json, "expiresAt", 0L);
            String message = optString(json, "message", valid ? "Licensed" : "Key not recognised");
            return new Result(true, valid, valid ? parsedTier : Tier.NONE, expiry, message);
        } catch (IOException exception) {
            return new Result(false, false, Tier.NONE, 0L, "Offline - could not reach key server");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(false, false, Tier.NONE, 0L, "Check interrupted");
        } catch (RuntimeException exception) {
            return new Result(false, false, Tier.NONE, 0L, "Unexpected response from key server");
        }
    }

    private static void applyResult(Result result, String requestedKey) {
        synchronized (LOCK) {
            checking = false;

            // The user may have pasted a different key while this request was in flight.
            if (!requestedKey.equals(key)) {
                return;
            }

            if (result.ok()) {
                tier = result.tier();
                expiresAt = result.expiresAt();
                detail = result.message();
                if (result.valid()) {
                    lastVerified = System.currentTimeMillis();
                }
            } else if (withinGrace()) {
                // Keep the cached tier so the client still runs, but say what happened.
                detail = "Offline - running on saved licence";
            } else {
                tier = Tier.NONE;
                expiresAt = 0L;
                detail = result.message();
            }
            saveLocked();
        }
    }

    private static void fail(String message, String requestedKey) {
        applyResult(new Result(false, false, Tier.NONE, 0L, message), requestedKey);
    }

    private static boolean withinGrace() {
        return isValidLocked() && lastVerified > 0L
                && System.currentTimeMillis() - lastVerified < GRACE_MILLIS;
    }

    /* ------------------------------------------------------------------ helpers */

    public static long remainingMillis() {
        Status current = status();
        if (current.tier() == Tier.PERMANENT || current.expiresAt() <= 0L) {
            return 0L;
        }
        return Math.max(0L, current.expiresAt() - System.currentTimeMillis());
    }

    public static String remainingLabel() {
        Status current = status();
        if (current.tier() == Tier.PERMANENT) {
            return "Never expires";
        }
        long millis = remainingMillis();
        if (millis <= 0L) {
            return "Expired";
        }
        long days = millis / 86_400_000L;
        if (days >= 1L) {
            return days + (days == 1L ? " day left" : " days left");
        }
        long hours = millis / 3_600_000L;
        if (hours >= 1L) {
            return hours + (hours == 1L ? " hour left" : " hours left");
        }
        return Math.max(1L, millis / 60_000L) + " min left";
    }

    public static String expiryLabel() {
        Status current = status();
        if (current.expiresAt() <= 0L) {
            return "-";
        }
        return Instant.ofEpochMilli(current.expiresAt()).toString().replace("T", " ").substring(0, 16);
    }

    private static Tier parseTier(String raw) {
        if (raw == null) {
            return Tier.NONE;
        }
        String cleaned = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (cleaned.equals("PERM") || cleaned.equals("PERMANENT") || cleaned.equals("LIFETIME")) {
            return Tier.PERMANENT;
        }
        for (Tier candidate : Tier.values()) {
            if (candidate.name().equals(cleaned)) {
                return candidate;
            }
        }
        return Tier.NONE;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String optString(JsonObject json, String name, String fallback) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsString() : fallback;
    }

    private static long optLong(JsonObject json, String name, long fallback) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsLong() : fallback;
    }

    private static boolean optBoolean(JsonObject json, String name, boolean fallback) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? json.get(name).getAsBoolean() : fallback;
    }
}
