package com.example.helios.testutil;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public final class UnsafeTestHelper {

    private UnsafeTestHelper() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateWithoutConstructor(Class<T> type) {
        try {
            return (T) unsafe().allocateInstance(type);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to allocate instance for " + type.getName(), e);
        }
    }

    public static void setObjectField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            long offset = unsafe().objectFieldOffset(field);
            unsafe().putObject(target, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set field: " + fieldName, e);
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }
}
