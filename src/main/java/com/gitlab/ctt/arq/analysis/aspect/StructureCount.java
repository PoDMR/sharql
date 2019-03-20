package com.gitlab.ctt.arq.analysis.aspect;

import com.gitlab.ctt.arq.analysis.aspect.util.FlagWalker;
import com.gitlab.ctt.arq.sparql.SparqlAlgorithms;
import com.gitlab.ctt.arq.sparql.SparqlGraph;
import com.gitlab.ctt.arq.sparql.SparqlProperties;
import com.gitlab.ctt.arq.sparql.SparqlTransducer;
import com.gitlab.ctt.arq.sparql.check.DesignCheck;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.syntax.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StructureCount {

	private AtomicLong regexCounter = new AtomicLong();



	private AtomicLong wbCounter = new AtomicLong();

	private AtomicLong uwdCounter = new AtomicLong();
	private AtomicLong uwwdCounter = new AtomicLong();
	private AtomicLong wdCounter = new AtomicLong();
	private AtomicLong wwdCounter = new AtomicLong();

	private AtomicLong uwdCompCounter = new AtomicLong();
	private AtomicLong uwwdCompCounter = new AtomicLong();
	private AtomicLong acyclicFOCounter = new AtomicLong();
	private AtomicLong acyclicCounter = new AtomicLong();
	private AtomicLong acyclicNoFilterCounter = new AtomicLong();

	private ShapeAnalysis shapeAnalysis;






















	private AtomicLong teePredicateCounter = new AtomicLong();

	public StructureCount(String tag) {
		shapeAnalysis = new ShapeAnalysis(tag);
	}

	@SuppressWarnings("Duplicates")
	public void handleQuery(Query query) {

		Element element = query.getQueryPattern();
		boolean hasRegex = SparqlProperties.get().hasPath(element);


		FlagWalker flagWalker = new FlagWalker();
		flagWalker.consume(element);
		long flagLong = flagWalker.asLong();
		boolean hasFilter = ((flagLong & 8L) != 0);
		boolean hasOptional = ((flagLong & 4L) != 0);
		boolean cpf = ((flagLong & ~9L) == 0);  
		boolean afo = ((flagLong & ~13L) == 0); 
		boolean afou = ((flagLong & ~0b1111) == 0); 
		boolean selectOrAsk = query.isSelectType() || query.isAskType() || query.isConstructType();
		afou = afou && selectOrAsk;
		afo = afo && selectOrAsk;




		boolean wellBehaved = !hasRegex && afou && SparqlProperties.get().isWellBehaved(element);

		boolean uwd = !hasRegex && afou && DesignCheck.isUwd(element);
		boolean uwwd = !hasRegex && afou && DesignCheck.isUwwd(element);
		boolean wd = !hasRegex && afo && DesignCheck.isUwd(element);
		boolean wwd = !hasRegex && afo && DesignCheck.isUwwd(element);

		boolean optAFU = !hasRegex && hasOptional && afou;


		Element unionNormalized = SparqlTransducer.get().unionNormalize(element);
		boolean uwdComp = optAFU && DesignCheck.unionDecomposition(
			unionNormalized).map(x -> x.apply(DesignCheck::isUwd)).orElse(false);
		boolean uwwdComp = optAFU && DesignCheck.unionDecomposition(
			unionNormalized).map(x -> x.apply(DesignCheck::isUwwd)).orElse(false);



		boolean acyclicFO = selectOrAsk && !hasRegex && afo &&
			SparqlProperties.get().isAcyclic(element);
		boolean acyclic = acyclicFO && cpf;
		boolean acyclicNoFilter = acyclic && !hasFilter;















		shapeAnalysis.analyzeShape(query, flagWalker);

		doKeywordAnalyze(query);





		boolean teePredicate = SparqlGraph.hasTeePredicate(element);


		if (wellBehaved) {
			wbCounter.getAndIncrement();
		}
		if (uwd) {
			uwdCounter.getAndIncrement();
		}
		if (uwwd) {
			uwwdCounter.getAndIncrement();
		}
		if (wd) {
			wdCounter.getAndIncrement();
		}
		if (wwd) {
			wwdCounter.getAndIncrement();
		}
		if (uwdComp) {
			uwdCompCounter.getAndIncrement();
		}
		if (uwwdComp) {
			uwwdCompCounter.getAndIncrement();
		}






		if (hasRegex) {
			regexCounter.getAndIncrement();
		}






		if (acyclicFO) {
			acyclicFOCounter.getAndIncrement();
		}
		if (acyclic) {
			acyclicCounter.getAndIncrement();
		}
		if (acyclicNoFilter) {
			acyclicNoFilterCounter.getAndIncrement();
		}





































		if (teePredicate) {
			teePredicateCounter.getAndIncrement();
		}













	}

	@Deprecated
	private void processOnTopLevelUnions(Element element, FlagWalker flagWalker) {


		if (flagWalker.subquery.isTrue() ||
			flagWalker.graph.isTrue() ||
			flagWalker.optional.isTrue()) {
			return;
		}
		boolean hasRegex = SparqlProperties.get().hasPath(element);

		List<Element> unionArgs = new ArrayList<>();
		SparqlAlgorithms.collectUnionArgs(unionArgs, element);





























	}

	public void mapCounters(Map<String, Long> map) {
		map.put("regex", regexCounter.get());



		map.put("acyclicNoFilter", acyclicNoFilterCounter.get());
		map.put("acyclic", acyclicCounter.get());
		map.put("acyclicFO", acyclicFOCounter.get());
		map.put("wb", wbCounter.get());
		map.put("uwd", uwdCounter.get());
		map.put("uwwd", uwwdCounter.get());
		map.put("wd", wdCounter.get());
		map.put("wwd", wwdCounter.get());

		map.put("uwdComp", uwdCompCounter.get());
		map.put("uwwdComp", uwwdCompCounter.get());

		shapeAnalysis.outputCount(map);
		shapeAnalysis.commit();






















		map.put("teePredicate", teePredicateCounter.get());

		mapKeywords(map);
	}

	private static final List<String> keywords = Arrays.asList(
		"REDUCED",
		"GROUP BY",
		"HAVING",
		"ORDER BY",
		"LIMIT",
		"OFFSET",
		"VALUES");
	private static final List<String> operators = Arrays.asList(
		"SAMPLE",
		"GROUP_CONCAT"
	);
	private Map<String, LongAdder> keywordCounterMap = new ConcurrentHashMap<>();
	private static final LongAdder DUMMY_ADDER = new LongAdder();

	private void doKeywordAnalyze(Query query) {
		try {
			keywordAnalyze(query.toString(), key ->
				keywordCounterMap.computeIfAbsent(key, k -> new LongAdder()).increment());
		} catch (Exception ignored) {

		}
	}

	public static void keywordAnalyze(String queryStr, Consumer<String> consumer) {
		List<String> lines = Arrays.asList(queryStr.split("\n|(\r\n)"));
		lines = lines.stream().filter(s -> !s.startsWith("#")).collect(Collectors.toList());
		String input = String.join("\n", lines);
		keywords.forEach(keyword -> checkKeyword(input, keyword, wrapWord(keyword), consumer));
		operators.forEach(operator -> checkKeyword(input, operator, operator, consumer));
	}

	private static void checkKeyword(String input, String key, String patternStr, Consumer<String> consumer) {
		Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(input);
		if (matcher.find()) {
			consumer.accept(key);
		}
	}

	private static String wrapWord(String word) {
		return String.format("(?<!\\S)%s(?!\\S)", Pattern.quote(word));
	}

	private void mapKeywords(Map<String, Long> map) {
		for (String keyword : keywords) {
			map.put(keyword, keywordCounterMap.getOrDefault(keyword, DUMMY_ADDER).longValue());
		}
		for (String operator : operators) {
			map.put(operator, keywordCounterMap.getOrDefault(operator, DUMMY_ADDER).longValue());
		}
	}
}
