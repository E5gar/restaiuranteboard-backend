package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.repository.sql.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private InventoryRepository inventoryRepository;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    void findAllByIsDeletedFalse_returnsOnlyActiveIngredients() {
        Inventory active = new Inventory();
        active.setName("Tomate");
        active.setStockQuantity(10.0);
        active.setUnit("Gramos");
        active.setCategory("verduras");
        active.setPrice(2.5);
        active.setDeleted(false);
        inventoryRepository.save(active);

        Inventory deleted = new Inventory();
        deleted.setName("Cebolla");
        deleted.setStockQuantity(1.0);
        deleted.setUnit("Unidad");
        deleted.setCategory("verduras");
        deleted.setPrice(1.0);
        deleted.setDeleted(true);
        inventoryRepository.save(deleted);

        List<Inventory> rows = inventoryRepository.findAllByIsDeletedFalse();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("Tomate");
    }

    @Test
    void existsByNameIgnoreCaseAndIsDeletedFalse_detectsDuplicates() {
        Inventory item = new Inventory();
        item.setName("Arroz");
        item.setStockQuantity(5.0);
        item.setUnit("Unidad");
        item.setCategory("abarrotes");
        item.setPrice(4.0);
        item.setDeleted(false);
        inventoryRepository.save(item);

        assertThat(inventoryRepository.existsByNameIgnoreCaseAndIsDeletedFalse("arroz")).isTrue();
        assertThat(inventoryRepository.existsByNameIgnoreCaseAndIsDeletedFalse("frijol")).isFalse();
    }
}
