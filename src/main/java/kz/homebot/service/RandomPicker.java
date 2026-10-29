package kz.homebot.service;

import java.util.List;
import java.util.random.RandomGenerator;

public class RandomPicker {
    private final RandomGenerator random;
    public RandomPicker(RandomGenerator random) { this.random = random; }
    public <T> T pick(List<T> options) {
        if (options == null || options.isEmpty()) throw new IllegalArgumentException("No options");
        return options.get(random.nextInt(options.size()));
    }
    public boolean coin() { return random.nextBoolean(); }
}
