package it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes;

import java.io.Serializable;
import java.util.Objects;

public class Edge implements Serializable {

    // Define the type of edge
    public enum EdgeTypes {
        FORWARD,
        BACKWARD
    }

    private final Node<?> node;
    private final EdgeTypes type;

    public Edge() {
        this.node = null;
        this.type = null;
    }

    public Edge(Node<?> node, EdgeTypes type) {
        this.node = node;
        this.type = type;
    }

    public EdgeTypes getType() {
        return type;
    }

    public Node<?> getNode() {
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge that = (Edge) o;
        return Objects.equals(node, that.node) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, type);
    }
}