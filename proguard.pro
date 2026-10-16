# ==============================================================================
#                  PROGUARD RULES - ALCATRAZCOREOPTIMIZER
# ==============================================================================

-dontwarn
-dontnote
-ignorewarnings

# Mantener atributos esenciales para anotaciones de Spigot/Paper y reflexión
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations

# 1. MANTENER LA CLASE PRINCIPAL
-keep public class com.alcatrazm.plugin.AlcatrazPlugin {
    public *;
}

# 2. MANTENER COMANDOS Y AUTOCOMPLETADOS
-keep class * implements org.bukkit.command.CommandExecutor {
    public *;
}

-keep class * implements org.bukkit.command.TabCompleter {
    public *;
}

# 3. MANTENER EVENTOS Y LISTENERS
-keep class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler *;
    public *;
}
