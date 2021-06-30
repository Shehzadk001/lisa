package it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes;

import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.NormalStringGraphUtils;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.Const.ConstValues;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


public abstract class Node<V> implements Serializable {

    protected V value;
    public final String id;
    protected Node<?> forwardParent;
    protected final List<Edge> childrenEdges;
    protected final List<Node<?>> backwardParents;
    private static int counter = 0;
    private final Set<Node<?>> is;

    public Node() {
        this.is = new HashSet<>();
        this.forwardParent = null;
        this.childrenEdges = new ArrayList<>();
        this.backwardParents = new ArrayList<>();
        this.id = "id_" + Node.counter;
        Node.counter += 1;
    }

    public static <T extends Node<?>, V extends Node<?>>
    Map.Entry<T,V> createEdge(T n1, V n2) {
        return new AbstractMap.SimpleEntry<>(n1,n2);
    }

    public int getOutDegree() {
        return this.childrenEdges.size();
    }

    public int getInDegree() {
        return (isRoot() ? 0 : 1) + this.backwardParents.size();
    }

    public boolean isLeaf() {
        return this.getOutDegree() == 0;
    }

    public boolean isRoot() {
        return forwardParent == null;
    }

    public List<Edge> getChildrenEdges() {
        return childrenEdges;
    }

    public List<Node<?>> getChildren() {
        return childrenEdges.stream().map(Edge::getNode).collect(Collectors.toList());
    }

    public List<Node<?>> getForwardChildren() {
        return childrenEdges
                .stream()
                .filter(c -> c.getType() == Edge.EdgeTypes.FORWARD)
                .map(Edge::getNode)
                .collect(Collectors.toList());
    }

    public List<Node<?>> getBackwardChildren() {
        return childrenEdges
                .stream()
                .filter(c -> c.getType() == Edge.EdgeTypes.BACKWARD)
                .map(Edge::getNode)
                .collect(Collectors.toList());
    }

    public Node<?> getForwardParent() {
        return forwardParent;
    }

    public List<Node<?>> getBackwardParents() {
        return backwardParents;
    }

    private <C extends Node<?>> void addChild(C child, Edge.EdgeTypes type) {
        this.childrenEdges.add(new Edge(child, type));
    }

    private <C extends Node<?>> void addChild(C child, Edge.EdgeTypes type, int index) {
        this.childrenEdges.add(index, new Edge(child, type));
    }

    public <C extends Node<?>> void addForwardChild(C child) {
        if (!child.isRoot()) { child.getForwardParent().removeChild(child); }
        addChild(child, Edge.EdgeTypes.FORWARD);
        child.setForwardParent(this);
    }

    public <C extends Node<?>> void addForwardChild(int index, C child) {
        if (!child.isRoot()) { child.getForwardParent().removeChild(child); }
        addChild(child, Edge.EdgeTypes.FORWARD, index);
        child.setForwardParent(this);
    }

    public <C extends Node<?>> void addBackwardChild(C child) {
        addChild(child, Edge.EdgeTypes.BACKWARD);
        child.addBackwardParent(this);
    }

    public <C extends Node<?>> void addBackwardChild(int index, C child) {
        addChild(child, Edge.EdgeTypes.BACKWARD, index);
        child.addBackwardParent(this);
    }

    protected <P extends Node<?>> void setForwardParent(P forwardParent) {
        this.forwardParent = forwardParent;
    }

    protected <P extends Node<?>> void addBackwardParent(P parent) {
        this.backwardParents.add(parent);
    }

    public <C extends Node<?>> void removeChild(C child) {
        if (this.childrenEdges.removeIf(e -> e.getNode().equals(child)))
            child.removeParent(this);
    }

    public <P extends Node<?>> void removeParent(P parent) {
        if (parent.equals(this.forwardParent))
            this.forwardParent = null;
        this.backwardParents.remove(parent);
    }

    public V getValue() { return value; }

    public void setValue(V value) { this.value = value; }

    public abstract String getLabel();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node<?> that = (Node<?>) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


    @Override
    public String toString() {
        return "digraph string_graph_node_" + this.id + " { " + String.join("", this.toStringAux()) + " }";
        //return getLabel();
    }

    public Set<String> toStringAux() {
        Set<String> result = new LinkedHashSet<>();
        result.add(this.id + " [label=\"" + this.getLabel() + "\"]\n");
        for (Node<?> child : this.getForwardChildren()) {
            result.addAll(child.toStringAux());
            result.add(this.id+ " -> " + child.id + "\n");
        }
        for (Node<?> child : this.getBackwardChildren()) {
            result.add(this.id + " -> " + child.id + " [style=dashed]\n");
        }
        return result;
    }

    public static Node<?> create(String value) {
        value = NormalStringGraphUtils.unquote(value);
        if (value.length() == 0)
            return new Const(ConstValues.EMPTY);
        if (value.length() == 1)
            return new Simple(value);
        return new Concat(value);
    }

    public abstract List<String> getDenotation();

    public boolean isFinite() {
        // If the current node is a leaf, it is finite: returns true
        if (this.isLeaf()) return true;
        else {
            // Otherwise the node itself and all child nodes must be checked not to have backward children
            if (this.getBackwardChildren().size() > 0) {
                return false;
            } else {
                boolean response = true;
                Iterator<Node<?>> i = this.getForwardChildren().iterator();
                while(response && i.hasNext()) {
                    Node<?> node = i.next();
                    response = node.isFinite();
                }
                return response;
            }
        }
    }

    public Set<Node<?>> getPrincipalNodes() {
        return Set.of(this);
    }

    public Set<String> getPrincipalLabels() {
        return getPrincipalNodes().stream()
                .map(Node::getLabel)
                .collect(Collectors.toSet());
    }

    public boolean isLessOrEqual(Node<?> other) {
        return NormalStringGraphUtils.partialOrderAux(this, other, new HashSet<>());
    }

    public int getDepth() {
        Node<?> n = this;
        int depth = 0;
        while(!n.isRoot()) {
            depth += 1;
            n = n.getForwardParent();
        }
        return depth;
    }

    public List<Node<?>> getAncestors() {
        Node<?> root = this;
        List<Node<?>> ancestors = new ArrayList<>();
        while(!root.isRoot()) {
            root = root.getForwardParent();
            ancestors.add(root);
        }
        return ancestors;
    }

    public boolean isProperAncestor(Node<?> descendant) {
        return NormalStringGraphUtils.getForwardPath(this, descendant).size() > 0;
    }

    public Set<Node<?>> is() {
        return this.is;
    }

    public Set<Node<?>> ris() {
        return this.is.stream().filter(n -> ConstValues.MAX != n.getValue()).collect(Collectors.toSet());
    }
}