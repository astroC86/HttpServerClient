package collections;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BiMap<K,V> {
    Map<K,V> map    = new HashMap<K, V>();
    Map<V,K> inverse = new HashMap<V, K>();

    public void put(K k, V v) {
        map.put(k, v);
        inverse.put(v, k);
    }

    public V get(K k) {
        return map.get(k);
    }

    public Map<V,K> inverse(){
        return Map.copyOf(this.inverse);
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public boolean containsKey(V contentType) {
        return map.containsKey(contentType);
    }
}
