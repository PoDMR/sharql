package com.gitlab.ctt.arq.util;

import com.gitlab.ctt.arq.analysis.aspect.util.HyperTreeEval;
import com.gitlab.ctt.arq.core.BatchProcessor.BailException;
import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.utilx.LabeledEdge;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.ext.com.google.common.collect.ImmutableSet;
import org.apache.jena.ext.com.google.common.collect.Lists;
import org.apache.jena.graph.Node_Variable;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.ARQInternalErrorException;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.walker.WalkerVisitor;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.ExprVisitorBase;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.Graph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.alg.cycle.PatonCycleBase;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.IntegerComponentNameProvider;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.DepthFirstIterator;

import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphShape {
	public static void main(String[] args) {
		String sparqlStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		Either<Exception, Query> mQuery = SparqlUtil.get().toQuery(sparqlStr);
		Element element = mQuery.right().value().getQueryPattern();
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		System.out.println(isCycleTreeU(graph));



		System.out.println(hasHiddenCycle(element, graph));
	}

	public static <V, E> boolean isChainSetU(DirectedGraph<V, E> graph) {
		return graph != null && isChainSetU(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> boolean isChainSetU(UndirectedGraph<V, E> ug) {
		List<Set<V>> cs = connectedSets(ug);
		return cs.stream().allMatch(c -> isChainU(subGraph(ug, c)));
	}

	public static <V, E> boolean isChainU(DirectedGraph<V, E> graph) {
		return graph != null && isChainU(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> boolean isChainU(UndirectedGraph<V, E> ug) {
		if (!isConnected(ug)) {
			return false;
		}

		Set<V> ends = ug.vertexSet().stream()
			.filter(v -> ug.degreeOf(v) == 1).collect(Collectors.toSet());
		return ends.size() == 2 && ug.vertexSet().stream()
			.filter(v -> !ends.contains(v))
			.allMatch(v -> ug.degreeOf(v) == 2);
	}

	public static <V, E> boolean isStarU(DirectedGraph<V, E> graph) {
		return graph != null && isStarU(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> boolean isStarU(UndirectedGraph<V, E> ug) {
		if (!isTreeU(ug)) {
			return false;
		}
		List<Object> rootCandidates = ug.vertexSet().stream()
			.filter(v -> ug.degreeOf(v) >= 3)
			.collect(Collectors.toList());
		if (rootCandidates.size() != 1) {
			return false;
		}

		Set<V> nonRoots = ug.vertexSet().stream()
			.filter(v -> !rootCandidates.get(0).equals(v))
			.collect(Collectors.toSet());
		UndirectedGraph<V, E> sg = subGraph(ug, nonRoots);
		return connectedSets(sg).stream().allMatch(
			c -> c.size() == 1 || isChainU(subGraph(ug, c))
		);
	}

	public static <V, E> boolean isCircleU(DirectedGraph<V, E> graph) {
		return graph != null && isCircleU(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> boolean isCircleU(UndirectedGraph<V, E> ug) {

		return isConnected(ug) &&
			ug.vertexSet().stream().allMatch(v -> ug.degreeOf(v) == 2);
	}

	public static <V, E> boolean isTreeU(DirectedGraph<V, E> graph) {
		return graph != null && isTreeU(new AsUndirectedGraph<>(graph));
	}

	public static <V, E> boolean isTreeU(UndirectedGraph<V, E> ug) {
		return isConnected(ug) && !isCyclic(ug);
	}

	static <V, E> UndirectedGraph<V, E> subGraph(UndirectedGraph<V, E> ug, Set<V> c) {
		return new UndirectedSubgraph<>(ug, c, ug.edgeSet());
	}

	public static <V, E> boolean isForestU(DirectedGraph<V, E> graph) {
		return graph != null && isForestU(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> boolean isForestU(UndirectedGraph<V, E> ug) {
		List<Set<V>> cs = connectedSets(ug);
		return cs.stream().allMatch(c -> isTreeU(subGraph(ug, c)));
	}

	public static <V, E> boolean isConnected(UndirectedGraph<V, E> ug) {
		ConnectivityInspector<V, E> connectivity = new ConnectivityInspector<>(ug);
		return connectivity.isGraphConnected();
	}

	public static <V, E> List<Set<V>> connectedSets(UndirectedGraph<V, E> ug) {
		ConnectivityInspector<V, E> connectivity = new ConnectivityInspector<>(ug);
		return connectivity.connectedSets();
	}









	public static <V, E> List<List<V>> findCycleBase(DirectedGraph<V, E> graph) {
		return findCycleBase(new AsUndirectedGraph<>(graph));
	}

	public static <V, E> List<List<V>> findCycleBase(UndirectedGraph<V, E> ug) {
		PatonCycleBase<V, E> cycleFinder = new PatonCycleBase<>();
		cycleFinder.setGraph(ug);
		return cycleFinder.findCycleBase();
	}

	public static <V, E> boolean isCyclic(UndirectedGraph<V, E> ug) {
		try {
			new DepthFirstIterator<V, E>(ug) {
				@Override
				protected void encounterVertexAgain(V vertex, E edge) {
					if (VisitColor.WHITE.equals(getSeenData(vertex)) ||
						ug.getEdgeSource(edge).equals(ug.getEdgeTarget(edge))) {
						throw new BailException();
					}
					super.encounterVertexAgain(vertex, edge);
				}
			}.forEachRemaining(x -> {
			});
		} catch (BailException ignored) {
			return true;
		}
		return false;
	}

	public static <V, E> boolean isCycleTreeU(DirectedGraph<V, E> graph) {
		return graph != null && isCycleTreeU(new AsUndirectedGraph<>(graph));
	}

	public static <V, E> int longestPath(UndirectedGraph<V, E> ug) {
		List<Set<V>> cs = connectedSets(ug);
		return cs.stream().mapToInt(c -> longestPathInTree(subGraph(ug, c))).max().orElse(0);
	}

	private static <V, E> int longestPathInTree(UndirectedGraph<V, E> ug) {
		Optional<V> maybeV = ug.vertexSet().stream().findFirst();
		if (maybeV.isPresent()) {
			V v = maybeV.get();
			Pair<V, Integer> p1 = farBfs(ug, v);
			Pair<V, Integer> p2 = farBfs(ug, p1.getLeft());
			return p2.getRight();
		}
		return 0;
	}

	private static <V, E> Pair<V, Integer> farBfs(UndirectedGraph<V, E> ug, V v1) {
		Map<V, Integer> dist = new LinkedHashMap<>();
		ArrayDeque<V> q = new ArrayDeque<>();
		q.add(v1);
		dist.put(v1, 0);
		while (!q.isEmpty()) {
			V v = q.pop();

			for (E e : ug.edgesOf(v)) {
				V w = ug.getEdgeTarget(e);
				if (w.equals(v)) {
					w = ug.getEdgeSource(e);
				}
				if (dist.get(w) == null) {
					q.push(w);
					dist.put(w, dist.get(v) + 1);
				}
			}
		}
		Pair<V, Integer> max = Pair.of(v1, 0);
		for (Map.Entry<V, Integer> entry : dist.entrySet()) {
			if (entry.getValue() > max.getRight()) {
				max = Pair.of(entry.getKey(), entry.getValue());
			}
		}
		return max;
	}

	private static <V, E> boolean isCycleTreeU(UndirectedGraph<V, E> graph) {
		return isConnected(graph) && oneCycleTrimmable(graph);
	}

	private static <V, E> boolean oneCycleTrimmable(UndirectedGraph<V, E> graph) {
		List<E> cycle = findCycle(graph);
		if (cycle.size() < 3) {
			return false;
		}
		Set<E> oneEdgeRemoved = new LinkedHashSet<>(graph.edgeSet());
		oneEdgeRemoved.remove(Lists.reverse(cycle).get(0));
		UndirectedSubgraph<V, E> subgraph = new UndirectedSubgraph<>(
			graph, graph.vertexSet(), oneEdgeRemoved);
		if (findCycle(subgraph).size() != 0) {
			return false;
		}
		HashSet<E> edges = new HashSet<>(graph.edgeSet());
		edges.removeAll(cycle);
		Set<V> isolated = new HashSet<>();
		cycle.stream().map(graph::getEdgeSource)
			.filter(v -> graph.degreeOf(v) < 3).forEach(isolated::add);
		cycle.stream().map(graph::getEdgeTarget)
			.filter(v -> graph.degreeOf(v) < 3).forEach(isolated::add);
		HashSet<V> vertexSet = new HashSet<>(graph.vertexSet());
		vertexSet.removeAll(isolated);
		return !vertexSet.isEmpty() &&
			isForestU(new UndirectedSubgraph<>(graph, vertexSet, edges));
	}

	private static <V, E> List<E> findCycle(UndirectedGraph<V, E> graph) {
		Deque<V> todo = new ArrayDeque<>(graph.vertexSet());
		Set<V> mark = new LinkedHashSet<>();
		while (!todo.isEmpty()) {
			V start = todo.removeFirst();
			Deque<Pair<V, Deque<E>>> stack = new ArrayDeque<>();
			stack.push(Pair.of(start, new ArrayDeque<>()));
			while (!stack.isEmpty()) {
				Pair<V, Deque<E>> pair = stack.pop();
				V v = pair.getLeft();
				Deque<E> path = pair.getRight();
				if (mark.contains(v)) {
					return new ArrayList<>(path);
				}
				mark.add(v);
				for (E e : graph.edgesOf(v)) {
					V w = graph.getEdgeTarget(e);
					if (w.equals(v)) {
						w = graph.getEdgeSource(e);
					}
					if (path.isEmpty() || !e.equals(path.getLast())) {
						Deque<E> newPath = new ArrayDeque<>(path);
						newPath.add(e);
						stack.push(Pair.of(w, newPath));
					}
				}
			}
			todo.removeAll(mark);
		}
		return Collections.emptyList();
	}

	public static <V, E> UndirectedGraph<V, E> pseudoToSimple(DirectedGraph<V, E> graph) {
		return pseudoToSimple(new AsUndirectedGraph<>(graph));
	}

	private static <V, E> UndirectedGraph<V, E> pseudoToSimple(UndirectedGraph<V, E> graph) {
		Set<V> vertexSet = graph.vertexSet();
		Set<E> edges = new HashSet<>(graph.edgeSet());
		Map<Set<V>, Set<E>> map = new LinkedHashMap<>();
		for (E e : graph.edgeSet()) {
			V src = graph.getEdgeSource(e);
			V tgt = graph.getEdgeTarget(e);
			if (src.equals(tgt)) {
				edges.remove(e);
			}
			map.computeIfAbsent(ImmutableSet.of(src, tgt), k -> new LinkedHashSet<>()).add(e);
		}
		for (Map.Entry<Set<V>, Set<E>> entry : map.entrySet()) {
			Set<E> es = entry.getValue();
			if (es.size() > 1) {
				es.stream().skip(1).forEach(edges::remove);
			}
		}
		return new UndirectedSubgraph<>(graph, vertexSet, edges);
	}

	public static <V, E> boolean isChainSetUSG(DirectedGraph<V, E> graph) {
		return isChainSetU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isStarUSG(DirectedGraph<V, E> graph) {
		return isStarU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isCircleUSG(DirectedGraph<V, E> graph) {
		return isCircleU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isTreeUSG(DirectedGraph<V, E> graph) {
		return isTreeU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isForestUSG(DirectedGraph<V, E> graph) {
		return isForestU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isCycleTreeUSG(DirectedGraph<V, E> graph) {
		return GraphShape.isCycleTreeU(pseudoToSimple(graph));
	}

	public static <V, E> boolean isBicycleUSG(UndirectedGraph<V, E> graph) {
		if (graph == null) {
			return false;
		}
		UndirectedGraph<V, E> sg = GraphShape.pseudoToSimple(graph);
		List<List<V>> base = GraphShape.findCycleBase(sg);
		return base.size() == 2;
	}

	public static <V, E> boolean isBicycleUSG(DirectedGraph<V, E> graph) {
		return isBicycleUSG(new AsUndirectedGraph<>(graph));
	}

	public static <V, E> boolean isSingeNode(DirectedGraph<V, E> graph) {
		return graph.vertexSet().size() == 1
			&& graph.edgeSet().size() == 0;
	}

	public static <V, E> boolean isSingeEdge(DirectedGraph<V, E> graph) {
		return graph.edgeSet().size() == 1 &&
			graph.vertexSet().stream().findAny().map(
				v -> graph.outDegreeOf(v) + graph.inDegreeOf(v) <= 1
			).orElse(true);
	}

	public static <V, E> boolean isSingeEdgeSet(DirectedGraph<V, E> graph) {
		return GraphShape.connectedSets(new AsUndirectedGraph<>(graph))
			.stream().allMatch(
				cs -> cs.size() <= 2 &&
				cs.stream().allMatch(
					v -> graph.outDegreeOf(v) + graph.inDegreeOf(v) <= 1)
			);
	}

	public static <V, E> boolean isNonBranching(DirectedGraph<V, E> graph) {
		return limitBranchTest(graph, false, 0);
	}

	public static <V, E> boolean isNonBranchingSet(DirectedGraph<V, E> graph) {
		return limitBranchTest(graph, true, 0);
	}

	public static <V, E> boolean isLimitedBranching(DirectedGraph<V, E> graph) {
		return limitBranchTest(graph, false, 1);
	}

	public static <V, E> boolean isLimitedBranchingSet(DirectedGraph<V, E> graph) {
		return limitBranchTest(graph, true, 1);
	}

	private static <V, E> boolean limitBranchTest(DirectedGraph<V, E> graph, boolean set, int max) {
		AsUndirectedGraph<V, E> ug = new AsUndirectedGraph<>(graph);
		if (isCyclic(ug)) {
			return false;
		}
		List<Set<V>> cs = GraphShape.connectedSets(ug);
		if (!(set || cs.size() <= 1)) {
			return false;
		}
		return cs.stream().allMatch(
			c -> c.stream().mapToInt(ug::degreeOf).filter(i -> i > 2).count() <= max
		);
	}

	public static <V, E> boolean hasSelfLoop(DirectedGraph<V, E> graph) {
		UndirectedGraph<V, E> ug = new AsUndirectedGraph<>(graph);
		for (E e : ug.edgeSet()) {
			if (Objects.equals(ug.getEdgeSource(e), ug.getEdgeTarget(e))) {
				return true;
			}
		}
		return false;
	}

	public static <V, E> boolean hasParallelEdges(DirectedGraph<V, E> graph) {
		UndirectedGraph<V, E> ug = new AsUndirectedGraph<>(graph);
		Set<Pair<V, V>> pairs = new LinkedHashSet<>();
		for (E e : ug.edgeSet()) {
			Pair<V, V> p = Pair.of(ug.getEdgeSource(e), ug.getEdgeTarget(e));
			if (pairs.contains(p)) {
				return true;
			}
			pairs.add(p);
		}
		return false;
	}

	public static boolean hasHiddenCycle(Element element,
			DirectedGraph<Object, DefaultEdge> graph) {

		UndirectedGraph<Object, DefaultEdge> ug0 = new AsUndirectedGraph<>(graph);
		if (isCyclic(ug0)) {
			return false;
		}
		DirectedGraph<Object, DefaultEdge> graphHE = new DirectedPseudograph<>(DefaultEdge.class);
		SparqlGraph.tryGraphFromQuery(element, graphHE, (s, o, p) -> {
			graphHE.addVertex(s);
			graphHE.addVertex(o);
			if (p instanceof Node_Variable) {
				graphHE.addVertex(p);
				graphHE.addEdge(s, p, new LabeledEdge<>(Arrays.asList(s, o)));
				graphHE.addEdge(p, o, new LabeledEdge<>(Arrays.asList(p, o)));
			} else {
				graphHE.addEdge(s, o, new LabeledEdge<>(Arrays.asList(s, o, p)));
			}
		});
		UndirectedGraph<Object, DefaultEdge> ug = new AsUndirectedGraph<>(graphHE);
		return isCyclic(ug);
	}

	
	public static Either<Boolean, Integer> freeConnexAcyclic(
		Query query,
		DirectedGraph<Object, DefaultEdge> graph,
		boolean regex
	) {
		List<Var> vars = getProjectionInput(query);
		if (graph != null && vars.size() <= 2) {
			if (vars.size() == 2) {

				graph.addVertex(vars.get(0));
				graph.addVertex(vars.get(1));
				graph.addEdge(vars.get(0), vars.get(1));
				UndirectedGraph<Object, DefaultEdge> ug = new AsUndirectedGraph<>(graph);
				return Either.left(!isCyclic(ug));
			} else {
				return Either.left(true);
			}
		} else if (!regex) {
			List<String> strings = vars
				.stream().map(String::valueOf).collect(Collectors.toList());
			int htw = HyperTreeEval.hyperTreeCheck(query, strings).getLeft();
			return Either.right(htw);
		} else {
			return Either.right(0);
		}
	}

	private static List<Var> getProjectionInput(Query query) {
		try {
			Map<Var, Expr> exprs = query.getProject().getExprs();
			List<Var> projectVars = query.getProjectVars();
			if (exprs != null && projectVars != null) {
				return projectVars.stream().flatMap(v ->
					exprs.get(v) != null ? getVars(exprs.get(v)).stream() : Stream.of(v)
				).collect(Collectors.toList());
			}
		} catch (ARQInternalErrorException ignored) {
		}
		return Collections.emptyList();
	}

	private static Set<Var> getVars(Expr expr) {
		Set<Var> vars = new LinkedHashSet<>();
		ExprVisitorBase exprVisitor = new ExprVisitorBase() {
			@Override
			public void visit(ExprVar nv) {
				super.visit(nv);
				vars.add(nv.asVar());
			}
		};
		WalkerVisitor walker = new WalkerVisitor(new OpVisitorBase(), exprVisitor,
			new OpVisitorBase(), new OpVisitorBase());
		walker.walk(expr);
		return vars;

	}

	public static <V, E> String graphToViz(Graph<V, E> graph) {
		DOTExporter<V, E> exporter = new DOTExporter<>(
			new IntegerComponentNameProvider<>(),
			component -> StringEscapeUtils.escapeJava(component.toString()),
			component -> StringEscapeUtils.escapeJava(component.toString()));
		Writer writer = new StringWriter();
		exporter.exportGraph(graph, writer);
		return writer.toString();
	}
}
