package com.alcatrazm.plugin.sonicblade;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.protection.Claim;
import com.alcatrazm.plugin.protection.ClaimFlag;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class SonicBladeListener implements Listener {

    private final AlcatrazPlugin plugin;
    private final SonicBladeManager bladeManager;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public SonicBladeListener(AlcatrazPlugin plugin, SonicBladeManager bladeManager) {
        this.plugin = plugin;
        this.bladeManager = bladeManager;
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.NETHERITE_SWORD) return;
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int targetCMD = plugin.getConfig().getInt("arma.custom-model-data", 12345);
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != targetCMD) return;

        if (!player.hasPermission("sonicblade.use")) {
            player.sendMessage(bladeManager.getMessage("sin-permiso-uso", "&cNo dominas el poder de esta arma."));
            return;
        }

        long cooldownMs = plugin.getConfig().getLong("habilidad.cooldown-segundos", 5) * 1000L;
        UUID playerId = player.getUniqueId();
        if (cooldowns.containsKey(playerId)) {
            long timeElapsed = System.currentTimeMillis() - cooldowns.get(playerId);
            if (timeElapsed < cooldownMs) {
                long secondsLeft = ((cooldownMs - timeElapsed) / 1000) + 1;
                String msgCooldown = plugin.getConfig().getString("mensajes-hojasonica.cooldown", "&c¡Recargando! Espera %tiempo%s.")
                        .replace("%tiempo%", String.valueOf(secondsLeft));
                player.sendMessage(bladeManager.color(plugin.getConfig().getString("mensajes-hojasonica.prefijo", "&8[&bSonicBlade&8] ") + msgCooldown));
                return;
            }
        }
        cooldowns.put(playerId, System.currentTimeMillis());

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        double range = plugin.getConfig().getDouble("habilidad.rango", 25.0);
        double raySize = plugin.getConfig().getDouble("habilidad.radio-hitbox", 3.0);

        // Raytrace blocks first to prevent shooting through walls and bunkers
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(eyeLoc, direction, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = (blockHit != null && blockHit.getHitPosition() != null)
                ? eyeLoc.distance(blockHit.getHitPosition().toLocation(player.getWorld()))
                : range;

        RayTraceResult result = player.getWorld().rayTraceEntities(
                eyeLoc,
                direction,
                effectiveRange,
                raySize,
                entity -> entity instanceof LivingEntity living
                        && !living.equals(player)
                        && !(living instanceof ArmorStand)
                        && !living.hasMetadata("NPC")
                        && !living.isInvulnerable()
        );

        for (double i = 1.0; i <= effectiveRange; i += 2.0) {
            Location particleLoc = eyeLoc.clone().add(direction.clone().multiply(i));
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, particleLoc, 1, 0, 0, 0, 0);
        }

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            boolean respetarProtecciones = plugin.getConfig().getBoolean("habilidad.respetar-protecciones-claims", true);
            if (respetarProtecciones) {
                if (target instanceof Player && !player.getWorld().getPVP()) {
                    player.sendMessage(bladeManager.getMessage("protegido", "&eEl objetivo está protegido en esta zona."));
                    return;
                }

                if (plugin.getProtectionManager() != null) {
                    Claim targetClaim = plugin.getProtectionManager().getClaimAt(target.getLocation());
                    Claim playerClaim = plugin.getProtectionManager().getClaimAt(player.getLocation());

                    if (target instanceof Player) {
                        if ((targetClaim != null && !targetClaim.getFlag(ClaimFlag.PVP)) ||
                            (playerClaim != null && !playerClaim.getFlag(ClaimFlag.PVP))) {
                            player.sendMessage(bladeManager.getMessage("protegido", "&eEl PvP está desactivado en esta zona."));
                            return;
                        }
                    } else if (target instanceof Villager) {
                        if (targetClaim != null && !targetClaim.isTrusted(player.getUniqueId()) && !targetClaim.getFlag(ClaimFlag.VILLAGER_DAMAGE)) {
                            player.sendMessage(bladeManager.getMessage("protegido", "&eEl aldeano está protegido en esta zona."));
                            return;
                        }
                    } else if (!(target instanceof Monster)) {
                        if (targetClaim != null && !targetClaim.isTrusted(player.getUniqueId()) && !targetClaim.getFlag(ClaimFlag.MOB_DAMAGE)) {
                            player.sendMessage(bladeManager.getMessage("protegido", "&eLa criatura pacífica está protegida en esta zona."));
                            return;
                        }
                    }
                }
            }

            double danio = plugin.getConfig().getDouble("habilidad.danio", 15.0);
            double healthBefore = target.getHealth();
            target.setNoDamageTicks(0);
            target.damage(danio, player);

            // Si el daño fue efectivamente aplicado y no cancelado por reclamos/protecciones
            if (target.isDead() || target.getHealth() < healthBefore) {
                int duracionOscuridad = plugin.getConfig().getInt("habilidad.duracion-oscuridad-ticks", 200);
                if (duracionOscuridad > 0 && !target.isDead()) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duracionOscuridad, 0, false, true, true));
                }
            } else if (respetarProtecciones) {
                player.sendMessage(bladeManager.getMessage("protegido", "&eEl objetivo está protegido en esta zona."));
            }
        }
    }
}
