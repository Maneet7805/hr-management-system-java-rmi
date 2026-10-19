package com.bhel.hrm.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Test helper: sends an object through the same byte-stream conversion RMI uses. */
public final class SerializationSupport {

    private SerializationSupport() {
    }

    public static byte[] toBytes(Object value) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("could not serialize " + value, e);
        }
    }

    public static Object fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return in.readObject();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T roundTrip(T value) {
        try {
            return (T) fromBytes(toBytes(value));
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("could not deserialize " + value, e);
        }
    }
}
