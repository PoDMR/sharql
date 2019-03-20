package com.gitlab.ctt.arq.analysis.aspect.db;

import com.gitlab.ctt.arq.analysis.aspect.ShapeAnalysis;
import com.gitlab.ctt.arq.analysis.aspect.StructureCount;
import com.gitlab.ctt.arq.analysis.aspect.util.Element2Triples;
import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.analysis.aspect.util.HyperTreeEval;
import com.gitlab.ctt.arq.analysis.support.PathWalker;
import com.gitlab.ctt.arq.sparql.*;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck;
import com.gitlab.ctt.arq.sparql.check.OptCheck2;
import com.gitlab.ctt.arq.util.*;
import com.gitlab.ctt.arq.utilx.Resources;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.aggregate.*;
import org.apache.jena.sparql.syntax.Element;
import org.jgrapht.DirectedGraph;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.gitlab.ctt.arq.analysis.aspect.ShapeAnalysis.AFOSBD;
import static com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker.DATA;

public class RecordProcessor {
	public static void main(String[] args) {
		QueryRecord record = new QueryRecord();

		record.queryStr = Resources.getResourceAsString("sample/misc/scrap.sparql");
		processFull(record);
		System.out.println(record);
	}

	public static void processFull(QueryRecord record) {
		Either<Exception, Query> maybeQuery = record.maybeQuery;
		if (record.maybeQuery == null) {

			maybeQuery = SparqlUtil.get().toQuery(record.queryStr);
		}

		if (maybeQuery.isRight()) {
			Query query = maybeQuery.right().value();
			withQuery(record, query);
		} else {
			record.parseError = true;
		}
	}

	private static void withQuery(QueryRecord record, Query query) {
		record.parseError = false;
		Element element = query.getQueryPattern();
		if (element != null) {
			analyzeQuery(record, query);
			withBody(record, element, query);
		}



	}

	private static void analyzeQuery(QueryRecord record, Query query) {
		record.select = query.isSelectType();
		record.ask = query.isAskType();
		record.construct = query.isConstructType();
		record.describe = query.isDescribeType();

		record.distinct = query.isDistinct();
		record.limit = query.hasLimit();

		Optional<Boolean> projection = SparqlQueries.maybeProjection(query);
		if (!projection.isPresent()) {
			record.projectionUnsure = true;
		} else if (projection.orElse(false)) {
			record.projection = true;
		} else {
			record.projection = false;
			record.projectionUnsure = false;
		}
		Optional<Boolean> askProjection = SparqlQueries.maybeAskProjection(query);
		if (!askProjection.isPresent()) {
			record.askProjectionUnsure = true;
		} else if (askProjection.orElse(false)) {
			record.askProjection = true;
		} else {
			record.askProjection = false;
			record.askProjectionUnsure = false;
		}

		List<ExprAggregator> aggregators = query.getAggregators();
		record.count = record.sum = record.avg = record.min = record.max = false;
		for (ExprAggregator exprAggregator : aggregators) {
			Aggregator aggregator = exprAggregator.getAggregator();
			record.count = (aggregator instanceof AggCount);
			record.sum = (aggregator instanceof AggSum);
			record.avg = (aggregator instanceof AggAvg);
			record.min = (aggregator instanceof AggMin);
			record.max = (aggregator instanceof AggMax);
		}
	}

	private static void withBody(QueryRecord record, Element element, Query query) {
		record.regex = SparqlProperties.get().hasPath(element);
		record.teePredicate = SparqlGraph.hasTeePredicate(element);
		record.var_predicate = SparqlGraph.hasVarPredicate(element);
		record.hasNoVarPredicateReuse = SparqlGraph.freeVarPredicate(element);

		analyzeOperators(record, element);

		if (!record.regex && !record.teePredicate) {
			record.wb = SparqlProperties.get().isWellBehaved(element);
			record.uwd = DesignCheck.isUwd(element);
			record.uwwd = DesignCheck.isUwwd(element);

			if (record.afou && record.optional) {
				Element unionNormalized = SparqlTransducer.get().unionNormalize(element);
				record.uwdComp = DesignCheck.unionDecomposition(
					unionNormalized).map(x -> x.apply(DesignCheck::isUwd)).orElse(false);
				record.uwwdComp = DesignCheck.unionDecomposition(
					unionNormalized).map(x -> x.apply(DesignCheck::isUwwd)).orElse(false);
			}
		}

		analyzeShapes(record, element, query);

		Set<TriplePath> tps = SparqlAlgorithms.collectTriplesWithService(element);
		record.tripleCount = tps.size();
		record.tripleSymbolCount = (int) PathWalker.symbolCount(element);
	}

	private static void analyzeOperators(QueryRecord record, Element element) {
		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		record.and = flagWalker.and.isTrue();
		record.filter = flagWalker.filter.isTrue();
		record.optional = flagWalker.optional.isTrue();
		record.union = flagWalker.union.isTrue();
		record.graph = flagWalker.graph.isTrue();
		record.subquery = flagWalker.subquery.isTrue();  
		record.exists = flagWalker.exists.isTrue();
		record.notExists = flagWalker.notExists.isTrue();
		record.service = flagWalker.service.isTrue();
		record.bind = flagWalker.bind.isTrue();
		record.assign = flagWalker.assign.isTrue();
		record.minus = flagWalker.minus.isTrue();
		record.data = flagWalker.data.isTrue();
		record.dataset = flagWalker.dataset.isTrue();

		long flagLong = flagWalker.asLong();
		record.afo = ((flagLong & ~13L) == 0);
		record.afou = ((flagLong & ~0b1111) == 0);
		record.cq = (flagLong & ~(1)) == 0;
		record.cq_f = (flagLong & ~(1|8)) == 0;
		record.cq_fo = (flagLong & ~(1|8|4)) == 0;
		record.cq_fov = (flagLong & ~(1|8|4|(1<<DATA-1))) == 0;
		record.cq_fox = (flagLong & ~(AFOSBD)) == 0;

		Set<String> keywords = new HashSet<>();
		StructureCount.keywordAnalyze(record.queryStr, keywords::add);
		record.HAVING = keywords.contains("HAVING");
		record.GROUP__BY = keywords.contains("GROUP BY");
		record.ORDER__BY = keywords.contains("ORDER BY");
		record.OFFSET = keywords.contains("OFFSET");
		record.VALUES = keywords.contains("VALUES");
		record.SAMPLE = keywords.contains("SAMPLE");
		record.GROUP_CONCAT = keywords.contains("GROUP_CONCAT");
	}

	private static void analyzeShapes(QueryRecord record, Element element, Query query) {
		DirectedGraph<Object, DefaultEdge> graph = SparqlGraph.graphFromQuery(element);
		record.nc = false;


		if (graph != null) {
			handleGraph(record, graph, element, query);
		}



	}

	private static void graphPreAnalysis(QueryRecord record,
		DirectedGraph<Object, DefaultEdge> graph, Element element) {


		record.permit_service = Element2Triples.permitServiceInGraph(element, graph);
		record.permit_bind = Element2Triples.permitBindInGraph(element, graph);
		record.permit_data = Element2Triples.permitValuesInGraph(element, graph);
		record.permit_filter = Element2Triples.permitFilterInGraph(element, graph);
		record.bad_filter =
			!(record.permit_service && record.permit_bind && record.permit_data && record.permit_filter);

		record.asRegularGraph = !record.teePredicate && record.hasNoVarPredicateReuse
			&& !record.bad_filter
		;

		record.wdpt = true;
		if (record.optional && record.asRegularGraph && record.cq_fox) {
			OptCheck optCheck = OptCheck.check(element);
			record.opt_bad_nesting = optCheck.isBadNesting();
			record.opt_bad_interface = optCheck.isBadInterface();
			record.asRegularGraph &= record.opt_bad_interface;
			if (Boolean.FALSE.equals(record.uwd) && !record.opt_bad_interface) {
				record.wdpt = OptCheck2.wdpt(element);  
				record.cq_fox &= record.wdpt;
			} else {
				record.wdpt = false;
				record.cq_fox &= !Boolean.FALSE.equals(record.uwd);
			}
		}

		record.cq     = record.asRegularGraph && record.cq;
		record.cq_f   = record.asRegularGraph && record.cq_f;
		record.cq_fo  = record.asRegularGraph && record.cq_fo;
		record.cq_fov = record.asRegularGraph && record.cq_fov;
		record.cq_fox = record.asRegularGraph && record.cq_fox;
	}

	private static void handleGraph(QueryRecord record,
			DirectedGraph<Object, DefaultEdge> graph, Element element, Query query) {
		graphPreAnalysis(record, graph, element);
		if (!(record.wdpt && record.cq_fox)) return;

		if (record.asRegularGraph) {

			UndirectedGraph<Object, DefaultEdge> ug = new AsUndirectedGraph<>(graph);
			record.isCyclic = GraphShape.isCyclic(ug);

			if (!record.isCyclic) {
				record.noNode = false;
				record.noEdge = graph.edgeSet().size() == 0;

				record.selfLoops = false;
				record.parallelEdges = false;

				record.chain = GraphShape.isChainU(graph);
				record.chainSet = GraphShape.isChainSetU(graph);
				record.star = GraphShape.isStarU(graph);
				record.utree = GraphShape.isTreeU(graph);
				record.uforest =  GraphShape.isForestU(graph);

				record.circle = false;
				record.cycletree = false;
				record.bicycle = false;
			} else {
				record.noNode = graph.vertexSet().size() == 0;
				record.noEdge = false;

				record.selfLoops = GraphShape.hasSelfLoop(graph);
				record.parallelEdges = GraphShape.hasParallelEdges(graph);  

				record.chain = false;
				record.chainSet = false;
				record.star = false;
				record.utree = false;
				record.uforest = false;

				record.circle = GraphShape.isCircleU(graph);
				record.cycletree = GraphShape.isCycleTreeU(graph);
				record.bicycle = GraphShape.isBicycleUSG(graph);
			}

			record.singleNode = GraphShape.isSingeNode(graph);
			record.singleEdge = GraphShape.isSingeEdge(graph);
			record.singleEdgeSet = GraphShape.isSingeEdgeSet(graph);

			record.nonBranching = GraphShape.isNonBranching(graph);
			record.nonBranchingSet = GraphShape.isNonBranchingSet(graph);
			record.limitedBranching = GraphShape.isLimitedBranching(graph);
			record.limitedBranchingSet = GraphShape.isLimitedBranchingSet(graph);

			record.flower = SeriesParallel.isFlower(graph, false);
			record.flowerSet = SeriesParallel.isGarden(graph, false);
			record.spFlower = SeriesParallel.isFlower(graph, true);
			record.spFlowerSet = SeriesParallel.isGarden(graph, true);

			record.shapeless =
				!record.noNode &&
				!record.singleNode &&
				!record.noEdge &&
				!record.singleEdge &&
				!record.selfLoops &&
				!record.parallelEdges &&
				!record.chain &&
				!record.chainSet &&
				!record.star &&
				!record.utree &&
				!record.uforest &&
				!record.circle &&
				!record.cycletree &&
				!record.bicycle &&
				!record.flower &&
				!record.flowerSet &&
				!record.spFlower &&
				!record.spFlowerSet;
		}




		calculateExtras(record, graph, element, query);

		boolean hypergraphView = !record.asRegularGraph && !record.regex;
		if (record.asRegularGraph) {
			if (Boolean.TRUE.equals(record.shapeless)) {
				record.tw = HyperTreeEval.get().hyperTreeWidth(query, true, false);
			}
		}
		if (hypergraphView) {


				Pair<Integer, Integer> widthXnodeCount = HyperTreeEval.get().hyperTreeCheck(query);
				int hyperTreeWidth = widthXnodeCount.getLeft();

				if (hyperTreeWidth > 0) {
					record.htw = hyperTreeWidth;
				}

		}
	}

	private static void calculateExtras(QueryRecord record,
			DirectedGraph<Object, DefaultEdge> graph, Element element, Query query) {

		if (record.asRegularGraph) {
			UndirectedGraph<Object, DefaultEdge> ug = GraphShape.pseudoToSimple(graph);
			double count = ug.vertexSet().size();
			if (count > 0) {
				List<Set<Object>> cs = GraphShape.connectedSets(ug);
				record.componentCount = cs.size();

				record.depth_max = GraphShape.longestPath(ug);
				record.degree_max = ug.vertexSet().stream()
					.mapToInt(ug::degreeOf).max().orElse(0);
				record.inner_degree_avg = ug.vertexSet().stream()
					.mapToInt(ug::degreeOf).filter(x -> x > 1).average().orElse(0f);
				record.split_tot = (int) ug.vertexSet().stream()
					.mapToInt(ug::degreeOf).filter(x -> x > 2).count();
				record.inner_tot = (int) ug.vertexSet().stream()
					.mapToInt(ug::degreeOf).filter(x -> x > 1).count();
				record.split_rel = record.split_tot / count;
				record.inner_rel = record.inner_tot / count;

				List<List<Object>> cycleBase = GraphShape.findCycleBase(ug);
				boolean reasonableCount = cycleBase.size() <= 8;
				if (reasonableCount) {
					cycleBase = Cycles.findBase(cycleBase);
				}
				record.cl_min = cycleBase.stream().mapToInt(List::size).min().orElse(0);
				record.cl_max = cycleBase.stream().mapToInt(List::size).max().orElse(0);

				record.treePattern = SparqlGraph.isTreePatternAcyclic(graph, element);
			}
		}

		record.varCount = ShapeAnalysis.countVars(element);
		record.constCount = ShapeAnalysis.countConstants(element);

		record.optNestCount = ShapeAnalysis.optNest(element);

		Either<Boolean, Integer> fca = GraphShape.freeConnexAcyclic(query, graph, record.regex);
		if (record.asRegularGraph && fca.isLeft()) {
			record.fca = true;
		} else {
			if (!record.regex && fca.isRight()) {
				record.fca_htw = fca.right().value();
			}
		}

		record.edgeCover = EdgeCover.edgeCoverNumber(element);
	}
}
