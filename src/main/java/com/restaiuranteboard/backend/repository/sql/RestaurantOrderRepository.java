package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {

    List<RestaurantOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<RestaurantOrder> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<RestaurantOrder> findByStatusInAndDeliveryPerson_IdOrderByCreatedAtAsc(Collection<String> statuses, UUID deliveryPersonId);

    List<RestaurantOrder> findByClient_IdAndStatusNotInOrderByCreatedAtDesc(UUID clientId, Collection<String> statuses);

    List<RestaurantOrder> findByClient_IdOrderByCreatedAtDesc(UUID clientId);

    @Query("SELECT DISTINCT o FROM RestaurantOrder o LEFT JOIN FETCH o.deliveryPerson WHERE o.client.id = :clientId AND o.status NOT IN :excluded ORDER BY o.createdAt DESC")
    List<RestaurantOrder> loadSeguimientoPendientes(
            @Param("clientId") UUID clientId,
            @Param("excluded") Collection<String> excluded);

    @Query("SELECT DISTINCT o FROM RestaurantOrder o LEFT JOIN FETCH o.deliveryPerson WHERE o.client.id = :clientId AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<RestaurantOrder> loadSeguimientoFinalizados(
            @Param("clientId") UUID clientId,
            @Param("statuses") Collection<String> statuses);
}
