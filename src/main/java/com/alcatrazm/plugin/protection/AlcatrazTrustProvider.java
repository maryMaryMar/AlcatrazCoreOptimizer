package com.alcatrazm.plugin.protection;

import org.bukkit.entity.Player;

public interface AlcatrazTrustProvider {
    boolean canAccess(Claim claim, Player player);
    boolean canContainer(Claim claim, Player player);
    boolean canBuild(Claim claim, Player player);
}
