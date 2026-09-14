package dev.silentauth.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class Reflect {

    private Reflect() {
    }

    public static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                continue;
            }
        }
        throw new IllegalStateException("None of " + join(names) + " exist on " + owner.getName());
    }

    public static void set(Object instance, Class<?> owner, Object value, String... names) {
        Field field = findField(owner, names);
        try {
            stripFinal(field);
            field.set(instance, value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write " + join(names) + " on " + owner.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object instance, Class<?> owner, String... names) {
        Field field = findField(owner, names);
        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read " + join(names) + " on " + owner.getName(), e);
        }
    }

    private static void stripFinal(Field field) {
        if (!Modifier.isFinal(field.getModifiers())) {
            return;
        }
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception ignored) {
            return;
        }
    }

    private static String join(String[] names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(names[i]);
        }
        return sb.toString();
    }
}
