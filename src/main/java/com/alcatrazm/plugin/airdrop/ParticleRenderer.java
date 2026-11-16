package com.alcatrazm.plugin.airdrop;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;

/**
 * Renderiza particulas configurables de estela, orbita e impacto para cada AirDrop.
 */
public final class ParticleRenderer {
    private final AirDropModule module;
    private static final DustOptions DUST_GOLD = new DustOptions(Color.fromRGB(255, 211, 105), 0.85F);

    public ParticleRenderer(AirDropModule module) {
        this.module = module;
    }

    public void renderOrbit(Location loc, long tick) {
        if (!this.module.getBoolean("presentation.particles", true) || loc.getWorld() == null) {
            return;
        }
        if (tick % 4L == 0L) {
            double radius = this.module.getDouble("presentation.effect-radius", 1.9, 0.5, 6.0);
            int density = this.module.getInt("presentation.particle-density", 1, 1, 4);
            int points = Math.min(48, 12 * density + 4);
            double angleOffset = tick * 0.045;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2) * i / points + angleOffset;
                spawnDust(loc.clone().add(Math.cos(angle) * radius, 0.35 + Math.sin(angle * 2.0 + angleOffset) * 0.1, Math.sin(angle) * radius));
            }
        }
    }

    private void spawnDust(Location loc) {
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0, DUST_GOLD, true);
        }
    }

    public void renderFallingTrail(Location loc) {
        if (this.module.getBoolean("presentation.particles", true) && loc.getWorld() != null) {
            int density = this.module.getInt("presentation.particle-density", 1, 1, 4);
            loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.0, 0.8, 0.0), 2 + density, 0.2, 0.35, 0.2, 0.02, null, true);
        }
    }

    public void renderImpactBurst(Location loc) {
        if (this.module.getBoolean("presentation.particles", true) && loc.getWorld() != null) {
            double radius = this.module.getDouble("presentation.effect-radius", 1.9, 0.5, 6.0) + 0.5;
            loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0, Color.fromRGB(255, 220, 130), true);
            loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 24, 0.7, 0.5, 0.7, 0.03, null, true);
            for (int i = 0; i < 28; i++) {
                double angle = i * Math.PI * 2.0 / 28.0;
                spawnDust(loc.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius));
            }
        }
    }
}
