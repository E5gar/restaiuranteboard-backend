package com.restaiuranteboard.backend.service.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatToolDefinitions {

    private ChatToolDefinitions() {
    }

    public static List<Map<String, Object>> herramientasCliente() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("buscar_productos", "Busca productos por categoria, texto o precio max.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "categoria", prop("string", "Categoria opcional"),
                                "texto", prop("string", "Nombre parcial"),
                                "precio_max", prop("number", "Precio maximo"),
                                "orden_por_precio", prop("string", "asc o desc")
                        )
                )));
        tools.add(fn("modificar_carrito", "Agrega o quita productos del carrito.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "producto_id", prop("string", "ID producto"),
                                "nombre", prop("string", "Nombre si no hay id"),
                                "cantidad", prop("integer", "Unidades 1-10"),
                                "accion", prop("string", "AGREGAR, QUITAR o ELIMINAR_LINEA")
                        ),
                        "required", List.of("accion")
                )));
        tools.add(fn("consultar_estado_pedido", "Estado del pedido activo del cliente.",
                Map.of("type", "object", "properties", Map.of())));
        return tools;
    }

    public static List<Map<String, Object>> herramientasAdmin() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("obtener_kpis_ventas", "KPIs de ventas en rango de fechas ISO.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "fecha_inicio", prop("string", "YYYY-MM-DD"),
                                "fecha_fin", prop("string", "YYYY-MM-DD")
                        )
                )));
        tools.add(fn("obtener_alertas_inventario", "Stock bajo y alertas de inventario.",
                Map.of("type", "object", "properties", Map.of())));
        tools.add(fn("obtener_stock_ingrediente", "Stock de un insumo por id.",
                Map.of(
                        "type", "object",
                        "properties", Map.of("id_insumo", prop("string", "ID numerico insumo")),
                        "required", List.of("id_insumo")
                )));
        tools.add(fn("listar_personal", "Lista cajeros, cocineros o repartidores.",
                Map.of(
                        "type", "object",
                        "properties", Map.of("rol", prop("string", "CAJERO, COCINERO o REPARTIDOR"))
                )));
        tools.add(fn("enviar_email_personal", "Envia correo de admin a empleado.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "email_destino", prop("string", "Email destino"),
                                "nombre_destino", prop("string", "Nombre si no hay email"),
                                "mensaje", prop("string", "Cuerpo del mensaje")
                        ),
                        "required", List.of("mensaje")
                )));
        tools.add(fn("buscar_productos", "Busca productos del catalogo.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "categoria", prop("string", "Categoria"),
                                "texto", prop("string", "Texto"),
                                "precio_max", prop("number", "Precio max"),
                                "orden_por_precio", prop("string", "asc o desc")
                        )
                )));
        return tools;
    }

    private static Map<String, Object> fn(String name, String desc, Map<String, Object> params) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", desc);
        fn.put("parameters", params);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    private static Map<String, String> prop(String type, String desc) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }
}
