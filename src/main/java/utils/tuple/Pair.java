package utils.tuple;

public class Pair<K, V> {

    private final K left;
    private final V right;

    public static <K, V> Pair<K, V> createPair(K element0, V element1) {
        return new Pair<K, V>(element0, element1);
    }

    public Pair(K element0, V element1) {
        this.left = element0;
        this.right = element1;
    }

    public K getLeft() {
        return left;
    }

    public V getRight() {
        return right;
    }

}
