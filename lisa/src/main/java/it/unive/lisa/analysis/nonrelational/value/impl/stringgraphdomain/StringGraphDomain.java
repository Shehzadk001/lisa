package it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain;

import it.unive.lisa.analysis.SemanticDomain;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.nonrelational.value.BaseNonRelationalValueDomain;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.*;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.Const.ConstValues;
import it.unive.lisa.program.cfg.ProgramPoint;
import it.unive.lisa.symbolic.value.BinaryOperator;
import it.unive.lisa.symbolic.value.Constant;
import java.util.*;

public class StringGraphDomain extends BaseNonRelationalValueDomain<StringGraphDomain> {

    private final Node<?> root;
    private final static StringGraphDomain TOP = new StringGraphDomain(new Const(ConstValues.MAX));
    private final static StringGraphDomain BOTTOM = new StringGraphDomain(new Const(ConstValues.MIN));

    public StringGraphDomain() {
        this(new Const(ConstValues.MAX));
    }

    public StringGraphDomain(Node<?> root) {
        this.root = root;
    }

    @Override
    public String representation() {
        return this.root.toString();
    }

    @Override
    public StringGraphDomain top() {
        return TOP;
    }

    @Override
    public StringGraphDomain bottom() {
        return BOTTOM;
    }

    @Override
    protected StringGraphDomain evalNonNullConstant(Constant constant, ProgramPoint pp) {
        if (constant.getValue() instanceof String) {
            String value = (String) constant.getValue();
            return new StringGraphDomain(Node.create(value));
        }
        return top();
    }

    @Override
    protected StringGraphDomain evalBinaryExpression(BinaryOperator operator, StringGraphDomain left,
                                                     StringGraphDomain right, ProgramPoint pp) {
        if (BinaryOperator.STRING_CONCAT == operator) {
            Node<?> concatNode = new Concat();
            concatNode.addForwardChild(left.root);
            concatNode.addForwardChild(right.root);
            return new StringGraphDomain(NormalStringGraphUtils.normalize(concatNode));
        }
        return top();
    }

    @Override
    protected StringGraphDomain lubAux(StringGraphDomain other) throws SemanticException {
        // Section 4.4.3
        Node<?> orNode = new Or();
        orNode.addForwardChild(this.root);
        orNode.addForwardChild(other.root);
        Node<?> result = NormalStringGraphUtils.normalize(orNode);
        return new StringGraphDomain(result);
    }

    @Override
    public StringGraphDomain glbAux(StringGraphDomain other) {

        // Since the expected denotation should be an intersection (hence a list), the node must be an OR node
        Node<?> l0 = new Or();
        Set<Node<?>> S_sn = new LinkedHashSet<>();
        Set<Node<?>> S_ul = new LinkedHashSet<>();
        S_ul.add(l0);
        l0.is().add(this.root);
        l0.is().add(other.root);

        // REPEAT-UNTIL
        do {
            // select random l from S_ul
            Node<?> l = S_ul.iterator().next();
            // RULE 1
            if (!S_sn.isEmpty()) {
                Node<?> lp = l.getForwardParent();
                // check if exists an OR Node from a generic lg in S_sn to l.parent
                Node<?> lg = null;
                for (Node<?> _lg : S_sn) {
                    List<Node<?>> path = NormalStringGraphUtils.getForwardPath(_lg, lp);
                    if (path.stream().anyMatch(p -> !(p instanceof Or))) {
                        lg = _lg;
                        break;
                    }
                }

                if (lg != null) {
                    S_ul.remove(l);
                    lp.addBackwardChild(lg);
                    lp.removeChild(l);
                    continue;
                }
            }

            // RULE 2
            if (l.ris().isEmpty()) {
                Const maxNode = new Const(ConstValues.MAX);
                NormalStringGraphUtils.replace(l, maxNode);
                S_ul.remove(l);
                S_sn.add(maxNode);
                continue;
            }

            // RULE 3
            if (l.ris().stream().allMatch(n -> n instanceof Or)) {
                Or orNode = new Or();
                NormalStringGraphUtils.replace(l, orNode);
                S_ul.remove(l);
                S_sn.add(orNode);

                Or m = (Or) l.ris().iterator().next();
                Set<Node<?>> ris_no_m = new HashSet<>(l.ris());
                ris_no_m.remove(m);

                for (int idx = 0; idx < m.getForwardChildren().size(); idx++) {
                    Node<?> mi = m.getForwardChildren().get(idx);
                    Node<?> li = new Or();
                    li.is().add(mi);
                    li.is().addAll(ris_no_m);
                    S_ul.add(li);
                    l.addForwardChild(li);
                }
                continue;
            }

            // RULE 4
            Optional<Node<?>> opt = l.is().stream().filter(x -> x instanceof Concat || x instanceof Simple).findAny();
            if (opt.isPresent()) {
                Node<?> n = opt.get();

                boolean labelFound = l.ris().stream().filter(x -> !x.equals(n)).allMatch(m ->
                        m.getPrincipalNodes().stream().anyMatch(md -> md.getLabel().equals(n.getLabel()))
                );

                if (labelFound) {
                    NormalStringGraphUtils.replace(l, n);
                    S_ul.remove(l);
                    S_sn.add(n);

                    if (n instanceof Concat) {

                        final Set<Node<?>> S_md = new HashSet<>();
                        l.ris().stream().filter(x -> !x.equals(n)).forEach(m ->
                                m.getPrincipalNodes().stream().filter(md -> md.getLabel().equals(n.getLabel())).findAny().ifPresent(S_md::add)
                        );

                        for (int idx = 0; idx < n.getForwardChildren().size(); idx++) {
                            Node<?> ni = n.getForwardChildren().get(idx);
                            Node<?> li = new Or();
                            li.is().add(ni);
                            l.addForwardChild(li);
                            int finalIdx = idx;
                            S_md.forEach(m ->  li.is().add(m.getForwardChildren().get(finalIdx)));
                            S_ul.add(li);
                        }
                    }
                } else {
                    Const minNode = new Const(ConstValues.MIN);
                    NormalStringGraphUtils.replace(l, minNode);
                    S_ul.remove(l);
                    S_sn.add(minNode);
                }
            }
        } while (!S_ul.isEmpty());

        return new StringGraphDomain(NormalStringGraphUtils.compact(l0));
    }

    @Override
    protected StringGraphDomain wideningAux(StringGraphDomain other) throws SemanticException {
        Or tmp = new Or();
        tmp.addForwardChild(this.root);
        tmp.addForwardChild(other.root);
        Node<?> gn = NormalStringGraphUtils.normalize(tmp);
        Node<?> widened = NormalStringGraphUtils.normalize(NormalStringGraphUtils.widening(this.root, gn));
        return new StringGraphDomain(widened);
    }

    @Override
    protected boolean lessOrEqualAux(StringGraphDomain other) throws SemanticException {
        return this.root.isLessOrEqual(other.root);
    }

    @Override
    protected SemanticDomain.Satisfiability satisfiesBinaryExpression(BinaryOperator operator, StringGraphDomain left, StringGraphDomain right, ProgramPoint pp) {
        if (BinaryOperator.STRING_CONTAINS == operator) {
            // 4.4.6
            // checking only for a single character
            Simple simpleGraphNode = NormalStringGraphUtils.getSingleCharacterString(right.root);
            if (simpleGraphNode != null) {
                Character c = simpleGraphNode.getValueAsChar();

                // check if "true" condition of Section 4.4.6 - Table VII holds
                if (NormalStringGraphUtils.strContainsAux_checkTrue(left.root, c)) {
                    return SemanticDomain.Satisfiability.SATISFIED;
                }

                // checks if "false" condition of Section 4.4.6 - Table VII holds
                if (NormalStringGraphUtils.strContainsAux_checkFalse(left.root, c)) {
                    return SemanticDomain.Satisfiability.NOT_SATISFIED;
                }

                return SemanticDomain.Satisfiability.UNKNOWN;
            }
        }

        return SemanticDomain.Satisfiability.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringGraphDomain that = (StringGraphDomain) o;
        return Objects.equals(root, that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(root);
    }
}