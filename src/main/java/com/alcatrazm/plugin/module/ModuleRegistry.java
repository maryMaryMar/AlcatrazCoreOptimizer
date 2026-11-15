package com.alcatrazm.plugin.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro central para habilitar, deshabilitar y recargar los módulos de AlcatrazCoreOptimizer
 * sin instanciar listeners ni tareas para módulos desactivados.
 */
public class ModuleRegistry {

    private final Map<String, AlcatrazModule> modules = new LinkedHashMap<>();
    private final Set<String> activeModuleIds = new LinkedHashSet<>();

    public void register(AlcatrazModule module) {
        if (module == null || module.getId() == null) {
            return;
        }
        modules.put(module.getId(), module);
    }

    public boolean isActive(String id) {
        return activeModuleIds.contains(id);
    }

    public AlcatrazModule getModule(String id) {
        return modules.get(id);
    }

    public Collection<AlcatrazModule> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public void enableAll(Logger logger) {
        for (AlcatrazModule module : modules.values()) {
            enableModule(module, logger);
        }
    }

    public void disableAll(Logger logger) {
        List<AlcatrazModule> reverseOrder = new ArrayList<>(modules.values());
        Collections.reverse(reverseOrder);
        for (AlcatrazModule module : reverseOrder) {
            disableModule(module, logger);
        }
    }

    public void reloadAll(Logger logger) {
        for (AlcatrazModule module : modules.values()) {
            disableModule(module, logger);
            enableModule(module, logger);
        }
    }

    private void enableModule(AlcatrazModule module, Logger logger) {
        String id = module.getId();
        if (!module.isEnabled()) {
            if (logger != null) {
                logger.info("  [-] Módulo " + module.getDisplayName() + " [DESACTIVADO en config.yml]");
            }
            return;
        }
        if (activeModuleIds.contains(id)) {
            return;
        }
        try {
            module.enable();
            activeModuleIds.add(id);
            if (logger != null) {
                logger.info("  [+] Módulo " + module.getDisplayName() + " [ACTIVO]");
            }
        } catch (Throwable ex) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Error al iniciar módulo " + module.getDisplayName(), ex);
            }
        }
    }

    private void disableModule(AlcatrazModule module, Logger logger) {
        String id = module.getId();
        if (!activeModuleIds.contains(id)) {
            return;
        }
        try {
            module.disable();
        } catch (Throwable ex) {
            if (logger != null) {
                logger.log(Level.WARNING, "Error al detener módulo " + module.getDisplayName(), ex);
            }
        } finally {
            activeModuleIds.remove(id);
        }
    }
}
