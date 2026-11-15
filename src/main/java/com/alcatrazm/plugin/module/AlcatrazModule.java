package com.alcatrazm.plugin.module;

/**
 * Contrato común del ciclo de vida para los módulos internos de AlcatrazCoreOptimizer.
 */
public interface AlcatrazModule {

    /**
     * Identificador técnico único en minúsculas (por ejemplo, "autorestart", "resource-pack").
     */
    String getId();

    /**
     * Nombre legible del módulo para logs y listados de estado.
     */
    String getDisplayName();

    /**
     * Indica si el módulo debe estar activo según config.yml.
     */
    boolean isEnabled();

    /**
     * Inicializa listeners, comandos o tareas del módulo. Solo se invoca si isEnabled() es true.
     */
    void enable();

    /**
     * Detiene tareas, desregistra listeners/hooks y libera recursos del módulo.
     */
    void disable();

    /**
     * Recarga el módulo de forma limpia reaplicando su estado habilitado/deshabilitado.
     */
    default void reload() {
        disable();
        if (isEnabled()) {
            enable();
        }
    }
}
