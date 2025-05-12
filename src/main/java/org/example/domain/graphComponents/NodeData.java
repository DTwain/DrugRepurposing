package org.example.domain.graphComponents;

/**
 * Represents a single graph node with a label and 2D coordinates.
 */
public class NodeData {
    private final String name;
    private final String type;
    private double x;
    private double y;

    /**
     * @param name the label (e.g. gene symbol)
     * @param x    the x‐coordinate in canvas space
     * @param y    the y‐coordinate in canvas space
     */
    public NodeData(String name, String type, double x, double y) {
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "NodeData{" +
                "name='" + name + '\'' +
                ", x=" + x +
                ", y=" + y +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeData)) return false;
        NodeData node = (NodeData) o;
        return Double.compare(node.x, x) == 0 &&
                Double.compare(node.y, y) == 0 &&
                name.equals(node.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        long temp;
        temp = Double.doubleToLongBits(x);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = 31 * result + (int)(temp ^ (temp >>> 32));
        return result;
    }

    public String getType() {
        return type;
    }
}
