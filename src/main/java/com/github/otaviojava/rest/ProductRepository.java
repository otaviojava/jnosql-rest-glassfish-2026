package com.github.otaviojava.rest;

import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Repository;
import jakarta.data.restrict.Restriction;
import jakarta.ws.rs.Path;
import org.eclipse.jnosql.rest.RestDTO;
import org.eclipse.jnosql.rest.RestRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RestRepository(path = "/products")
@RestDTO(excludeFromResponse = {"supplier", "supplierCost", "internalNotes"},
         excludeFromRequest = {"id"})
public interface ProductRepository {

    @Insert
    Product insert(Product product);

    @Find
    Optional<Product> findById(String id);

    @Delete
    @Path("{id}")
    void delete(String id);

    @Find
    @OrderBy("id")
    @Path("")
    Page<Product> findAll(PageRequest request);

    @Find
    @Path("search")
    List<Product> search(Restriction<Product> restriction);
}
