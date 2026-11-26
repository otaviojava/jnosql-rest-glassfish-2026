package com.github.otaviojava.rest;

import com.mongodb.client.MongoClients;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogIT {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final String BASE = "http://localhost:8080/api/products";

    @Test
    void deployedGeneratedApiPersistsMapsPaginatesSearchesAndDeletes() throws Exception {
        String category = "it-" + UUID.randomUUID();
        List<String> ids = new ArrayList<>();
        String mongoUrl = System.getenv().getOrDefault("JNOSQL_MONGODB_URL", "mongodb://localhost:27017");
        String database = System.getenv().getOrDefault("JNOSQL_DOCUMENT_DATABASE", "product_catalog");

        try (var mongo = MongoClients.create(mongoUrl)) {
            var db = mongo.getDatabase(database);
            assertEquals(1.0, db.runCommand(new Document("ping", 1)).getDouble("ok"));
            var products = db.getCollection("Product");
            try {
                for (int index = 0; index < 2; index++) {
                    var created = send("POST", "", """
                            {
                              "name": "Keyboard",
                              "description": "Mechanical",
                              "category": "%s",
                              "price": 89.90,
                              "manufacturer": {"name": "Keyco", "country": "Brazil"},
                              "supplier": {"name": "Acme", "accountManager": "Jane", "contractReference": "C-1001"},
                              "supplierCost": 55.00,
                              "internalNotes": "Internal"
                            }
                            """.formatted(category));
                    assertEquals(201, created.statusCode(), created.body());
                    JsonObject product = object(created.body());
                    String id = product.getString("id");
                    ids.add(id);
                    assertTrue(ObjectId.isValid(id));
                    assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith("/" + id));
                    assertPublicProduct(product);

                    var found = send("GET", "/" + id, null);
                    assertEquals(200, found.statusCode(), found.body());
                    assertEquals(product, object(found.body()));
                    assertPublicProduct(object(found.body()));

                    var stored = products.find(eq("_id", new ObjectId(id))).first();
                    assertNotNull(stored, "Product must actually be persisted in MongoDB");
                    assertEquals("Brazil", stored.get("manufacturer", Document.class).getString("country"));
                    var supplier = stored.get("supplier", Document.class);
                    assertEquals("Acme", supplier.getString("name"));
                    assertEquals("Jane", supplier.getString("accountManager"));
                    assertEquals("C-1001", supplier.getString("contractReference"));
                    assertTrue(stored.containsKey("supplierCost"));
                    assertEquals("Internal", stored.getString("internalNotes"));
                }

                var first = send("GET", "?size=1&offset=0&includeTotal=true", null);
                assertEquals(200, first.statusCode(), first.body());
                JsonObject page = object(first.body());
                assertEquals(1, page.getJsonArray("content").size());
                assertEquals(0, page.getInt("offset"));
                assertEquals(1, page.getInt("size"));
                assertTrue(page.getBoolean("hasNext"));
                assertFalse(page.getBoolean("hasPrevious"));
                assertTrue(page.getJsonNumber("totalElements").longValue() >= 2);
                assertExclusions(page.getJsonArray("content").getJsonObject(0));

                var second = send("GET", "?size=1&offset=1", null);
                assertEquals(200, second.statusCode(), second.body());
                JsonObject next = object(second.body());
                assertEquals(1, next.getInt("offset"));
                assertTrue(next.getBoolean("hasPrevious"));
                assertNotEquals(page.getJsonArray("content").getJsonObject(0).getString("id"),
                        next.getJsonArray("content").getJsonObject(0).getString("id"));
                assertExclusions(next.getJsonArray("content").getJsonObject(0));
                assertFalse(next.containsKey("totalElements"));
                assertEquals(400, send("GET", "?size=0", null).statusCode());

                var search = send("QUERY", "/search", """
                        {"category": {"eq": "%s"}, "price": {"between": [50, 100]}}
                        """.formatted(category));
                assertEquals(200, search.statusCode(), "HTTP QUERY must work: " + search.body());
                try (var reader = Json.createReader(new StringReader(search.body()))) {
                    var results = reader.readArray();
                    assertEquals(2, results.size());
                    for (var result : results) {
                        assertPublicProduct(result.asJsonObject());
                        assertTrue(ids.contains(result.asJsonObject().getString("id")));
                    }
                }
                assertEquals(400, send("QUERY", "/search", "{}").statusCode());

                for (String id : ids) {
                    var deleted = send("DELETE", "/" + id, null);
                    assertEquals(204, deleted.statusCode(), deleted.body());
                    assertEquals(404, send("GET", "/" + id, null).statusCode());
                    assertNull(products.find(eq("_id", new ObjectId(id))).first());
                }
            } finally {
                products.deleteMany(eq("category", category));
            }
        }
    }

    private HttpResponse<String> send(String method, String suffix, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE + suffix))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject object(String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    private static void assertPublicProduct(JsonObject product) {
        assertEquals("Keyboard", product.getString("name"));
        assertEquals("Brazil", product.getJsonObject("manufacturer").getString("country"));
        assertEquals("Keyco", product.getJsonObject("manufacturer").getString("name"));
        assertEquals(0, product.getJsonNumber("price").bigDecimalValue()
                .compareTo(new java.math.BigDecimal("89.90")));
        assertExclusions(product);
    }

    private static void assertExclusions(JsonObject product) {
        assertFalse(product.containsKey("supplier"));
        assertFalse(product.containsKey("supplierCost"));
        assertFalse(product.containsKey("internalNotes"));
    }
}
