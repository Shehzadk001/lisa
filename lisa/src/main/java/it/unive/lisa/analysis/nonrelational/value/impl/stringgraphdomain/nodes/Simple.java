package it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes;

import java.util.List;

public class Simple extends Node<String> {

    public Simple() {
        super();
    }

    public Simple(String value) {
        this();
        if (value.length() != 1)
            throw new IllegalArgumentException("Value of SimpleStringGraphNode must be of length 1");

        this.value = value;
    }

    public Character getValueAsChar() {
        return getValue().charAt(0);
    }

    @Override
    public <C extends Node<?>> void addForwardChild(C child) {
        //throw new UnsupportedOperationException("Cannot add forward children to " + this.getClass().getName());
    }

    @Override
    public <C extends Node<?>> void removeChild(C child) {
        //throw new UnsupportedOperationException("Cannot remove child from " + this.getClass().getName());
    }

    @Override
    public List<String> getDenotation() {
        return List.of(this.value);
    }


    @Override
    public String getLabel() {
        return this.getValue();
    }
}