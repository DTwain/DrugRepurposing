package org.example.domain.graphComponents;

/**
 * Represents an undirected edge between two NodeData instances.
 */
public class EdgeData {
    private final NodeData a;
    private final NodeData b;

    /**
     * @param a one endpoint of the edge
     * @param b the other endpoint of the edge
     */
    public EdgeData(NodeData a, NodeData b) {
        this.a = a;
        this.b = b;
    }

    public NodeData getA() {
        return a;
    }

    public NodeData getB() {
        return b;
    }

    @Override
    public String toString() {
        return "EdgeData{" +
                "a=" + a +
                ", b=" + b +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EdgeData)) return false;
        EdgeData edge = (EdgeData) o;
        // since graph is undirected, (a,b) == (b,a)
        return (a.equals(edge.a) && b.equals(edge.b)) ||
                (a.equals(edge.b) && b.equals(edge.a));
    }

    @Override
    public int hashCode() {
        // order‐independent hash
        return a.hashCode() + b.hashCode();
    }
}
