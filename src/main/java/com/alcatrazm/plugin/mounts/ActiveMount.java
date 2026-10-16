package com.alcatrazm.plugin.mounts;

import com.alcatrazm.plugin.AlcatrazPlugin;
import com.alcatrazm.plugin.protection.Claim;
import com.alcatrazm.plugin.protection.ClaimFlag;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

public class ActiveMount {

    public static final String METADATA_TAG = "alcatraz_mount";

    private final AlcatrazPlugin plugin;
    private final Player player;
    private final UUID ownerId;
    private final MountType type;
    private double scale;
    private double speed;
    private double jumpVelocity;
    private double stepHeight;
    private int mobSize;

    // Configuración de combate y ataque de guardián
    private boolean attackEnabled;
    private double attackDamage;
    private double attackRange;
    private long attackCooldownMs;
    private boolean targetMonsters;
    private boolean targetRivalPlayers;

    // Configuración de distancias de seguimiento
    private double followStartDistance;
    private double teleportDistance;

    private LivingEntity entity;
    private final NamespacedKey mountKey;
    private final NamespacedKey ownerKey;

    // Vectores para interpolación suave de velocidad
    private final Vector currentVelocity = new Vector(0, 0, 0);
    private long lastAttackTime = 0;

    // Objetivo de represalia (para el MiniDragón del End)
    private UUID retaliationTargetId = null;
    private long retaliationTargetTime = 0;

    // Ángulo de órbita para el Allay
    private double allayAngle = 0.0;
    private int pathfindCooldown = 0;

    public ActiveMount(AlcatrazPlugin plugin, Player player, MountType type, double scale, double speed, double jumpVelocity) {
        this.plugin = plugin;
        this.player = player;
        this.ownerId = player.getUniqueId();
        this.type = type;
        this.mountKey = new NamespacedKey(plugin, "alcatraz_mount");
        this.ownerKey = new NamespacedKey(plugin, "alcatraz_mount_owner");
        loadConfigValues();
    }

    public void loadConfigValues() {
        String path = "mounts.types." + type.getId() + ".";
        this.scale = plugin.getConfig().getDouble(path + "scale", type.getDefaultScale());
        this.speed = plugin.getConfig().getDouble(path + "speed", type.getDefaultSpeed());
        this.jumpVelocity = plugin.getConfig().getDouble(path + "jump-velocity", type.getDefaultJump());
        this.stepHeight = plugin.getConfig().getDouble(path + "step-height", 1.25);
        this.mobSize = plugin.getConfig().getInt(path + "mob-size", type == MountType.PHANTOM ? 2 : 1);

        boolean defaultAttack = type == MountType.PHANTOM || type == MountType.MINIDRAGON;
        this.attackEnabled = plugin.getConfig().getBoolean(path + "attack.enabled", defaultAttack);
        this.attackDamage = plugin.getConfig().getDouble(path + "attack.damage", type == MountType.MINIDRAGON ? 14.0 : 9.0);
        this.attackRange = plugin.getConfig().getDouble(path + "attack.range", type == MountType.MINIDRAGON ? 20.0 : 14.0);
        this.attackCooldownMs = (long) (plugin.getConfig().getDouble(path + "attack.cooldown-seconds", 1.0) * 1000L);
        this.targetMonsters = plugin.getConfig().getBoolean(path + "attack.target-monsters", !type.isRetaliateOnly());
        this.targetRivalPlayers = plugin.getConfig().getBoolean(path + "attack.target-rival-players", true);

        this.followStartDistance = plugin.getConfig().getDouble("mounts.follow-start-distance", 5.0);
        this.teleportDistance = plugin.getConfig().getDouble("mounts.teleport-distance", 28.0);

        if (entity != null && entity.isValid()) {
            setAttribute(entity, Attribute.GENERIC_SCALE, scale);
            if (!type.isFlying() && !type.isAquatic()) {
                setAttribute(entity, Attribute.GENERIC_STEP_HEIGHT, stepHeight);
                setAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, speed);
            } else if (type.isFlying()) {
                setAttribute(entity, Attribute.GENERIC_FLYING_SPEED, speed);
            }
            if (entity instanceof Phantom phantom) {
                phantom.setSize(mobSize);
            }
        }
    }

    public void setRetaliationTarget(Player target) {
        if (target == null || target.getUniqueId().equals(ownerId)) return;
        this.retaliationTargetId = target.getUniqueId();
        this.retaliationTargetTime = System.currentTimeMillis();
    }

    public boolean isAttackAllowedAt(Location loc, boolean isPvP, boolean isAnimal) {
        if (loc == null || plugin == null || plugin.getProtectionManager() == null || !plugin.getProtectionManager().isEnabled()) {
            return true;
        }
        Claim claim = plugin.getProtectionManager().getClaimAt(loc);
        if (claim != null) {
            if (isPvP && !claim.getFlag(ClaimFlag.PVP)) {
                return false;
            }
            if (isAnimal && !claim.getFlag(ClaimFlag.MOB_DAMAGE)) {
                return false;
            }
        }
        if (entity != null && entity.isValid()) {
            Claim mountClaim = plugin.getProtectionManager().getClaimAt(entity.getLocation());
            if (mountClaim != null) {
                if (isPvP && !mountClaim.getFlag(ClaimFlag.PVP)) {
                    return false;
                }
                if (isAnimal && !mountClaim.getFlag(ClaimFlag.MOB_DAMAGE)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void spawn() {
        Location spawnLoc = player.getLocation();
        LivingEntity spawned = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, type.getEntityType());
        this.entity = spawned;

        spawned.setMetadata(METADATA_TAG, new FixedMetadataValue(plugin, true));
        spawned.getPersistentDataContainer().set(mountKey, PersistentDataType.STRING, type.getId());
        spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerId.toString());

        setAttribute(spawned, Attribute.GENERIC_SCALE, scale);
        setAttribute(spawned, Attribute.GENERIC_MAX_HEALTH, 100.0);
        setAttribute(spawned, Attribute.GENERIC_SAFE_FALL_DISTANCE, 200.0);
        setAttribute(spawned, Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER, 0.0);

        if (!type.isFlying() && !type.isAquatic()) {
            setAttribute(spawned, Attribute.GENERIC_STEP_HEIGHT, stepHeight);
            setAttribute(spawned, Attribute.GENERIC_MOVEMENT_SPEED, speed);
        } else if (type.isFlying()) {
            setAttribute(spawned, Attribute.GENERIC_FLYING_SPEED, speed);
            spawned.setGravity(false);
        }

        spawned.setHealth(100.0);
        spawned.setInvulnerable(true);
        spawned.setCollidable(false);
        spawned.setPersistent(false);
        spawned.setRemoveWhenFarAway(false);

        String colorTitle = switch (type) {
            case FOX -> "&6&lZorro Ancestral de &e" + player.getName();
            case RABBIT -> "&e&lConejo Colosal de &a" + player.getName();
            case ARMADILLO -> "&6&lArmadillo Veloz de &e" + player.getName();
            case CAT -> "&8&lGato Pantera de &7" + player.getName();
            case POLAR_BEAR -> "&b&lOso Polar Guerrero de &f" + player.getName();
            case PANDA -> "&f&lPanda Guardián de &e" + player.getName();
            case SNIFFER -> "&2&lSniffer Arcaico de &a" + player.getName();
            case PHANTOM -> "&5&lPhantom del Abismo de &d" + player.getName();
            case MINIDRAGON -> "&d&lMiniDragón del End de &5" + player.getName();
            case BAT -> "&7&lMurciélago Espectral de &8" + player.getName();
            case BEE -> "&e&lAbeja Reina Melífera de &6" + player.getName();
            case SALMON -> "&c&lSalmón Titán de &f" + player.getName();
            case DOLPHIN -> "&b&lDelfín Oceánico de &3" + player.getName();
            case AXOLOTL -> "&d&lAjolote Místico de &f" + player.getName();
            case ALLAY -> "&b&lHada Protectora de &f" + player.getName();
        };
        spawned.setCustomName(ChatColor.translateAlternateColorCodes('&', colorTitle));
        spawned.setCustomNameVisible(true);

        if (spawned instanceof Fox fox) {
            fox.setAdult();
            fox.setFirstTrustedPlayer(player);
            fox.setSitting(false);
            fox.setSleeping(false);
            fox.setCrouching(false);
        } else if (spawned instanceof Rabbit rabbit) {
            rabbit.setAdult();
            rabbit.setRabbitType(Rabbit.Type.GOLD);
        } else if (spawned instanceof Armadillo armadillo) {
            armadillo.setAdult();
        } else if (spawned instanceof Cat cat) {
            cat.setAdult();
            cat.setTamed(true);
            cat.setOwner(player);
            cat.setSitting(false);
            cat.setCatType(Cat.Type.ALL_BLACK);
        } else if (spawned instanceof PolarBear polarBear) {
            polarBear.setAdult();
        } else if (spawned instanceof Panda panda) {
            panda.setAdult();
            panda.setSitting(false);
            panda.setMainGene(Panda.Gene.NORMAL);
            panda.setHiddenGene(Panda.Gene.NORMAL);
        } else if (spawned instanceof Sniffer sniffer) {
            sniffer.setAdult();
            setAttribute(spawned, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
        } else if (spawned instanceof Phantom phantom) {
            phantom.setSize(mobSize);
            phantom.setFireTicks(0);
            phantom.setVisualFire(false);
            phantom.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        } else if (spawned instanceof EnderDragon dragon) {
            dragon.setPhase(EnderDragon.Phase.HOVER);
            dragon.setSilent(true);
            if (dragon.getBossBar() != null) {
                dragon.getBossBar().setVisible(false);
            }
        } else if (spawned instanceof Bat bat) {
            bat.setAwake(true);
        } else if (spawned instanceof Bee bee) {
            bee.setAdult();
            bee.setHasStung(false);
            bee.setAnger(0);
            bee.setHasNectar(true);
            bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
        } else if (spawned instanceof Dolphin dolphin) {
            // Configuración adicional de delfín si es necesaria
        } else if (spawned instanceof Axolotl axolotl) {
            axolotl.setAdult();
            axolotl.setVariant(Axolotl.Variant.LUCY);
            setAttribute(spawned, Attribute.GENERIC_STEP_HEIGHT, stepHeight);
        } else if (spawned instanceof Allay allay) {
            allay.setCanDuplicate(false);
            spawned.setGravity(false);
        }

        if (spawned instanceof Mob mob) {
            try {
                org.bukkit.Bukkit.getMobGoals().removeAllGoals(mob);
            } catch (Throwable ignored) {}
        }

        if (type.isRideable()) {
            spawned.addPassenger(player);
        }

        spawnLoc.getWorld().spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.1);
    }

    private void setAttribute(LivingEntity living, Attribute attribute, double value) {
        if (living == null || attribute == null) return;
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public void tick(PlayerInputHelper.InputResult input) {
        if (entity == null || !entity.isValid() || player == null || !player.isOnline()) {
            return;
        }

        if (type == MountType.ALLAY) {
            handleAllayFollowing();
            return;
        }

        boolean isPassenger = entity.getPassengers().contains(player);

        if (entity instanceof Mob mob) {
            if (type.isFlying()) {
                if (mob.isAware()) {
                    mob.setAware(false);
                }
            } else {
                if (isPassenger) {
                    if (mob.isAware()) {
                        mob.setAware(false);
                    }
                } else {
                    if (!mob.isAware()) {
                        mob.setAware(true);
                    }
                }
            }
        }

        if (isPassenger) {
            if (type.isAquatic()) {
                Block blockAtEntity = entity.getLocation().getBlock();
                Block blockAtEye = player.getEyeLocation().getBlock();
                boolean inWater = blockAtEntity.isLiquid() || blockAtEye.isLiquid();
                if (type == MountType.AXOLOTL && !inWater) {
                    handleGroundMovement(input, player.getLocation().getDirection());
                } else {
                    handleAquaticMovement(input);
                }
            } else {
                handleMountedMovement(input);
            }
        } else {
            handleFollowing();
        }
    }

    private void handleAllayFollowing() {
        Location pLoc = player.getLocation();
        Location aLoc = entity.getLocation();

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 0, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 0, true, false, true));

        double distSq = pLoc.distanceSquared(aLoc);
        if (distSq > teleportDistance * teleportDistance) {
            entity.teleport(pLoc.clone().add(0, 1.5, 0));
            return;
        }

        allayAngle += 0.08;
        if (allayAngle > Math.PI * 2) allayAngle -= Math.PI * 2;

        double orbitRadius = 1.35;
        double targetX = pLoc.getX() + Math.cos(allayAngle) * orbitRadius;
        double targetZ = pLoc.getZ() + Math.sin(allayAngle) * orbitRadius;
        double targetY = pLoc.getY() + 1.6 + Math.sin(allayAngle * 2.0) * 0.25;

        Location targetLoc = new Location(pLoc.getWorld(), targetX, targetY, targetZ, pLoc.getYaw(), 0);
        Vector diff = targetLoc.toVector().subtract(aLoc.toVector());

        if (diff.lengthSquared() > 0.01) {
            entity.setVelocity(diff.multiply(0.35));
        } else {
            entity.setVelocity(new Vector(0, 0, 0));
        }
        Vector toPlayer = pLoc.toVector().subtract(aLoc.toVector());
        float lookAtPlayerYaw = (toPlayer.lengthSquared() > 0.0001) ?
                (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ())) : pLoc.getYaw();
        entity.setRotation(lookAtPlayerYaw, 0);
        entity.setBodyYaw(lookAtPlayerYaw);

        if (Math.random() < 0.25) {
            spawnParticleSafe(aLoc.clone().add(0, 0.3, 0), Particle.TOTEM_OF_UNDYING, 2, 0.1, 0.1, 0.1, 0.02);
            spawnParticleSafe(aLoc.clone().add(0, 0.3, 0), Particle.WAX_ON, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void handleAquaticMovement(PlayerInputHelper.InputResult input) {
        float playerYaw = player.getLocation().getYaw();
        float playerPitch = player.getLocation().getPitch();
        entity.setRotation(playerYaw, playerPitch * 0.5f);
        entity.setBodyYaw(playerYaw);

        Block blockAtEntity = entity.getLocation().getBlock();
        Block blockAtEye = player.getEyeLocation().getBlock();
        boolean inWater = blockAtEntity.isLiquid() || blockAtEye.isLiquid();

        if (type == MountType.DOLPHIN && !inWater) {
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&c¡El delfín solo puede nadar dentro del agua!"));
            entity.setVelocity(new Vector(0, -0.2, 0));
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 50, 0, true, false, false));
        if (type == MountType.DOLPHIN) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 50, 0, true, false, false));
        } else if (type == MountType.AXOLOTL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 50, 0, true, false, false));
        }

        Vector lookDir = player.getLocation().getDirection();
        Vector desired = new Vector(0, 0, 0);

        double rad = Math.toRadians(player.getLocation().getYaw());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        Vector forwardDir = lookDir.clone().normalize();
        Vector strafeDir = new Vector(-cos, 0, -sin).normalize();

        if (input.forward()) desired.add(forwardDir);
        if (input.backward()) desired.subtract(forwardDir);
        if (input.left()) desired.subtract(strafeDir);
        if (input.right()) desired.add(strafeDir);

        if (input.jump()) desired.setY(desired.getY() + 0.60);
        if (input.shift()) desired.setY(desired.getY() - 0.60);

        if (desired.lengthSquared() > 0.001) {
            desired.normalize().multiply(speed);

            currentVelocity.setX(currentVelocity.getX() * 0.25 + desired.getX() * 0.75);
            currentVelocity.setY(currentVelocity.getY() * 0.25 + desired.getY() * 0.75);
            currentVelocity.setZ(currentVelocity.getZ() * 0.25 + desired.getZ() * 0.75);

            entity.setVelocity(currentVelocity);

            Location loc = entity.getLocation().add(0, 0.4, 0);
            if (inWater) {
                spawnParticleSafe(loc, Particle.BUBBLE_COLUMN_UP, 3, 0.2, 0.2, 0.2, 0.05);
                if (type == MountType.DOLPHIN && Math.random() < 0.15) {
                    spawnParticleSafe(loc, Particle.DOLPHIN, 1, 0.1, 0.1, 0.1, 0.02);
                } else if (type == MountType.AXOLOTL && Math.random() < 0.20) {
                    spawnParticleSafe(loc, Particle.HEART, 1, 0.15, 0.15, 0.15, 0);
                }
            }
        } else {
            currentVelocity.multiply(0.75);
            if (currentVelocity.lengthSquared() < 0.005) {
                currentVelocity.zero();
            }
            entity.setVelocity(currentVelocity);
        }
    }

    private void handleMountedMovement(PlayerInputHelper.InputResult input) {
        float playerYaw = player.getLocation().getYaw();
        float playerPitch = player.getLocation().getPitch();

        float targetYaw = (type == MountType.MINIDRAGON) ? ((playerYaw + 180.0f) % 360.0f) : playerYaw;
        float targetPitch = (type == MountType.MINIDRAGON) ? -playerPitch : (type.isFlying() ? playerPitch : 0);

        entity.setRotation(targetYaw, targetPitch);
        entity.setBodyYaw(targetYaw);

        Vector lookDir = player.getLocation().getDirection();

        if (type.isFlying()) {
            handleFlightMovement(input, lookDir);
        } else {
            handleGroundMovement(input, lookDir);
        }
    }

    private void handleFlightMovement(PlayerInputHelper.InputResult input, Vector lookDir) {
        Vector desired = new Vector(0, 0, 0);

        double rad = Math.toRadians(player.getLocation().getYaw());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        Vector forwardDir = lookDir.clone().normalize();
        Vector strafeDir = new Vector(-cos, 0, -sin).normalize();

        if (input.forward()) desired.add(forwardDir);
        if (input.backward()) desired.subtract(forwardDir);
        if (input.left()) desired.subtract(strafeDir);
        if (input.right()) desired.add(strafeDir);

        if (input.jump()) desired.setY(desired.getY() + 0.65);
        if (input.shift()) desired.setY(desired.getY() - 0.65);

        if (desired.lengthSquared() > 0.001) {
            desired.normalize().multiply(speed);

            currentVelocity.setX(currentVelocity.getX() * 0.20 + desired.getX() * 0.80);
            currentVelocity.setY(currentVelocity.getY() * 0.20 + desired.getY() * 0.80);
            currentVelocity.setZ(currentVelocity.getZ() * 0.20 + desired.getZ() * 0.80);
        } else {
            currentVelocity.multiply(0.70);
            if (currentVelocity.lengthSquared() < 0.005) {
                currentVelocity.zero();
            }
        }

        entity.setGravity(false);

        if (type == MountType.MINIDRAGON) {
            float playerYaw = player.getLocation().getYaw();
            float playerPitch = player.getLocation().getPitch();
            float dragonYaw = (playerYaw + 180.0f) % 360.0f;
            float dragonPitch = -playerPitch;

            Location curLoc = entity.getLocation();
            Location nextLoc = curLoc.clone().add(currentVelocity);
            nextLoc.setYaw(dragonYaw);
            nextLoc.setPitch(dragonPitch);

            boolean canMove = true;
            if (currentVelocity.lengthSquared() > 0.001) {
                Block blockAtNext = nextLoc.getBlock();
                if (blockAtNext.getType().isSolid()) {
                    canMove = false;
                    currentVelocity.zero();
                }
            }

            if (canMove && (currentVelocity.lengthSquared() > 0.0001 || Math.abs(curLoc.getYaw() - dragonYaw) > 1.0f || Math.abs(curLoc.getPitch() - dragonPitch) > 1.0f)) {
                entity.teleport(nextLoc, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }

            Location loc = entity.getLocation().add(0, 0.3, 0);
            spawnParticleSafe(loc, Particle.DRAGON_BREATH, 2, 0.15, 0.1, 0.15, 0.02);
            spawnParticleSafe(loc, Particle.PORTAL, 2, 0.2, 0.1, 0.2, 0.02);
        } else {
            entity.setVelocity(currentVelocity);

            Location loc = entity.getLocation().add(0, 0.3, 0);
            if (type == MountType.BEE) {
                // Control suave y dócil (como un caballo volador)
                if (Math.random() < 0.18) {
                    spawnParticleSafe(loc, Particle.HEART, 1, 0.15, 0.15, 0.15, 0);
                }
                if (Math.random() < 0.15) {
                    spawnParticleSafe(loc, Particle.FALLING_NECTAR, 1, 0.1, 0.1, 0.1, 0.01);
                }

                // Utilidad 1: Neutralizar veneno y otorgar alivio / revitalización
                if (player.hasPotionEffect(PotionEffectType.POISON)) {
                    player.removePotionEffect(PotionEffectType.POISON);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.7f, 1.4f);
                    player.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&6🍯 ¡El néctar dulce de tu abeja neutralizó el veneno!"));
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false, false));

                // Utilidad 2: Polinización de cultivos al sobrevolarlos
                if (Math.random() < 0.10) {
                    Block groundBlock = entity.getLocation().subtract(0, 1.0, 0).getBlock();
                    if (groundBlock.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                        if (ageable.getAge() < ageable.getMaximumAge()) {
                            ageable.setAge(ageable.getAge() + 1);
                            groundBlock.setBlockData(ageable);
                            spawnParticleSafe(groundBlock.getLocation().add(0.5, 0.6, 0.5), Particle.HAPPY_VILLAGER, 3, 0.2, 0.1, 0.2, 0);
                        }
                    }
                }
            } else if (type == MountType.BAT) {
                if (Math.random() < 0.20) {
                    spawnParticleSafe(loc, Particle.SMOKE, 2, 0.1, 0.1, 0.1, 0.01);
                }
            } else {
                spawnParticleSafe(loc, Particle.FIREWORK, 2, 0.15, 0.1, 0.15, 0.04);
                spawnParticleSafe(loc, Particle.PORTAL, 3, 0.2, 0.1, 0.2, 0.02);
            }
        }

        if (entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
    }

    private void handleGroundMovement(PlayerInputHelper.InputResult input, Vector lookDir) {
        Vector flatDir = lookDir.clone().setY(0);
        if (flatDir.lengthSquared() < 0.0001) {
            flatDir = new Vector(0, 0, 1);
        } else {
            flatDir.normalize();
        }

        Vector strafe = new Vector(-flatDir.getZ(), 0, flatDir.getX()).normalize();
        Vector desired = new Vector(0, 0, 0);

        if (input.forward()) desired.add(flatDir);
        if (input.backward()) desired.subtract(flatDir);
        if (input.left()) desired.subtract(strafe);
        if (input.right()) desired.add(strafe);

        double currentY = entity.getVelocity().getY();

        if (desired.lengthSquared() > 0.001) {
            desired.normalize().multiply(speed);

            currentVelocity.setX(currentVelocity.getX() * 0.25 + desired.getX() * 0.75);
            currentVelocity.setZ(currentVelocity.getZ() * 0.25 + desired.getZ() * 0.75);

            if (input.jump() && (entity.isOnGround() || (currentY >= -0.10 && currentY <= 0.10))) {
                currentY = jumpVelocity;
                Location loc = entity.getLocation();
                spawnParticleSafe(loc, Particle.CLOUD, 4, 0.2, 0.05, 0.2, 0.05);
            }

            Vector finalVel = new Vector(currentVelocity.getX(), currentY, currentVelocity.getZ());
            entity.setVelocity(finalVel);

            if (Math.random() < 0.25) {
                Location loc = entity.getLocation().add(0, 0.2, 0);
                if (type == MountType.FOX) {
                    spawnParticleSafe(loc, Particle.FLAME, 1, 0.15, 0.1, 0.15, 0.01);
                } else if (type == MountType.SNIFFER) {
                    spawnParticleSafe(loc, Particle.HAPPY_VILLAGER, 2, 0.3, 0.1, 0.3, 0);
                } else if (type == MountType.PANDA) {
                    spawnParticleSafe(loc.clone().add(0, 0.6, 0), Particle.HEART, 1, 0.2, 0.2, 0.2, 0);
                } else if (type == MountType.RABBIT) {
                    spawnParticleSafe(loc, Particle.HAPPY_VILLAGER, 1, 0.15, 0.1, 0.15, 0);
                } else if (type == MountType.ARMADILLO) {
                    spawnParticleSafe(loc, Particle.CRIT, 2, 0.1, 0.1, 0.1, 0.02);
                } else if (type == MountType.CAT) {
                    spawnParticleSafe(loc, Particle.WITCH, 1, 0.1, 0.1, 0.1, 0.02);
                } else if (type == MountType.POLAR_BEAR) {
                    spawnParticleSafe(loc, Particle.SNOWFLAKE, 2, 0.2, 0.1, 0.2, 0.01);
                }
            }
        } else {
            currentVelocity.setX(currentVelocity.getX() * 0.45);
            currentVelocity.setZ(currentVelocity.getZ() * 0.45);
            if (Math.abs(currentVelocity.getX()) < 0.02) currentVelocity.setX(0);
            if (Math.abs(currentVelocity.getZ()) < 0.02) currentVelocity.setZ(0);

            entity.setVelocity(new Vector(currentVelocity.getX(), Math.min(currentY, 0), currentVelocity.getZ()));
        }
    }

    private void handleFollowing() {
        Location pLoc = player.getLocation();
        Location mLoc = entity.getLocation();

        if (pLoc.getWorld() == null || mLoc.getWorld() == null || !pLoc.getWorld().equals(mLoc.getWorld())) {
            entity.teleport(pLoc);
            return;
        }

        double distSq = pLoc.distanceSquared(mLoc);
        double dist = Math.sqrt(distSq);

        if (dist > teleportDistance) {
            Vector offset = pLoc.getDirection().clone().multiply(-4.0).setY(type.isFlying() ? 2.5 : 0.2);
            Location tpDest = pLoc.clone().add(offset);
            if (tpDest.getBlock().getType().isSolid()) {
                tpDest = pLoc.clone().add(0, type.isFlying() ? 2.5 : 0.5, 0);
            }
            entity.teleport(tpDest);
            spawnParticleSafe(entity.getLocation().add(0, 0.5, 0), Particle.PORTAL, 12, 0.3, 0.3, 0.3, 0.06);
            return;
        }

        if (attackEnabled && handleGuardianAttack(mLoc)) {
            return;
        }

        Vector toPlayer = pLoc.toVector().subtract(mLoc.toVector());
        float lookAtPlayerYaw = (toPlayer.lengthSquared() > 0.0001) ?
                (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ())) : pLoc.getYaw();

        if (type.isFlying()) {
            if (dist > followStartDistance) {
                Location hoverTarget = pLoc.clone().add(0, 2.4, 0);
                Vector diff = hoverTarget.toVector().subtract(mLoc.toVector());
                if (diff.lengthSquared() > 0.2) {
                    Vector fly = diff.normalize().multiply(Math.min(1.1, speed * 0.75));
                    float moveYaw = (float) Math.toDegrees(Math.atan2(-diff.getX(), diff.getZ()));
                    if (type == MountType.MINIDRAGON) {
                        float flyYaw = (float) Math.toDegrees(Math.atan2(-diff.getX(), diff.getZ()));
                        Location nextFollow = mLoc.clone().add(fly);
                        nextFollow.setYaw((flyYaw + 180.0f) % 360.0f);
                        nextFollow.setPitch(0);
                        if (!nextFollow.getBlock().getType().isSolid()) {
                            entity.teleport(nextFollow, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                        }
                    } else {
                        entity.setGravity(false);
                        entity.setVelocity(fly);
                        entity.setRotation(moveYaw, 0);
                        entity.setBodyYaw(moveYaw);
                    }
                }
            } else {
                if (type == MountType.MINIDRAGON) {
                    float dragonYaw = (lookAtPlayerYaw + 180.0f) % 360.0f;
                    entity.setRotation(dragonYaw, 0);
                    entity.setBodyYaw(dragonYaw);
                } else {
                    entity.setVelocity(new Vector(0, 0, 0));
                    entity.setRotation(lookAtPlayerYaw, 0);
                    entity.setBodyYaw(lookAtPlayerYaw);
                }
            }
            if (entity.getFireTicks() > 0) {
                entity.setFireTicks(0);
            }
        } else {
            if (entity instanceof Mob mob) {
                try {
                    mob.lookAt(player);
                } catch (Throwable ignored) {}
                entity.setRotation(lookAtPlayerYaw, 0);
                entity.setBodyYaw(lookAtPlayerYaw);

                if (dist > followStartDistance) {
                    pathfindCooldown--;
                    if (pathfindCooldown <= 0) {
                        pathfindCooldown = 6;
                        mob.getPathfinder().moveTo(pLoc, 1.35);
                    }
                } else {
                    pathfindCooldown = 0;
                    mob.getPathfinder().stopPathfinding();
                }
            }
        }
    }

    private boolean handleGuardianAttack(Location mLoc) {
        if (!attackEnabled) return false;
        long now = System.currentTimeMillis();

        // 1. Caso exclusivo de represalia (MiniDragón del End)
        if (type.isRetaliateOnly()) {
            if (retaliationTargetId == null) return false;
            if (now - retaliationTargetTime > 20000L) {
                retaliationTargetId = null;
                return false;
            }
            Player victim = player.getServer().getPlayer(retaliationTargetId);
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                retaliationTargetId = null;
                return false;
            }
            if (!isAttackAllowedAt(victim.getLocation(), true, false)) {
                return false;
            }

            return executeAttackOnTarget(victim, mLoc, now);
        }

        // 2. Caso guardián general (Phantom u otros configurados)
        LivingEntity bestTarget = null;
        double closestDistSq = attackRange * attackRange;

        for (Entity nearby : entity.getWorld().getNearbyEntities(mLoc, attackRange, attackRange, attackRange)) {
            if (nearby instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                if (living.getUniqueId().equals(ownerId)) continue;

                boolean isTargetPlayer = living instanceof Player;
                boolean isTargetMonster = living instanceof Monster;

                if (isTargetPlayer) {
                    Player targetPlayer = (Player) living;
                    if (targetPlayer.getGameMode() == GameMode.CREATIVE || targetPlayer.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    if (!targetRivalPlayers) continue;
                    if (!isAttackAllowedAt(targetPlayer.getLocation(), true, false)) {
                        continue;
                    }
                } else if (isTargetMonster) {
                    if (!targetMonsters) continue;
                    if (!isAttackAllowedAt(living.getLocation(), false, false)) {
                        continue;
                    }
                } else {
                    continue;
                }

                double d = living.getLocation().distanceSquared(mLoc);
                if (d < closestDistSq) {
                    closestDistSq = d;
                    bestTarget = living;
                }
            }
        }

        if (bestTarget != null) {
            return executeAttackOnTarget(bestTarget, mLoc, now);
        }

        return false;
    }

    private boolean executeAttackOnTarget(LivingEntity target, Location mLoc, long now) {
        Vector attackDir = target.getLocation().add(0, 0.6, 0).toVector().subtract(mLoc.toVector());
        double distToTarget = target.getLocation().distance(mLoc);

        if (distToTarget <= 2.5) {
            if (now - lastAttackTime > attackCooldownMs) {
                lastAttackTime = now;
                target.damage(attackDamage, entity);
                Sound attackSound = type == MountType.MINIDRAGON ? Sound.ENTITY_ENDER_DRAGON_GROWL :
                        (type.isFlying() ? Sound.ENTITY_PHANTOM_BITE : Sound.ENTITY_PLAYER_ATTACK_CRIT);
                entity.getWorld().playSound(entity.getLocation(), attackSound, 1.4f, 1.0f);
                spawnParticleSafe(target.getLocation().add(0, 1, 0), Particle.CRIT, 10, 0.3, 0.3, 0.3, 0.1);
                spawnParticleSafe(target.getLocation().add(0, 1, 0), Particle.DRAGON_BREATH, 8, 0.2, 0.2, 0.2, 0.05);

                if (type.isFlying()) {
                    entity.setVelocity(new Vector(0, 0.65, 0));
                }
            }
        } else {
            if (type.isFlying()) {
                Vector dive = attackDir.normalize().multiply(1.25);
                float targetYaw = (float) Math.toDegrees(Math.atan2(-attackDir.getX(), attackDir.getZ()));
                if (type == MountType.MINIDRAGON) {
                    Location nextAttack = mLoc.clone().add(dive);
                    nextAttack.setYaw((targetYaw + 180.0f) % 360.0f);
                    nextAttack.setPitch(25);
                    entity.teleport(nextAttack, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                } else {
                    entity.setVelocity(dive);
                    entity.setRotation(targetYaw, 25);
                }
            } else if (entity instanceof Mob mob) {
                mob.getPathfinder().moveTo(target, 1.35);
            }
        }
        return true;
    }

    public void dismiss() {
        if (entity != null && entity.isValid()) {
            entity.eject();
            spawnParticleSafe(entity.getLocation().add(0, 0.5, 0), Particle.POOF, 10, 0.3, 0.3, 0.3, 0.05);
            entity.remove();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public MountType getType() {
        return type;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && player != null && player.isOnline();
    }

    private void spawnParticleSafe(Location loc, Particle particle, int count, double ox, double oy, double oz, double extra) {
        if (loc == null || loc.getWorld() == null || particle == null) return;
        try {
            loc.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, extra);
        } catch (Throwable ignored) {}
    }
}
