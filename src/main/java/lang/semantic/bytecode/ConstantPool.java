package lang.semantic.bytecode;

import java.util.*;

public final class ConstantPool {

    public enum Tag { INT, FLOAT, BOOL, STRING }

    public static final class Const {
        public final Tag tag;
        public final Object value;
        public Const(Tag tag, Object value) {
            this.tag = tag; this.value = value;
        }
    }

    private final List<Const> pool = new ArrayList<>();
    private final Map<ConstKey, Integer> index = new HashMap<>();

    private record ConstKey(Tag tag, Object value) {}

    public int addInt(int v) { return add(Tag.INT, v); }
    public int addFloat(double v) { return add(Tag.FLOAT, v); }
    public int addBool(boolean v) { return add(Tag.BOOL, v); }
    public int addString(String v) { return add(Tag.STRING, v); }

    private int add(Tag tag, Object value) {
        ConstKey key = new ConstKey(tag, value);
        Integer idx = index.get(key);
        if (idx != null) return idx;
        int newIdx = pool.size();
        pool.add(new Const(tag, value));
        index.put(key, newIdx);
        return newIdx;
    }

    public List<Const> getPool() { return pool; }
}
