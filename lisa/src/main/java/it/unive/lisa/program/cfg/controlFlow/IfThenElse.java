package it.unive.lisa.program.cfg.controlFlow;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.edge.Edge;
import it.unive.lisa.program.cfg.edge.FalseEdge;
import it.unive.lisa.program.cfg.edge.SequentialEdge;
import it.unive.lisa.program.cfg.edge.TrueEdge;
import it.unive.lisa.program.cfg.statement.NoOp;
import it.unive.lisa.program.cfg.statement.Statement;
import it.unive.lisa.util.datastructures.graph.AdjacencyMatrix;

public class IfThenElse extends ControlFlowStructure {

	private final AdjacencyMatrix<Statement, Edge, CFG> trueBranch;

	private final AdjacencyMatrix<Statement, Edge, CFG> falseBranch;

	public IfThenElse(Statement condition, Statement firstFollower, AdjacencyMatrix<Statement, Edge, CFG> trueBranch,
			AdjacencyMatrix<Statement, Edge, CFG> falseBranch) {
		super(condition, firstFollower);
		this.trueBranch = trueBranch;
		this.falseBranch = falseBranch;
	}

	public AdjacencyMatrix<Statement, Edge, CFG> getTrueBranch() {
		return trueBranch;
	}

	public AdjacencyMatrix<Statement, Edge, CFG> getFalseBranch() {
		return falseBranch;
	}

	@Override
	public boolean contains(Statement st) {
		return trueBranch.containsNode(st, true) || falseBranch.containsNode(st, true);
	}

	@Override
	public int distance(Statement st) {
		return Math.min(distanceAux(trueBranch, st), distanceAux(falseBranch, st));
	}

	@Override
	public void simplify() {
		Set<Statement> targets = trueBranch.getNodes().stream().filter(NoOp.class::isInstance)
				.collect(Collectors.toSet());
		trueBranch.simplify(targets, Collections.emptySet());
		targets = falseBranch.getNodes().stream().filter(NoOp.class::isInstance).collect(Collectors.toSet());
		falseBranch.simplify(targets, Collections.emptySet());
	}

	@Override
	public AdjacencyMatrix<Statement, Edge, CFG> getCompleteStructure() {
		AdjacencyMatrix<Statement, Edge, CFG> complete = new AdjacencyMatrix<>(trueBranch);
		if (falseBranch != null && !falseBranch.getNodes().isEmpty())
			complete.mergeWith(falseBranch);

		complete.addNode(getCondition());
		if (getFirstFollower() != null)
			complete.addNode(getFirstFollower());

		complete.addEdge(new TrueEdge(getCondition(), trueBranch.getEntries().iterator().next()));
		if (falseBranch != null && !falseBranch.getNodes().isEmpty())
			complete.addEdge(new FalseEdge(getCondition(), falseBranch.getEntries().iterator().next()));
		else if (getFirstFollower() != null)
			complete.addEdge(new FalseEdge(getCondition(), getFirstFollower()));

		if (getFirstFollower() != null) {
			for (Statement exit : trueBranch.getExits())
				complete.addEdge(new SequentialEdge(exit, getFirstFollower()));

			if (falseBranch != null && !falseBranch.getNodes().isEmpty())
				for (Statement exit : falseBranch.getExits())
					complete.addEdge(new SequentialEdge(exit, getFirstFollower()));
		}

		return complete;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((falseBranch == null) ? 0 : falseBranch.hashCode());
		result = prime * result + ((trueBranch == null) ? 0 : trueBranch.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		IfThenElse other = (IfThenElse) obj;
		if (falseBranch == null) {
			if (other.falseBranch != null)
				return false;
		} else if (!falseBranch.equals(other.falseBranch))
			return false;
		if (trueBranch == null) {
			if (other.trueBranch != null)
				return false;
		} else if (!trueBranch.equals(other.trueBranch))
			return false;
		return true;
	}

	@Override
	public boolean isEqualTo(ControlFlowStructure obj) {
		if (this == obj)
			return true;
		if (!super.isEqualTo(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		IfThenElse other = (IfThenElse) obj;
		if (falseBranch == null) {
			if (other.falseBranch != null)
				return false;
		} else if (!falseBranch.isEqualTo(other.falseBranch))
			return false;
		if (trueBranch == null) {
			if (other.trueBranch != null)
				return false;
		} else if (!trueBranch.isEqualTo(other.trueBranch))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "if-then-else[" + getCondition() + "]";
	}
}
