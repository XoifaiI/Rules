package rules;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public final class SecureHashMap<K, V> extends AbstractMap<K, V> {
    private static final long SIPHASH_C0 = 0x736f6d6570736575L;
    private static final long SIPHASH_C1 = 0x646f72616e646f6dL;
    private static final long SIPHASH_C2 = 0x6c7967656e657261L;
    private static final long SIPHASH_C3 = 0x7465646279746573L;

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private final long k0;
    private final long k1;

    private Node<K, V>[] table;
    private int size;
    private int threshold;

    private Set<Entry<K, V>> entrySet;

    public SecureHashMap() {
        this(DEFAULT_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public SecureHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }

        SecureRandom random = new SecureRandom();
        this.k0 = random.nextLong();
        this.k1 = random.nextLong();

        int capacity = tableSizeFor(initialCapacity);
        this.table = (Node<K, V>[]) new Node[capacity];
        this.threshold = (int) (capacity * LOAD_FACTOR);
        this.size = 0;
    }

    public static <K, V> SecureHashMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
        Objects.requireNonNull(map, "map cannot be null");
        SecureHashMap<K, V> result = new SecureHashMap<>(Math.max(DEFAULT_CAPACITY,
                (int) (map.size() / LOAD_FACTOR) + 1));
        result.putAll(map);
        return result;
    }

    private int secureHash(Object key) {
        if (key == null) {
            return 0;
        }

        byte[] data;
        if (key instanceof String) {
            data = ((String) key).getBytes(StandardCharsets.UTF_8);
        } else if (key instanceof byte[]) {
            data = (byte[]) key;
        } else {
            data = key.toString().getBytes(StandardCharsets.UTF_8);
        }

        long hash = sipHash24(data);

        int h = (int) (hash ^ (hash >>> 32));
        return h ^ (h >>> 16);
    }

    private long sipHash24(byte[] data) {
        long v0 = k0 ^ SIPHASH_C0;
        long v1 = k1 ^ SIPHASH_C1;
        long v2 = k0 ^ SIPHASH_C2;
        long v3 = k1 ^ SIPHASH_C3;

        int len = data.length;
        int blocks = len / 8;

        for (int i = 0; i < blocks; i++) {
            long m = bytesToLongLE(data, i * 8);
            v3 ^= m;

            v0 += v1;
            v1 = Long.rotateLeft(v1, 13);
            v1 ^= v0;
            v0 = Long.rotateLeft(v0, 32);
            v2 += v3;
            v3 = Long.rotateLeft(v3, 16);
            v3 ^= v2;
            v0 += v3;
            v3 = Long.rotateLeft(v3, 21);
            v3 ^= v0;
            v2 += v1;
            v1 = Long.rotateLeft(v1, 17);
            v1 ^= v2;
            v2 = Long.rotateLeft(v2, 32);

            v0 += v1;
            v1 = Long.rotateLeft(v1, 13);
            v1 ^= v0;
            v0 = Long.rotateLeft(v0, 32);
            v2 += v3;
            v3 = Long.rotateLeft(v3, 16);
            v3 ^= v2;
            v0 += v3;
            v3 = Long.rotateLeft(v3, 21);
            v3 ^= v0;
            v2 += v1;
            v1 = Long.rotateLeft(v1, 17);
            v1 ^= v2;
            v2 = Long.rotateLeft(v2, 32);

            v0 ^= m;
        }

        long m = ((long) len) << 56;
        int remaining = len % 8;
        int offset = blocks * 8;

        switch (remaining) {
            case 7:
                m |= ((long) (data[offset + 6] & 0xff)) << 48;
            case 6:
                m |= ((long) (data[offset + 5] & 0xff)) << 40;
            case 5:
                m |= ((long) (data[offset + 4] & 0xff)) << 32;
            case 4:
                m |= ((long) (data[offset + 3] & 0xff)) << 24;
            case 3:
                m |= ((long) (data[offset + 2] & 0xff)) << 16;
            case 2:
                m |= ((long) (data[offset + 1] & 0xff)) << 8;
            case 1:
                m |= ((long) (data[offset] & 0xff));
            case 0:
                break;
        }

        v3 ^= m;

        v0 += v1;
        v1 = Long.rotateLeft(v1, 13);
        v1 ^= v0;
        v0 = Long.rotateLeft(v0, 32);
        v2 += v3;
        v3 = Long.rotateLeft(v3, 16);
        v3 ^= v2;
        v0 += v3;
        v3 = Long.rotateLeft(v3, 21);
        v3 ^= v0;
        v2 += v1;
        v1 = Long.rotateLeft(v1, 17);
        v1 ^= v2;
        v2 = Long.rotateLeft(v2, 32);

        v0 += v1;
        v1 = Long.rotateLeft(v1, 13);
        v1 ^= v0;
        v0 = Long.rotateLeft(v0, 32);
        v2 += v3;
        v3 = Long.rotateLeft(v3, 16);
        v3 ^= v2;
        v0 += v3;
        v3 = Long.rotateLeft(v3, 21);
        v3 ^= v0;
        v2 += v1;
        v1 = Long.rotateLeft(v1, 17);
        v1 ^= v2;
        v2 = Long.rotateLeft(v2, 32);

        v0 ^= m;

        v2 ^= 0xff;

        for (int i = 0; i < 4; i++) {
            v0 += v1;
            v1 = Long.rotateLeft(v1, 13);
            v1 ^= v0;
            v0 = Long.rotateLeft(v0, 32);
            v2 += v3;
            v3 = Long.rotateLeft(v3, 16);
            v3 ^= v2;
            v0 += v3;
            v3 = Long.rotateLeft(v3, 21);
            v3 ^= v0;
            v2 += v1;
            v1 = Long.rotateLeft(v1, 17);
            v1 ^= v2;
            v2 = Long.rotateLeft(v2, 32);
        }

        return v0 ^ v1 ^ v2 ^ v3;
    }

    private static long bytesToLongLE(byte[] data, int offset) {
        return ((long) (data[offset] & 0xff))
                | (((long) (data[offset + 1] & 0xff)) << 8)
                | (((long) (data[offset + 2] & 0xff)) << 16)
                | (((long) (data[offset + 3] & 0xff)) << 24)
                | (((long) (data[offset + 4] & 0xff)) << 32)
                | (((long) (data[offset + 5] & 0xff)) << 40)
                | (((long) (data[offset + 6] & 0xff)) << 48)
                | (((long) (data[offset + 7] & 0xff)) << 56);
    }

    private static int indexFor(int h, int length) {
        return h & (length - 1);
    }

    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return getNode(key) != null;
    }

    @Override
    public V get(Object key) {
        Node<K, V> node = getNode(key);
        return node == null ? null : node.value;
    }

    private Node<K, V> getNode(Object key) {
        int hash = secureHash(key);
        int index = indexFor(hash, table.length);

        for (Node<K, V> node = table[index]; node != null; node = node.next) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                return node;
            }
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        int hash = secureHash(key);
        int index = indexFor(hash, table.length);

        for (Node<K, V> node = table[index]; node != null; node = node.next) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                V oldValue = node.value;
                node.value = value;
                return oldValue;
            }
        }

        addNode(hash, key, value, index);
        return null;
    }

    private void addNode(int hash, K key, V value, int index) {
        Node<K, V> newNode = new Node<>(hash, key, value, table[index]);
        table[index] = newNode;

        if (++size > threshold) {
            resize();
        }
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldTable = table;
        int oldCapacity = oldTable.length;

        if (oldCapacity >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        int newCapacity = oldCapacity << 1;
        Node<K, V>[] newTable = (Node<K, V>[]) new Node[newCapacity];
        threshold = (int) (newCapacity * LOAD_FACTOR);

        for (Node<K, V> node : oldTable) {
            while (node != null) {
                Node<K, V> next = node.next;
                int index = indexFor(node.hash, newCapacity);
                node.next = newTable[index];
                newTable[index] = node;
                node = next;
            }
        }

        table = newTable;
    }

    @Override
    public V remove(Object key) {
        int hash = secureHash(key);
        int index = indexFor(hash, table.length);

        Node<K, V> prev = null;
        Node<K, V> node = table[index];

        while (node != null) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                if (prev == null) {
                    table[index] = node.next;
                } else {
                    prev.next = node.next;
                }
                size--;
                return node.value;
            }
            prev = node;
            node = node.next;
        }
        return null;
    }

    @Override
    public void clear() {
        if (size > 0) {
            size = 0;
            for (int i = 0; i < table.length; i++) {
                table[i] = null;
            }
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new EntrySet();
        }
        return entrySet;
    }

    private static class Node<K, V> implements Entry<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof Entry<?, ?> e))
                return false;
            return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }

    private class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            SecureHashMap.this.clear();
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?> e))
                return false;
            Node<K, V> node = getNode(e.getKey());
            return node != null && Objects.equals(node.value, e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Entry<?, ?> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                return SecureHashMap.this.remove(key, value);
            }
            return false;
        }
    }

    private class EntryIterator implements Iterator<Entry<K, V>> {
        private int index;
        private Node<K, V> current;
        private Node<K, V> next;

        EntryIterator() {
            current = null;
            next = null;
            index = 0;

            while (index < table.length && table[index] == null) {
                index++;
            }
            if (index < table.length) {
                next = table[index];
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Entry<K, V> next() {
            if (next == null) {
                throw new NoSuchElementException();
            }

            current = next;

            next = current.next;
            while (next == null && ++index < table.length) {
                next = table[index];
            }

            return current;
        }

        @Override
        public void remove() {
            if (current == null) {
                throw new IllegalStateException();
            }
            SecureHashMap.this.remove(current.key);
            current = null;
        }
    }

    public boolean remove(Object key, Object value) {
        Node<K, V> node = getNode(key);
        if (node != null && Objects.equals(node.value, value)) {
            remove(key);
            return true;
        }
        return false;
    }
}