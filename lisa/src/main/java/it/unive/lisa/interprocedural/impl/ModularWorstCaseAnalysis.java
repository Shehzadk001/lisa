package it.unive.lisa.interprocedural.impl;

import it.unive.lisa.analysis.*;
import it.unive.lisa.caches.Caches;
import it.unive.lisa.logging.IterationLogger;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.Parameter;
import it.unive.lisa.program.cfg.statement.CFGCall;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.heap.HeapReference;
import it.unive.lisa.symbolic.value.HeapIdentifier;
import it.unive.lisa.symbolic.value.PushAny;
import it.unive.lisa.symbolic.value.ValueIdentifier;
import it.unive.lisa.util.datastructures.graph.FixpointException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModularWorstCaseAnalysis<A extends AbstractState<A, H, V>,
        H extends HeapDomain<H>,
        V extends ValueDomain<V>>  extends CallGraphBasedInterproceduralAnalysis<A, H, V> {

    private static final Logger log = LogManager.getLogger(it.unive.lisa.interprocedural.impl.ModularWorstCaseAnalysis.class);


    /**
     * The cash of the fixpoints' results. {@link Map#keySet()} will contain all
     * the cfgs that have been added. If a key's values's
     * {@link Optional#isEmpty()} yields true, then the fixpoint for that key
     * has not be computed yet.
     */
    private final Map<CFG, Optional<CFGWithAnalysisResults<A, H, V>>> results;

    /**
     * Builds the call graph.
     */
    public ModularWorstCaseAnalysis() {
        this.results = new ConcurrentHashMap<>();
    }

    @Override
    public final void clear() {
        results.clear();
    }


    @Override
    public final void fixpoint(
            AnalysisState<A, H, V> entryState)
            throws FixpointException {
        for (CFG cfg : IterationLogger.iterate(log, program.getAllCFGs(), "Computing fixpoint over the whole program",
                "cfgs"))
            try {
                results.put(cfg, Optional.of(cfg.fixpoint(prepareEntryStateOfEntryPoint(entryState, cfg), this)));
            } catch (SemanticException e) {
                throw new FixpointException("Error while creating the entrystate for " + cfg, e);
            }
    }



    @Override
    @SuppressWarnings("unchecked")
    public final Collection<CFGWithAnalysisResults<A, H, V>> getAnalysisResultsOf(
            CFG cfg) {
        return Collections.singleton(results.get(cfg).orElse(null));
    }

    @Override
    public final AnalysisState<A, H, V> getAbstractResultOf(CFGCall call, AnalysisState<A, H, V> entryState, Collection<SymbolicExpression>[] parameters)
            throws SemanticException {
        if (call.getStaticType().isVoidType())
            return entryState.top();

        return entryState.top().smallStepSemantics(new ValueIdentifier(call.getRuntimeTypes(), "ret_value"), call);
    }
}