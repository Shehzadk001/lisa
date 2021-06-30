package it.unive.lisa.nonrelational.impl;

import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.NormalStringGraphUtils;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.*;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.Const.ConstValues;
import org.junit.Test;
import java.util.List;

public class StringGraphTest {

    @Test
    public void testStringGraph() {
        Node<?> normalized = null;
        Node<?> concat1 = Node.create("ipsum");
        Node<?> concat2 = Node.create("lorem");
        Node<?> concat3 = Node.create("dolor");
        Node<?> concat4 = new Concat();
        concat4.addForwardChild(new Const(ConstValues.MAX));
        concat4.addForwardChild(new Const(ConstValues.MAX));
        Or root = new Or();
        root.addForwardChild(concat1);
        root.addForwardChild(concat2);
        concat2.addForwardChild(concat3);
        concat1.addBackwardChild(root);
        concat3.addForwardChild(new Const(ConstValues.MAX));
        System.out.println("!____Non Compact____!");
        System.out.println(NormalStringGraphUtils.compact(root));
        Node<?> result = NormalStringGraphUtils.compact(concat2);
        System.out.println(root);
        System.out.println("!____Compacted____!");
    }

    @Test
    public void testCompactionAlgorithm() {
        // case 1
        Node<?> node1 = new Or();
        node1.addForwardChild(new Const(ConstValues.MIN));
        node1.addForwardChild(new Const(ConstValues.MIN));
        System.out.println("!____NODE 1____!");
        System.out.println(node1);
        Node<?> result1 = NormalStringGraphUtils.compact(node1);
        System.out.println("!____NODE 1 COMPACTED____!");
        System.out.println(result1);
        assert result1 instanceof Const && result1.getValue().equals(ConstValues.MIN);

        // case 2
        Node<?> node2 = new Or();
        node2.addForwardChild(new Const(ConstValues.MIN));
        node2.addForwardChild(new Simple("a"));
        node2.addForwardChild(new Simple("b"));
        System.out.println("!____NODE 2____!");
        System.out.println(node2);
        Node<?> result2 = NormalStringGraphUtils.compact(node2);
        System.out.println("!____NODE 2 COMPACTED____!");
        System.out.println(result2);
        assert result2 instanceof Or &&
                result2.getOutDegree() == 2 &&
                result2.getForwardChildren().get(0).getValue().equals("a") &&
                result2.getForwardChildren().get(1).getValue().equals("b");

        // case 3
        Node<?> node3 = new Concat();
        Node<?> node3simple1 = new Simple("a");
        node3.addForwardChild(node3simple1);
        Node<?> node3or1 = new Or();
        node3.addForwardChild(node3or1);
        Node<?> node3or2 = new Or();
        Node<?> node3simple2 = new Simple("b");
        node3or1.addForwardChild(node3or2);
        node3or2.addBackwardChild(node3);
        Node<?> node3concat1 = new Concat();
        node3or2.addForwardChild(node3concat1);
        node3concat1.addBackwardChild(node3or1);
        node3concat1.addForwardChild(node3simple2);

        System.out.println("!____NODE 3____!");
        System.out.println(node3);
        System.out.println(node3.getDenotation());
        Node<?> result8 = NormalStringGraphUtils.compact(node3);
        System.out.println("!____NODE 3 COMPACTED____!");
        System.out.println(result8);
        System.out.println(result8.getDenotation());

    }

    @Test
    public void testGetForwardPath() {
        Node<?> node2 = new Or();
        Node<?> node2Concat = new Concat();
        node2.addForwardChild(new Const(ConstValues.MIN));
        Node<?> node2Simple = new Simple("a");
        node2Concat.addForwardChild(new Simple("a"));
        node2Concat.addForwardChild(new Simple("b"));
        node2.addForwardChild(node2Concat);
        List<Node<?>> forwardPath = NormalStringGraphUtils.getForwardPath(node2, node2Simple);
        System.out.println(forwardPath);
    }
}