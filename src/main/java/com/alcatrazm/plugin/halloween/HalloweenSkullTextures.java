package com.alcatrazm.plugin.halloween;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Constantes de texturas Base64 oficiales para las cabezas de calabaza del Evento de Halloween.
 * URLs verificadas en Mojang CDN (HTTPS 200 OK) y compatibles con Paper 1.21.1 y Bedrock (Geyser).
 */
public final class HalloweenSkullTextures {

    private HalloweenSkullTextures() {}

    /**
     * Calabaza Clásica Naranja (MHF_Pumpkin oficial de Mojang).
     * Cuenta oficial de Mojang con UUID nativo reconocido por todos los clientes Java y Geyser Bedrock.
     */
    public static final UUID MHF_PUMPKIN_UUID = UUID.fromString("f44d355b-b6ae-4ba8-8e62-ae6441854785");
    public static final String MHF_PUMPKIN_NAME = "MHF_Pumpkin";

    public static final String CLASSIC_PUMPKIN_URL =
        "https://textures.minecraft.net/texture/b162d72d8d576d9bbb81c9aedc690617416dedc95ccf17f794580ca674942a65";

    public static final String CLASSIC_PUMPKIN =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2IxNjJkNzJkOGQ1NzZkOWJiYjgxYzlhZWRjNjkwNjE3NDE2ZGVkYzk1Y2NmMTdmNzk0NTgwY2E2NzQ5NDJhNjUifX19";

    /**
     * Calabaza Espectral Azul (BluePumpkin de Mojang).
     */
    public static final UUID SPECTRAL_SOUL_UUID = UUID.nameUUIDFromBytes("SpectralSoulPumpkin".getBytes(StandardCharsets.UTF_8));
    public static final String SPECTRAL_SOUL_NAME = "SpectralPumpkin";

    public static final String SPECTRAL_SOUL_URL =
        "https://textures.minecraft.net/texture/30c8745d404de6c2e9650fc9217f17236de50d54ee8e0ae949182f2ae9ead618";

    public static final String SPECTRAL_SOUL =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzMwYzg3NDVkNDA0ZGU2YzJlOTY1MGZjOTIxN2YxNzIzNmRlNTBkNTRlZThlMGFlOTQ5MTgyZjJhZTllYWQ2MTgifX19";
}
