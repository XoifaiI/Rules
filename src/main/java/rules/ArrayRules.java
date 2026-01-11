package rules;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;

public final class ArrayRules {

    private static final String NULL_MSG = "Array is null";
    private static final String NOT_ARRAY_MSG = "Value is not an array";
    private static final int SECURE_COMPARE_MAX_LENGTH = 1024;
    private static final int ABSOLUTE_MAX_LENGTH = 100_000_000;

    private ArrayRules() {
    }

    public static <T> Rule<T[]> notNull() {
        return value -> value != null
                ? ValidationResult.valid()
                : ValidationResult.invalid(NULL_MSG);
    }

    public static Rule<byte[]> bytesNotNull() {
        return value -> value != null
                ? ValidationResult.valid()
                : ValidationResult.invalid(NULL_MSG);
    }

    public static Rule<int[]> intsNotNull() {
        return value -> value != null
                ? ValidationResult.valid()
                : ValidationResult.invalid(NULL_MSG);
    }

    public static Rule<long[]> longsNotNull() {
        return value -> value != null
                ? ValidationResult.valid()
                : ValidationResult.invalid(NULL_MSG);
    }

    public static Rule<double[]> doublesNotNull() {
        return value -> value != null
                ? ValidationResult.valid()
                : ValidationResult.invalid(NULL_MSG);
    }

    public static Rule<Object> isArrayOf(Class<?> componentType) {
        Objects.requireNonNull(componentType, "componentType cannot be null");
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            Class<?> valueClass = value.getClass();
            if (!valueClass.isArray()) {
                return ValidationResult.invalid(NOT_ARRAY_MSG);
            }
            Class<?> actualComponent = valueClass.getComponentType();
            if (!componentType.isAssignableFrom(actualComponent)) {
                return ValidationResult.invalid("Array component type mismatch");
            }
            return ValidationResult.valid();
        };
    }

    public static <T> Rule<T[]> notEmpty() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length > 0
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array is empty");
        };
    }

    public static Rule<byte[]> bytesNotEmpty() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length > 0
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array is empty");
        };
    }

    public static Rule<int[]> intsNotEmpty() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length > 0
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array is empty");
        };
    }

    public static <T> Rule<T[]> length(int exactLength) {
        if (exactLength < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length == exactLength
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array length is incorrect");
        };
    }

    public static Rule<byte[]> bytesLength(int exactLength) {
        if (exactLength < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length == exactLength
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array length is incorrect");
        };
    }

    public static <T> Rule<T[]> lengthBetween(int min, int max) {
        validateLengthBounds(min, max);
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            int len = value.length;
            return (len >= min && len <= max)
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array length is out of range");
        };
    }

    public static Rule<byte[]> bytesLengthBetween(int min, int max) {
        validateLengthBounds(min, max);
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            int len = value.length;
            return (len >= min && len <= max)
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array length is out of range");
        };
    }

    public static <T> Rule<T[]> minLength(int min) {
        if (min < 0) {
            throw new IllegalArgumentException("min cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length >= min
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array is too short");
        };
    }

    public static Rule<byte[]> bytesMinLength(int min) {
        if (min < 0) {
            throw new IllegalArgumentException("min cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length >= min
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array is too short");
        };
    }

    public static <T> Rule<T[]> maxLength(int max) {
        validateMaxLength(max);
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length <= max
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array exceeds maximum length");
        };
    }

    public static Rule<byte[]> bytesMaxLength(int max) {
        validateMaxLength(max);
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length <= max
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array exceeds maximum length");
        };
    }

    public static Rule<int[]> intsMaxLength(int max) {
        validateMaxLength(max);
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return value.length <= max
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array exceeds maximum length");
        };
    }

    public static <T> Rule<T[]> allElements(Rule<T> elementRule) {
        Objects.requireNonNull(elementRule, "elementRule cannot be null");
        return new Rule<T[]>() {
            @Override
            public ValidationResult validate(T[] value) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }
                for (int i = 0; i < value.length; i++) {
                    ValidationResult result = elementRule.validate(value[i]);
                    if (result == null || result.isInvalid()) {
                        return ValidationResult.invalid("Element validation failed");
                    }
                }
                return ValidationResult.valid();
            }

            @Override
            public ValidationResult validateWithState(T[] value, ValidationState state) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }

                ValidationResult sizeCheck = state.checkCollectionSize(value.length);
                if (sizeCheck.isInvalid()) {
                    return sizeCheck;
                }

                ValidationResult scopeCheck = state.enterScope(value);
                if (scopeCheck.isInvalid()) {
                    return scopeCheck;
                }

                try {
                    for (int i = 0; i < value.length; i++) {
                        state.checkTimeout();
                        ValidationResult result = elementRule.validateWithState(value[i], state);
                        if (result == null || result.isInvalid()) {
                            if (result != null && result.isSystemError()) {
                                return result;
                            }
                            return ValidationResult.invalid(
                                    state.context().errorMessage(
                                            "Element[" + i + "]: " + (result != null
                                                    ? result.messageOrDefault("invalid")
                                                    : "invalid"),
                                            "Element validation failed"));
                        }
                    }
                    return ValidationResult.valid();
                } finally {
                    state.exitScope(value);
                }
            }
        };
    }

    public static <T> Rule<T[]> anyElement(Rule<T> elementRule) {
        Objects.requireNonNull(elementRule, "elementRule cannot be null");
        return new Rule<T[]>() {
            @Override
            public ValidationResult validate(T[] value) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }
                for (int i = 0; i < value.length; i++) {
                    ValidationResult result = elementRule.validate(value[i]);
                    if (result != null && result.isValid()) {
                        return ValidationResult.valid();
                    }
                }
                return ValidationResult.invalid("No element matches");
            }

            @Override
            public ValidationResult validateWithState(T[] value, ValidationState state) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }

                ValidationResult sizeCheck = state.checkCollectionSize(value.length);
                if (sizeCheck.isInvalid()) {
                    return sizeCheck;
                }

                ValidationResult scopeCheck = state.enterScope(value);
                if (scopeCheck.isInvalid()) {
                    return scopeCheck;
                }

                try {
                    for (int i = 0; i < value.length; i++) {
                        state.checkTimeout();
                        ValidationResult result = elementRule.validateWithState(value[i], state);
                        if (result != null && result.isSystemError()) {
                            return result;
                        }
                        if (result != null && result.isValid()) {
                            return ValidationResult.valid();
                        }
                    }
                    return ValidationResult.invalid("No element matches");
                } finally {
                    state.exitScope(value);
                }
            }
        };
    }

    public static <T> Rule<T[]> noElements(Rule<T> elementRule) {
        Objects.requireNonNull(elementRule, "elementRule cannot be null");
        return new Rule<T[]>() {
            @Override
            public ValidationResult validate(T[] value) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }
                for (int i = 0; i < value.length; i++) {
                    ValidationResult result = elementRule.validate(value[i]);
                    if (result != null && result.isValid()) {
                        return ValidationResult.invalid("Element matched unexpectedly");
                    }
                }
                return ValidationResult.valid();
            }

            @Override
            public ValidationResult validateWithState(T[] value, ValidationState state) {
                if (value == null) {
                    return ValidationResult.invalid(NULL_MSG);
                }

                ValidationResult sizeCheck = state.checkCollectionSize(value.length);
                if (sizeCheck.isInvalid()) {
                    return sizeCheck;
                }

                ValidationResult scopeCheck = state.enterScope(value);
                if (scopeCheck.isInvalid()) {
                    return scopeCheck;
                }

                try {
                    for (int i = 0; i < value.length; i++) {
                        state.checkTimeout();
                        ValidationResult result = elementRule.validateWithState(value[i], state);
                        if (result != null && result.isSystemError()) {
                            return result;
                        }
                        if (result != null && result.isValid()) {
                            return ValidationResult.invalid(
                                    state.context().errorMessage(
                                            "Element[" + i + "]: matched unexpectedly",
                                            "Element matched unexpectedly"));
                        }
                    }
                    return ValidationResult.valid();
                } finally {
                    state.exitScope(value);
                }
            }
        };
    }

    public static <T> Rule<T[]> noNullElements() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (value[i] == null) {
                    return ValidationResult.invalid("Array contains null element");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static <T> Rule<T[]> contains(T element) {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (Objects.equals(value[i], element)) {
                    return ValidationResult.valid();
                }
            }
            return ValidationResult.invalid("Array does not contain element");
        };
    }

    public static Rule<byte[]> bytesContains(byte element) {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (value[i] == element) {
                    return ValidationResult.valid();
                }
            }
            return ValidationResult.invalid("Array does not contain element");
        };
    }

    public static Rule<int[]> intsContains(int element) {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (value[i] == element) {
                    return ValidationResult.valid();
                }
            }
            return ValidationResult.invalid("Array does not contain element");
        };
    }

    public static Rule<byte[]> secureEquals(byte[] expected) {
        Objects.requireNonNull(expected, "expected cannot be null");
        if (expected.length > SECURE_COMPARE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "expected exceeds maximum secure comparison length");
        }
        byte[] expectedCopy = expected.clone();
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            if (value.length > SECURE_COMPARE_MAX_LENGTH) {
                return ValidationResult.invalid("Array exceeds maximum comparison length");
            }
            return java.security.MessageDigest.isEqual(expectedCopy, value)
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array does not match expected");
        };
    }

    public static Rule<int[]> secureEqualsInts(int[] expected) {
        Objects.requireNonNull(expected, "expected cannot be null");
        if (expected.length > SECURE_COMPARE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "expected exceeds maximum secure comparison length");
        }
        int[] expectedCopy = expected.clone();
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            if (value.length > SECURE_COMPARE_MAX_LENGTH) {
                return ValidationResult.invalid("Array exceeds maximum comparison length");
            }
            return constantTimeEqualsInts(expectedCopy, value)
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array does not match expected");
        };
    }

    public static Rule<long[]> secureEqualsLongs(long[] expected) {
        Objects.requireNonNull(expected, "expected cannot be null");
        if (expected.length > SECURE_COMPARE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "expected exceeds maximum secure comparison length");
        }
        long[] expectedCopy = expected.clone();
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            if (value.length > SECURE_COMPARE_MAX_LENGTH) {
                return ValidationResult.invalid("Array exceeds maximum comparison length");
            }
            return constantTimeEqualsLongs(expectedCopy, value)
                    ? ValidationResult.valid()
                    : ValidationResult.invalid("Array does not match expected");
        };
    }

    public static <T> Rule<T[]> validRange(int offset, int length) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return checkRange(value.length, offset, length);
        };
    }

    public static Rule<byte[]> bytesValidRange(int offset, int length) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            return checkRange(value.length, offset, length);
        };
    }

    public static <T> Rule<T> validDynamicRange(
            java.util.function.Function<T, byte[]> arrayExtractor,
            java.util.function.ToIntFunction<T> offsetExtractor,
            java.util.function.ToIntFunction<T> lengthExtractor) {
        Objects.requireNonNull(arrayExtractor, "arrayExtractor cannot be null");
        Objects.requireNonNull(offsetExtractor, "offsetExtractor cannot be null");
        Objects.requireNonNull(lengthExtractor, "lengthExtractor cannot be null");
        return container -> {
            if (container == null) {
                return ValidationResult.invalid("Container is null");
            }
            byte[] array = arrayExtractor.apply(container);
            if (array == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            int offset = offsetExtractor.applyAsInt(container);
            int length = lengthExtractor.applyAsInt(container);

            if (offset < 0) {
                return ValidationResult.invalid("Offset is negative");
            }
            if (length < 0) {
                return ValidationResult.invalid("Length is negative");
            }
            return checkRange(array.length, offset, length);
        };
    }

    public static <T> java.util.function.Function<T[], T[]> validateAndCopy(
            Rule<T[]> rule, int maxLength) {
        Objects.requireNonNull(rule, "rule cannot be null");
        validateMaxLength(maxLength);
        return value -> {
            if (value == null) {
                throw new ValidationException(NULL_MSG);
            }
            if (value.length > maxLength) {
                throw new ValidationException("Array exceeds maximum length");
            }
            ValidationResult result = rule.validate(value);
            if (result == null || result.isInvalid()) {
                throw new ValidationException(
                        result != null ? result.messageOrDefault("Validation failed")
                                : "Validation failed");
            }
            return Arrays.copyOf(value, value.length);
        };
    }

    public static java.util.function.Function<byte[], byte[]> validateAndCopyBytes(
            Rule<byte[]> rule, int maxLength) {
        Objects.requireNonNull(rule, "rule cannot be null");
        validateMaxLength(maxLength);
        return value -> {
            if (value == null) {
                throw new ValidationException(NULL_MSG);
            }
            if (value.length > maxLength) {
                throw new ValidationException("Array exceeds maximum length");
            }
            ValidationResult result = rule.validate(value);
            if (result == null || result.isInvalid()) {
                throw new ValidationException(
                        result != null ? result.messageOrDefault("Validation failed")
                                : "Validation failed");
            }
            return Arrays.copyOf(value, value.length);
        };
    }

    public static <T> T[] deepCopyOf(
            T[] array,
            java.util.function.UnaryOperator<T> elementCopier,
            IntFunction<T[]> arrayFactory) {
        Objects.requireNonNull(array, "array cannot be null");
        Objects.requireNonNull(elementCopier, "elementCopier cannot be null");
        Objects.requireNonNull(arrayFactory, "arrayFactory cannot be null");

        T[] copy = arrayFactory.apply(array.length);
        for (int i = 0; i < array.length; i++) {
            T element = array[i];
            copy[i] = element != null ? elementCopier.apply(element) : null;
        }
        return copy;
    }

    public static <T> Rule<T[][]> noNullInnerArrays() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (value[i] == null) {
                    return ValidationResult.invalid("Inner array is null");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static <T> Rule<T[][]> isRectangular() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            if (value.length == 0) {
                return ValidationResult.valid();
            }
            if (value[0] == null) {
                return ValidationResult.invalid("Inner array is null");
            }
            int expectedLength = value[0].length;
            for (int i = 1; i < value.length; i++) {
                if (value[i] == null) {
                    return ValidationResult.invalid("Inner array is null");
                }
                if (value[i].length != expectedLength) {
                    return ValidationResult.invalid("Array is not rectangular");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<byte[]> bytesInRange(byte min, byte max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot exceed max");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                byte b = value[i];
                if (b < min || b > max) {
                    return ValidationResult.invalid("Byte value out of range");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<byte[]> bytesInUnsignedRange(int min, int max) {
        if (min < 0 || min > 255) {
            throw new IllegalArgumentException("min must be 0-255");
        }
        if (max < 0 || max > 255) {
            throw new IllegalArgumentException("max must be 0-255");
        }
        if (min > max) {
            throw new IllegalArgumentException("min cannot exceed max");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                int unsigned = value[i] & 0xFF;
                if (unsigned < min || unsigned > max) {
                    return ValidationResult.invalid("Byte value out of range");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<byte[]> isPrintableAscii() {
        return bytesInUnsignedRange(0x20, 0x7E);
    }

    public static Rule<int[]> intsInRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot exceed max");
        }
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                int v = value[i];
                if (v < min || v > max) {
                    return ValidationResult.invalid("Value out of range");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<int[]> intsNonNegative() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (value[i] < 0) {
                    return ValidationResult.invalid("Array contains negative value");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<double[]> doublesNotNaN() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (Double.isNaN(value[i])) {
                    return ValidationResult.invalid("Array contains NaN");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<double[]> doublesFinite() {
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                if (!Double.isFinite(value[i])) {
                    return ValidationResult.invalid("Array contains non-finite value");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static Rule<double[]> doublesInRange(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("bounds cannot be NaN");
        }
        if (Double.isInfinite(min) || Double.isInfinite(max)) {
            throw new IllegalArgumentException("bounds cannot be infinite");
        }
        if (min > max) {
            throw new IllegalArgumentException("min cannot exceed max");
        }
        
        return value -> {
            if (value == null) {
                return ValidationResult.invalid(NULL_MSG);
            }
            for (int i = 0; i < value.length; i++) {
                double v = value[i];
                if (Double.isNaN(v) || v < min || v > max) {
                    return ValidationResult.invalid("Value out of range");
                }
            }
            return ValidationResult.valid();
        };
    }

    public static int safeLength(Object array) {
        Objects.requireNonNull(array, "array cannot be null");
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException(NOT_ARRAY_MSG);
        }
        return Array.getLength(array);
    }

    public static boolean isArray(Object value) {
        return value != null && value.getClass().isArray();
    }

    private static void validateLengthBounds(int min, int max) {
        if (min < 0) {
            throw new IllegalArgumentException("min cannot be negative");
        }
        if (max < 0) {
            throw new IllegalArgumentException("max cannot be negative");
        }
        if (min > max) {
            throw new IllegalArgumentException("min cannot exceed max");
        }
        if (max > ABSOLUTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "max cannot exceed absolute maximum of " + ABSOLUTE_MAX_LENGTH);
        }
    }

    private static void validateMaxLength(int max) {
        if (max < 0) {
            throw new IllegalArgumentException("max cannot be negative");
        }
        if (max > ABSOLUTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "max cannot exceed absolute maximum of " + ABSOLUTE_MAX_LENGTH);
        }
    }

    private static ValidationResult checkRange(int arrayLength, int offset, int length) {
        int end;
        try {
            end = Math.addExact(offset, length);
        } catch (ArithmeticException e) {
            return ValidationResult.invalid("Offset + length overflows");
        }

        if (end > arrayLength) {
            return ValidationResult.invalid("Range exceeds array bounds");
        }
        return ValidationResult.valid();
    }

    private static boolean constantTimeEqualsInts(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }
        byte[] bytesA = new byte[a.length * Integer.BYTES];
        byte[] bytesB = new byte[b.length * Integer.BYTES];
        for (int i = 0; i < a.length; i++) {
            int offset = i * Integer.BYTES;
            int valA = a[i];
            int valB = b[i];
            bytesA[offset] = (byte) (valA >> 24);
            bytesA[offset + 1] = (byte) (valA >> 16);
            bytesA[offset + 2] = (byte) (valA >> 8);
            bytesA[offset + 3] = (byte) valA;
            bytesB[offset] = (byte) (valB >> 24);
            bytesB[offset + 1] = (byte) (valB >> 16);
            bytesB[offset + 2] = (byte) (valB >> 8);
            bytesB[offset + 3] = (byte) valB;
        }
        return java.security.MessageDigest.isEqual(bytesA, bytesB);
    }

    private static boolean constantTimeEqualsLongs(long[] a, long[] b) {
        if (a.length != b.length) {
            return false;
        }
        byte[] bytesA = new byte[a.length * Long.BYTES];
        byte[] bytesB = new byte[b.length * Long.BYTES];
        for (int i = 0; i < a.length; i++) {
            int offset = i * Long.BYTES;
            long valA = a[i];
            long valB = b[i];
            bytesA[offset] = (byte) (valA >> 56);
            bytesA[offset + 1] = (byte) (valA >> 48);
            bytesA[offset + 2] = (byte) (valA >> 40);
            bytesA[offset + 3] = (byte) (valA >> 32);
            bytesA[offset + 4] = (byte) (valA >> 24);
            bytesA[offset + 5] = (byte) (valA >> 16);
            bytesA[offset + 6] = (byte) (valA >> 8);
            bytesA[offset + 7] = (byte) valA;
            bytesB[offset] = (byte) (valB >> 56);
            bytesB[offset + 1] = (byte) (valB >> 48);
            bytesB[offset + 2] = (byte) (valB >> 40);
            bytesB[offset + 3] = (byte) (valB >> 32);
            bytesB[offset + 4] = (byte) (valB >> 24);
            bytesB[offset + 5] = (byte) (valB >> 16);
            bytesB[offset + 6] = (byte) (valB >> 8);
            bytesB[offset + 7] = (byte) valB;
        }
        return java.security.MessageDigest.isEqual(bytesA, bytesB);
    }
}