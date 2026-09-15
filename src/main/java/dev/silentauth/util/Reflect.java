package dev.silentauth.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class Reflect {

    private Reflect() {
    }

    /**
     * Finds a field by name, then - if none of the names matched - by type.
     *
     * <p>The names cover the mapped and the obfuscated build. The type search is the safety
     * net: every field this mod touches is the only one of its type on its owner, so looking
     * it up by type keeps working even if a name is wrong or the mappings move.</p>
     */
    public static Field find(Class<?> owner, Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Try the next name, then fall through to the type search.
            }
        }

        Field match = null;
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                continue;
            }
            if (match != null) {
                throw new IllegalStateException(owner.getName() + " has more than one " + type.getSimpleName()
                        + " field, cannot pick between " + match.getName() + " and " + field.getName());
            }
            match = field;
        }
        if (match == null) {
            throw new IllegalStateException("No " + type.getSimpleName() + " field named " + join(names)
                    + " on " + owner.getName());
        }
        match.setAccessible(true);
        Log.warn("Found " + owner.getSimpleName() + "." + match.getName() + " by type rather than by name ("
                + join(names) + "), the mappings may have moved");
        return match;
    }

    public static void set(Object instance, Class<?> owner, Class<?> type, Object value, String... names) {
        Field field = find(owner, type, names);
        try {
            stripFinal(field);
            field.set(instance, value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not write " + join(names) + " on " + owner.getName(), e);
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
            // Field.set copes with a final instance field on Java 8 anyway once it is accessible.
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
