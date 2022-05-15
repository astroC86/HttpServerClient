package collections;

public class Pair<X, Y> {
    public final X _0;
    public final Y _1;
    public Pair(X _0, Y _1) {
        this._0 = _0;
        this._1 = _1;
    }

    @Override
    public String toString() {
        return "(" + _0 + "," + _1 + ")";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Pair)) return false;

        Pair<X,Y> o_ = (Pair<X,Y>) o;
        if(o_._0 == null || o_._1 == null) return false;
        return o_._0.equals(this._0) && o_._1.equals(this._1);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_0 == null) ? 0 : _0.hashCode());
        result = prime * result + ((_1 == null) ? 0 : _1.hashCode());
        return result;
    }
}