package it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain;

import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.*;
import it.unive.lisa.analysis.nonrelational.value.impl.stringgraphdomain.nodes.Const.ConstValues;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;


public abstract class NormalStringGraphUtils {


    public static Node<?> compact(Node<?> node) {
        if (node == null)
            return null;

        Node<?> newNode = deepClone(node);
        if (newNode != null) {
            int hash;
            do {
                hash = newNode.hashCode();
                newNode = compactAux(newNode);
            } while (newNode != null && !(newNode.hashCode() == hash));
            return newNode;
        } else {
            return null;
        }
    }

    private static Node<?> compactAux(Node<?> node) {
        // The algorithm is bottom up, starting from the leaves, so it recursivelly gets to the leaves first
        List<Node<?>> children = new ArrayList<>(node.getForwardChildren());
        for (Node<?> child : children) {
            Node<?> newChild = compactAux(child);
            if (newChild != child) {
                node.removeChild(child);
                if (newChild != null) {
                    node.addForwardChild(newChild);
                }
            }
        }

        // After compacting all leaves, the node itself can be compacted

        // Rule 1
        if ((node instanceof Or || node instanceof Concat) && node.getDenotation().size() == 0) {
            removeProperDescendants(node);
            return new Const(ConstValues.MIN);
        }

        // Rule 4
        if (node instanceof Or && node.getOutDegree() == 0)
            return new Const(ConstValues.MIN);

        // Rule 5
        if (node instanceof Or && node.getForwardChildren().stream().anyMatch(c -> c.getValue() == ConstValues.MAX)) {
            removeProperDescendants(node);
            return new Const(ConstValues.MAX);
        }

        // Rule 7
        if (node instanceof Or && node.getOutDegree() == 1) {
            Node<?> child = node.getBackwardChildren().size() == 1 ? node.getBackwardChildren().get(0) : node.getForwardChildren().get(0);
            Node<?> forwardParent = node.getForwardParent();
            List<Node<?>> backwardParents = new ArrayList<>(node.getBackwardParents());
            node.removeChild(child);

            for (Node<?> parent : backwardParents) {
                parent.removeChild(node);
                int nodeIndex = parent.getChildren().indexOf(node);
                parent.addBackwardChild(nodeIndex, child);
            }

            if (forwardParent != null) {
                forwardParent.removeChild(node);
                forwardParent.addForwardChild(child);
                return null;
            } else {
                return child;
            }
        }

        // Rule 8
        if (node.getBackwardChildren().size() > 0) {
            List<Node<?>> backwardChildren = new ArrayList<>(node.getBackwardChildren());
            for (Node<?> child : backwardChildren) {
                List<Node<?>> forwardPath = getForwardPath(child, node);
                if (forwardPath.size() > 0 && forwardPath.stream().allMatch(k -> k instanceof Or)) {
                    for (Node<?> k : forwardPath) {
                        List<Node<?>> kBackwardParents = new ArrayList<>(k.getBackwardParents());
                        for (Node<?> l : kBackwardParents) {
                            int kIndex = l.getChildren().indexOf(k);
                            l.removeChild(k);
                            l.addBackwardChild(kIndex, child);
                        }
                    }
                    return node;
                }
            }
        }

        // Specific rules for or nodes
        if (node instanceof Or) {
            // Rule 2
            List<Node<?>> toBeRemoved = node.getForwardChildren().stream()
                    .filter(c -> c.getValue() == ConstValues.MIN)
                    .collect(Collectors.toList());
            for (Node<?> child : toBeRemoved)
                node.removeChild(child);

            // Rule 3
            if (node.getForwardChildren().contains(node) || node.getBackwardChildren().contains(node)) {
                node.removeChild(node);
            }

            // Rule 6
            toBeRemoved = node.getForwardChildren().stream()
                    .filter(c -> (c instanceof Or && c.getInDegree() == 1))
                    .collect(Collectors.toList());

            for (Node<?> child : toBeRemoved) {
                node.removeChild(child);
                List<Node<?>> childForwardChildren = new ArrayList<>(child.getForwardChildren());
                List<Node<?>> childBackwardChildren = new ArrayList<>(child.getBackwardChildren());
                for (Node<?> c : childForwardChildren) {
                    child.removeChild(c);
                    node.addForwardChild(c);
                }
                for (Node<?> c : childBackwardChildren) {
                    child.removeChild(c);
                    node.addForwardChild(c);
                }
            }

            return node;
        }

        // Additional rules for concat nodes, as explained in the java doc of this function
        if (node instanceof Concat) {
            // Rule 1 from paper A suite of abstract domains for static analysis of string values - 4.4.1
            if (node.getOutDegree() == 1) {
                Node<?> newNode = node.getChildren().get(0);
                List<Node<?>> nodeBackwardParents = new ArrayList<>(node.getBackwardParents());
                for (Node<?> parent : nodeBackwardParents) {
                    int nodeIndex = parent.getChildren().indexOf(node);
                    parent.removeChild(node);
                    parent.addBackwardChild(nodeIndex, newNode);
                }
                node.removeChild(newNode);
                return newNode;
            }

            // Rule 2 from paper A suite of abstract domains for static analysis of string values - 4.4.1
            if (
                    node.getBackwardChildren().isEmpty() &&
                            node.getForwardChildren().stream().allMatch(n -> n instanceof Const && n.getValue().equals(ConstValues.MAX))
            ) {
                return new Const(ConstValues.MAX);
            }

            // Rule 3/4 from paper A suite of abstract domains for static analysis of string values - 4.4.1
            int index = 0;
            while (index < node.getChildren().size()) {
                Node<?> child = node.getChildren().get(index);
                if (child instanceof Concat && child.getInDegree() <= 1) {
                    while (child.getChildren().size() > 0) {
                        Node<?> c = child.getChildren().get(0);
                        node.addForwardChild(index, c);
                        child.removeChild(c);
                        index += 1;
                    }
                    node.removeChild(child);
                } else {
                    index += 1;
                }
            }
        }

        return node;
    }

    private static void removeProperDescendants(Node<?> node) {
        List<Node<?>> toBeRemoved = new ArrayList<>(node.getForwardChildren());
        for (Node<?> child : toBeRemoved) {
            // remove child's children only if child does not have other parents
            if (child.getInDegree() == 1)
                removeProperDescendants(child);
            node.removeChild(child);
        }
    }

    public static Node<?> deepClone(Node<?> node) {
        try {
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
            objectOutputStream.writeObject(node);
            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteOutputStream.toByteArray());
            ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
            return (Node<?>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException ignored) {
            return null;
        }
    }

    public static List<Node<?>> getForwardPath(Node<?> parent, Node<?> child) {
        Node<?> node = child;
        List<Node<?>> path = new ArrayList<>();
        while (!node.isRoot() && !node.equals(parent)) {
            path.add(node);
            node = node.getForwardParent();
        }
        return node.equals(parent) ? path : new ArrayList<>();
    }

    //==================================== DOMAIN UTILS SECTION =======================================================


    public static boolean partialOrderAux(Node<?> n, Node<?> m, Set<Map.Entry<Node<?>, Node<?>>> edges) {

        // case (1)
        if (edges.contains(Node.createEdge(n, m)))
            return true;

            // case (2)
        else if (m instanceof Const) {
            ConstValues constValue = ((Const)m).getValue();
            if (ConstValues.MAX == constValue) {
                return true;
            }
        }

        // case (3)
        else if (n instanceof Concat && m instanceof Concat) {
            Concat concatN = (Concat)n;
            Concat concatM = (Concat)m;
            List<Node<?>> childrenN = n.getChildren();
            List<Node<?>> childrenM = m.getChildren();
            if (Objects.equals(concatN.getValue(), concatM.getValue())) {
                // add current edge to edgeSet
                edges.add(Node.createEdge(n, m));
                // for each i in [0,k] must hold: <=(n/i, m/i, edges+{m,n})
                for (int i=0; i<concatN.getValue(); i++) {
                    boolean isLessOrEqual = partialOrderAux(childrenN.get(i), childrenM.get(i), edges);
                    if (!isLessOrEqual) return false;
                }
                return true;
            }
        }
        // case (4)
        else if (n instanceof Or && m instanceof Or) {
            int k = n.getOutDegree();
            List<Node<?>> children = n.getChildren();
            // add current edge to edgeSet
            edges.add(Node.createEdge(n, m));
            // for each i in [0,k] must hold: <=(n/i, m, edges+{m,n})
            for (int i=0; i<k; i++) {
                boolean isLessOrEqual = partialOrderAux(children.get(i), m, edges);
                if (!isLessOrEqual) return false;
            }
            return true;
        }

        // case (5)
        else if (m instanceof Or) {
            Node<?> md = null;
            // look for a node (named md) in prnd(m) such that lb(n) == lb(md)
            for (Node<?> prnd: m.getPrincipalNodes()) {
                String prndLbl = prnd.getLabel();
                if ( (prndLbl == null && n.getLabel() == null) ||
                        (prndLbl != null && prnd.getLabel().equals(n.getLabel()))) {
                    md = prnd; // found one
                    break;
                }
            }

            if (md != null) {
                edges.add(Node.createEdge(n, m));
                return partialOrderAux(n, md, edges);
            }
        }
        // case (6)
        return (n.getLabel().equals( m.getLabel() ));
    }



    public static boolean strContainsAux_checkTrue(Node<?> node, Character c) {

        if (node instanceof Simple)
            return ((Simple) node).getValueAsChar().equals(c);

        // do not check OR nodes here! At first iteration, left is the root, which can be an OR node!
        List<Node<?>> children = node.getChildren();
        for (Node<?> child: children) {
            if (child instanceof Or) continue;
            if (child instanceof Simple)
                if (strContainsAux_checkTrue(child, c))
                    return true;
            if (child instanceof Concat) {
                Integer k = ((Concat)child).getValue();
                for (int i=0; i<k; i++)
                    if (strContainsAux_checkTrue(child, c))
                        return true;
            }
        }

        return false;
    }


    public static boolean strContainsAux_checkFalse(Node<?> node, Character c) {

        if (node instanceof Const)
            return !ConstValues.MAX.equals( ((Const)node).getValue() );
        if (node instanceof Simple)
            return !((Simple) node).getValueAsChar().equals(c);

        // for all children must hold that: no child is c or MAX
        for (Node<?> child: node.getChildren())
            if (!strContainsAux_checkFalse(child, c))
                return false;

        // survived the check
        return true;
    }

    //==================================== UTILS SECTION ==============================================================


    public static Simple getSingleCharacterString(Node<?> node) {
        if (node instanceof Simple)
            return (Simple)node;
        if (node instanceof Concat) {
            List<Node<?>> children = node.getChildren();
            if (node.getOutDegree() == 3
                    && "\"".equals(children.get(0).getValue())
                    && "\"".equals(children.get(2).getValue()))
                return getSingleCharacterString(children.get(1));
            if (node.getOutDegree() == 1)
                return getSingleCharacterString(children.get(0));
        }
        return null;
    }


    public static String unquote(String value) {
        if (value == null)
            return "";
        if (value.startsWith("\""))
            value = value.substring(1);
        if (value.endsWith("\""))
            value = value.substring(0, value.length()-1);
        return value;
    }

    public static void replace(Node<?> original, Node<?> replacement) {
        if (!(original == replacement)) {
            List<Edge> originalEdges = new ArrayList<>(original.getChildrenEdges());
            List<Node<?>> originalBackwardParents = new ArrayList<>(original.getBackwardParents());
            for (Edge e : originalEdges) {
                original.removeChild(e.getNode());
            }
            for (Node<?> backwardParent : originalBackwardParents) {
                int originalIndex = backwardParent.getChildren().indexOf(original);
                backwardParent.addBackwardChild(originalIndex, replacement);
                backwardParent.removeChild(original);
            }
            if (!original.isRoot()) {
                Node<?> forwardParent = original.getForwardParent();
                int originalIndex = forwardParent.getChildren().indexOf(original);
                forwardParent.addForwardChild(originalIndex, replacement);
                forwardParent.removeChild(original);
            }
        }
    }

    //==================================== NORMALIZATION SECTION ======================================================


    public static Node<?> normalize(Node<?> node) {
        try {
            /* INITIALIZATION */
            HashMap<String, Set<Node<?>>> idToNfr = new HashMap<>();
            HashMap<String, Set<Node<?>>> idToNd = new HashMap<>();
            Node<?> m0 = initializeNodeFromSource(node);
            Set<Node<?>> S_ul = new LinkedHashSet<>();
            Set<Node<?>> S_sn = new LinkedHashSet<>();
            S_ul.add(m0);

            if (m0 instanceof Or)
                idToNfr.put(m0.id, new LinkedHashSet<>(node.getChildren()));
            else
                idToNfr.put(m0.id, new LinkedHashSet<>());
            idToNd.put(m0.id, new LinkedHashSet<>(idToNfr.get(m0.id)));
            idToNd.get(m0.id).add(node);

            /* REPEAT UNTIL */
            do {
                Node<?> m = S_ul.iterator().next();

                Optional<Node<?>> mgOpt = S_sn.stream().filter(_mg -> safeAnc(m, idToNd, _mg)).findFirst();
                if (mgOpt.isPresent()) {
                    // CASE 1
                    Node<?> mg = mgOpt.get();
                    ulBarc(m, mg, S_ul);
                } else if (m instanceof Simple
                        || (m instanceof Concat
                        && ((Concat)m).getValue() == 0
                        && ((Concat)m).desiredNumberOfChildren == 0)) {
                    // CASE 2
                    S_ul.remove(m);
                    S_sn.add(m);
                } else if (m instanceof Or) {
                    // CASE 3
                    Optional<Node<?>> nOpt = idToNfr.get(m.id).stream().filter(_n -> involvedOverlap(m,_n, idToNfr)).findFirst();
                    while(nOpt.isPresent()) {
                        Node<?> n = nOpt.get();
                        idToNfr.get(m.id).remove(n);
                        idToNfr.get(m.id).addAll(n.getChildren().stream().filter(k -> !idToNd.get(m.id).contains(k)).collect(Collectors.toSet()));
                        idToNd.get(m.id).addAll(n.getChildren());
                        nOpt = idToNfr.get(m.id).stream().filter(_n -> involvedOverlap(m,_n, idToNfr)).findFirst();
                    }
                    mgOpt = S_sn.stream().filter(_mg -> safeAnc(m, idToNd, _mg)).findFirst();
                    if (mgOpt.isPresent()) {
                        ulBarc(m, mgOpt.get(), S_ul);
                    } else {
                        for (Node<?> n : idToNfr.get(m.id).stream().filter(_n -> _n instanceof Or).collect(Collectors.toList())) {
                            Node<?> newChild = new Or();
                            m.addForwardChild(newChild);
                            idToNfr.put(newChild.id, new LinkedHashSet<>(n.getChildren()));
                            idToNd.put(newChild.id, new LinkedHashSet<>(n.getChildren()));
                            idToNd.get(newChild.id).add(n);
                        }
                        Set<String> labels = idToNfr
                                .get(m.id)
                                .stream()
                                .filter(_n -> _n instanceof Concat || _n instanceof Simple)
                                .map(Node::getLabel)
                                .collect(Collectors.toSet());
                        for (String l : labels) {
                            Set<Node<?>> sameLabel = idToNfr.get(m.id).stream().filter(_n -> _n.getLabel().equals(l)).collect(Collectors.toSet());
                            Node<?> newChild = initializeNodeFromSource(sameLabel.iterator().next());
                            m.addForwardChild(newChild);
                            idToNd.put(newChild.id, sameLabel);
                            idToNfr.put(newChild.id, new LinkedHashSet<>());
                        }
                        S_ul.remove(m);
                        S_ul.addAll(m.getChildren());
                        S_sn.add(m);
                    }
                } else if (m instanceof Concat) {
                    // CASE 4
                    if (idToNd.get(m.id).size() == 1) {
                        Node<?> n = idToNd.get(m.id).iterator().next();
                        for (int i = 0; i < ((Concat) m).desiredNumberOfChildren; i++) {
                            Node<?> child = n.getChildren().get(i);
                            Node<?> newChild = initializeNodeFromSource(child);
                            m.addForwardChild(newChild);
                            if (newChild instanceof Or) {
                                idToNfr.put(newChild.id, new LinkedHashSet<>(child.getChildren()));
                            } else {
                                idToNfr.put(newChild.id, new LinkedHashSet<>());
                            }
                            idToNd.put(newChild.id,  new LinkedHashSet<>(idToNfr.get(newChild.id)));
                            idToNd.get(newChild.id).add(child);
                        }
                    } else {
                        for (int i = 0; i < ((Concat) m).desiredNumberOfChildren; i++) {
                            int finalI = i;
                            Set<Node<?>> iThChildren = idToNd
                                    .get(m.id)
                                    .stream()
                                    .filter(_n -> _n.getOutDegree() > finalI)
                                    .map(_n -> (_n).getChildren().get(finalI))
                                    .collect(Collectors.toSet());
                            if (iThChildren.stream().anyMatch(_i -> _i instanceof Const && _i.getValue().equals(ConstValues.MAX))) {
                                Node<?> newChild = new Const(ConstValues.MAX);
                                m.addForwardChild(newChild);
                                idToNd.put(newChild.id, new LinkedHashSet<>(iThChildren));
                            } else {
                                Node<?> newChild = new Or();
                                m.addForwardChild(newChild);
                                idToNfr.put(newChild.id, new LinkedHashSet<>(iThChildren));
                                idToNd.put(newChild.id, new LinkedHashSet<>(iThChildren));
                            }
                        }
                    }
                    S_ul.remove(m);
                    S_ul.addAll(m.getChildren());
                    S_sn.add(m);
                }

            } while(S_ul.size() > 0);

            return compact(m0);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return null;
        }
    }

    /* AUXILIARY FUNCTIONS FOR NORMALIZATION ALGORITHM */

    private static boolean safeAnc(Node<?> m, HashMap<String, Set<Node<?>>> fn, Node<?> mg) {
        List<Node<?>> mgToMPath = getForwardPath(mg,m);
        if (mgToMPath.size() > 0) {
            mgToMPath.add(mg);
            mgToMPath.remove(0);
        }
        Set<Node> intersection = new HashSet<>(fn.get(mg.id));
        intersection.retainAll(fn.get(m.id));
        return mg.isProperAncestor(m)
                && (intersection.size() == fn.get(m.id).size() && intersection.size() == fn.get(mg.id).size())
                && mgToMPath.stream().anyMatch(mf -> !(mf instanceof Or));
    }

    private static boolean involvedOverlap(Node<?> m, Node<?> n, HashMap<String, Set<Node<?>>> nfr) {
        Set<String> nPrincipalLabels = n.getPrincipalLabels();
        Set<String> otherPrincipalLabels = nfr.get(m.id).stream().filter(k -> !k.equals(n)).flatMap(k -> k.getPrincipalLabels().stream()).collect(Collectors.toSet());
        Set<String> intersection = new HashSet<>(nPrincipalLabels);
        intersection.retainAll(otherPrincipalLabels);
        return nfr.get(m.id).contains(n) && n instanceof Or && !(intersection.isEmpty());
    }

    private static void ulBarc(Node<?> m, Node<?> mg, Set<Node<?>> S_ul) {
        S_ul.remove(m);
        Node<?> mParent = m.getForwardParent();
        int mIndex = mParent.getChildren().indexOf(m);
        mParent.removeChild(m);
        mParent.addBackwardChild(mIndex, mg);
    }

    private static <T> Node<T> initializeNodeFromSource(Node<T> source)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Node<T> newNode = (Node<T>) source.getClass().getDeclaredConstructor().newInstance();
        if (source instanceof Concat) {
            Concat result = ((Concat)newNode);
            result.desiredNumberOfChildren = (int)source.getValue();
            return (Node<T>) result;
        } else {
            newNode.setValue(source.getValue());
            return newNode;
        }
    }

    //========================================= WIDENING SECTION ======================================================


    public static Node<?> widening(Node<?> go, Node<?> gn) {
        if (gn != null) {
            /* CYCLE INTRODUCTION RULE */
            Iterator<List<Node<?>>> CIIterator = CI(go, gn).iterator();
            if (CIIterator.hasNext()) {
                List<Node<?>> CIEl = CIIterator.next();
                Node<?> v = CIEl.get(0);
                Node<?> vn = CIEl.get(1);
                Node<?> va = CIEl.get(3);
                int vnIndex = v.getChildren().indexOf(vn);
                v.removeChild(vn);
                v.addBackwardChild(vnIndex, va);
                return gn;
            } else {
                /* REPLACEMENT RULE */
                Iterator<List<Node<?>>> CRIterator = CR(go, gn).iterator();
                if (CRIterator.hasNext()) {
                    List<Node<?>> CREl = CRIterator.next();
                    Node<?> vn = CREl.get(0);
                    Node<?> va = CREl.get(1);
                    replace(va, vn);
                    return gn;
                } else {
                    return gn;
                }
            }
        }
        return go;
    }

    /* AUXILIARY FUNCTIONS FOR WIDENING ALGORITHM */

    private static Set<List<Node<?>>> correspondenceSet(Node<?> g1, Node<?> g2) {
        return correspondenceSetAux(g1, g2);
    }

    private static Set<List<Node<?>>> correspondenceSetAux(Node<?> g1, Node<?> g2) {
        Set<List<Node<?>>> relation = new HashSet<>();
        relation.add(List.of(g1,g2));
        if (eDepth(g1,g2) || ePf(g1,g2)) {
            Iterator<Node<?>> v1Iterator = g1.getForwardChildren().iterator();
            Iterator<Node<?>> v2Iterator = g2.getForwardChildren().iterator();
            while(v1Iterator.hasNext() && v2Iterator.hasNext()) {
                relation.addAll(correspondenceSetAux(v1Iterator.next(), v2Iterator.next()));
            }
        }
        return relation;
    }

    private static Set<List<Node<?>>> topologicalClashes(Node<?> g1, Node<?> g2) {
        if (!eDepth(g1, g2) && ePf(g1, g2)) {
            return correspondenceSet(g1, g2);
        }
        return new HashSet<>();
    }

    private static Set<List<Node<?>>> wideningTopologicalClashes(Node<?> g1, Node<?> g2) {
        return topologicalClashes(g1, g2)
                .stream()
                .filter(pair -> {
                    Node<?> v = pair.get(0);
                    Node<?> _v = pair.get(1);
                    return (!(_v instanceof Simple || _v instanceof Const))
                            && (!ePf(v,_v) || v.getDepth() < _v.getDepth());
                })
                .collect(Collectors.toSet());
    }

    private static boolean eDepth(Node<?> v1, Node<?> v2) {
        return v1.getDepth() == v2.getDepth();
    }

    private static boolean ePf(Node<?> v1, Node<?> v2) {
        Set<String> v1PrincipalFunctors = principalFunctors(v1);
        Set<String> v2PrincipalFunctors = principalFunctors(v2);
        Set<String> intersection = new HashSet<>(v1PrincipalFunctors);
        intersection.retainAll(v2PrincipalFunctors);
        return intersection.size() == v1PrincipalFunctors.size() && v1PrincipalFunctors.size() == v2PrincipalFunctors.size();
    }

    private static Set<String> principalFunctors(Node<?> v) {
        return v
                .getForwardChildren()
                .stream()
                .filter(_v -> _v instanceof Concat)
                .map(Node::getLabel)
                .collect(Collectors.toSet());
    }

    private static List<Node<?>> ca(Node<?> v, Node<?> v1, Set<List<Node<?>>> C) {
        Optional<List<Node<?>>> caOpt = C
                .stream()
                .filter(pair -> {
                    Node<?> _va = pair.get(0);
                    Node<?> _va1 = pair.get(1);
                    return _va.getForwardChildren().contains(v) && _va1.getForwardChildren().contains(v1);
                })
                .findFirst();
        return caOpt.orElseGet(ArrayList::new);
    }

    private static Set<List<Node<?>>> CI(Node<?> go, Node<?> gn) {
        Set<List<Node<?>>> CI = new HashSet<>();
        for (List<Node<?>> pair : wideningTopologicalClashes(go, gn)) {
            List<Node<?>> element = new ArrayList<>();
            Node<?> vo = pair.get(0);
            Node<?> vn = pair.get(1);
            Optional<Node<?>> vaOpt = vn
                    .getAncestors()
                    .stream()
                    .filter(_va -> vn.isLessOrEqual(_va) && vo.getDepth() >= _va.getDepth())
                    .findFirst();
            if (vaOpt.isPresent()) {
                Node<?> va = vaOpt.get();
                List<Node<?>> ca = ca(vo, vn, correspondenceSet(go, gn));
                if (ca.size() == 2) {
                    Node<?> v = ca.get(1);
                    element.add(v);
                    element.add(vn);
                    element.add(v);
                    element.add(va);
                    CI.add(element);
                }
            }
        }
        return CI;
    }

    private static Set<List<Node<?>>> CR(Node<?> go, Node<?> gn) {
        Set<List<Node<?>>> CR = new HashSet<>();
        for (List<Node<?>> pair : wideningTopologicalClashes(go, gn)) {
            List<Node<?>> element = new ArrayList<>();
            Node<?> vo = pair.get(0);
            Node<?> vn = pair.get(1);
            Optional<Node<?>> vaOpt = vn
                    .getAncestors()
                    .stream()
                    .filter(_va -> !vn.isLessOrEqual(_va)
                            && principalFunctors(_va).containsAll(principalFunctors(vn))
                            && vo.getDepth() >= _va.getDepth())
                    .findFirst();
            if (vaOpt.isPresent()) {
                Node<?> va = vaOpt.get();
                element.add(vn);
                element.add(va);
                CR.add(element);
            }
        }
        return CR;
    }
}