package com.queueflow.common;

import java.util.Objects;

/**
 * Represents one field of a PATCH-style partial update request, with three
 * possible states relative to the incoming JSON body:
 *
 * <ul>
 *   <li>{@link #undefined()} - the JSON property was absent entirely -&gt;
 *       "do not change this field".</li>
 *   <li>{@link #of(Object)} with a non-null value - the property was present
 *       with a value -&gt; "set the field to this value".</li>
 *   <li>{@link #of(Object)} with null - the property was present but set to
 *       JSON null -&gt; "clear the field".</li>
 * </ul>
 *
 * A plain nullable field cannot distinguish the first and third cases -
 * both collapse to Java null. This type exists specifically to make that
 * distinction explicit, without a heavyweight PATCH framework or a
 * {@code Map<String, Object>}.
 *
 * <p>For this to work with Jackson deserialization, the containing DTO must
 * NOT be a record: a record's canonical constructor is always invoked with
 * some value for every component, so an absent JSON property and an
 * explicit JSON null both arrive as plain null, indistinguishably. Instead,
 * the DTO should be a plain class with a setter per PatchField property
 * that wraps its incoming argument with {@link #of(Object)}. Jackson's
 * JavaBean deserialization only invokes a setter when the corresponding
 * JSON key is present (calling it with null if the JSON value is null, and
 * never calling it at all if the key is absent) - exactly the presence
 * signal this type needs, with no custom (de)serializer required.
 */
public final class PatchField<T> {

    private static final PatchField<?> UNDEFINED = new PatchField<>(false, null);

    private final boolean present;
    private final T value;

    private PatchField(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> PatchField<T> undefined() {
        return (PatchField<T>) UNDEFINED;
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public T value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PatchField<?> other)) {
            return false;
        }
        return present == other.present && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(present, value);
    }

    @Override
    public String toString() {
        return present ? "PatchField{value=" + value + "}" : "PatchField{undefined}";
    }
}
