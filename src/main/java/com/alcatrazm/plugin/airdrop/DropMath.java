package com.alcatrazm.plugin.airdrop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Utilidades matematicas para seleccion aleatoria de coordenadas y botin de AirDrops.
 */
public final class DropMath {

    private DropMath() {
    }

    public static Coords generateRandomCoords(double radius, RandomGenerator random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * radius;
        return new Coords((int) (Math.cos(angle) * dist), (int) (Math.sin(angle) * dist));
    }

    public static <T> List<T> pickRandom(List<T> list, int min, int max, RandomGenerator random) {
        if (list.isEmpty()) {
            return List.of();
        }
        int lo = Math.min(list.size(), Math.max(1, min));
        int hi = Math.min(list.size(), Math.max(lo, max));
        int count = random.nextInt(lo, hi + 1);
        List<T> copy = new ArrayList<>(list);
        for (int i = 0; i < count; i++) {
            int j = random.nextInt(i, copy.size());
            Collections.swap(copy, i, j);
        }
        return List.copyOf(copy.subList(0, count));
    }

    public enum DropType {
        LIBRE
    }

    public record Coords(int x, int z) {
    }
}
