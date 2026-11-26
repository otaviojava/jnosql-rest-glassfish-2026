package com.github.otaviojava.rest;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedApiTest {

    @Test
    void generatedResourceCompilesWithAllFiveHttpMethods() {
        assertEquals("/products", ProductRepositoryResource.class.getAnnotation(Path.class).value());
        Set<String> methods = Arrays.stream(ProductRepositoryResource.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotations()))
                .map(annotation -> annotation.annotationType().getAnnotation(HttpMethod.class))
                .filter(annotation -> annotation != null)
                .map(HttpMethod::value)
                .collect(Collectors.toSet());
        assertEquals(Set.of("POST", "GET", "DELETE", "QUERY"), methods);
        assertEquals(5, Arrays.stream(ProductRepositoryResource.class.getDeclaredMethods())
                .filter(method -> Arrays.stream(method.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(HttpMethod.class)))
                .count());
    }

    @Test
    void generatedMapStructImplementationMapsNestedRecordsAndExcludesPrivateResponseFields() {
        var request = new ProductCreateRequest("Keyboard", "Mechanical", "peripherals",
                new BigDecimal("89.90"), new ManufacturerDTO("Keyco", "Brazil"),
                new SupplierDTO("Acme", "Jane", "C-1001"), new BigDecimal("55.00"), "Internal");
        var mapper = new ProductDTOMapperImpl();

        Product entity = mapper.toEntity(request);
        assertNull(entity.getId());
        assertEquals(new Manufacturer("Keyco", "Brazil"), entity.getManufacturer());
        assertEquals(new Supplier("Acme", "Jane", "C-1001"), entity.getSupplier());
        assertEquals(new BigDecimal("55.00"), entity.getSupplierCost());
        assertEquals("Internal", entity.getInternalNotes());

        ProductDTO response = mapper.toDTO(entity);
        assertEquals("Keyboard", response.name());
        assertEquals(new BigDecimal("89.90"), response.price());
        assertEquals("Brazil", response.manufacturer().country());
        assertEquals(Set.of("id", "name", "description", "category", "price", "manufacturer"),
                components(ProductDTO.class));
        assertEquals(Set.of("name", "description", "category", "price", "manufacturer",
                "supplier", "supplierCost", "internalNotes"), components(ProductCreateRequest.class));
    }

    private static Set<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());
    }
}
