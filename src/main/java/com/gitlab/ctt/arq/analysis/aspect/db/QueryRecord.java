package com.gitlab.ctt.arq.analysis.aspect.db;

import fj.data.Either;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.jena.query.Query;


@SuppressWarnings("WeakerAccess")
public class QueryRecord {
	public Either<Exception, Query> maybeQuery;
	public int id;
	public String origin;


	public String originMajor;  

	public String originMinor;  

	public int originLinum;  



	public byte[] hash;
	public String queryStr;

	public Boolean parseError;


	public Boolean regex;
	public Boolean teePredicate;
	public Boolean var_predicate;
	public Boolean bad_filter;
	public Boolean opt_bad_nesting;    
	public Boolean opt_bad_interface;  
	public Boolean projection;
	public Boolean projectionUnsure;
	public Boolean askProjection;
	public Boolean askProjectionUnsure;


	public Boolean wb;
	public Boolean uwd;
	public Boolean uwwd;
	public Boolean uwdComp;   
	public Boolean uwwdComp;  
	public Boolean wdpt;      

	public Boolean select;
	public Boolean construct;
	public Boolean ask;
	public Boolean describe;


	public Boolean distinct;
	public Boolean limit;
	public Boolean count;
	public Boolean sum;
	public Boolean avg;
	public Boolean min;
	public Boolean max;

	public Boolean afo;
	public Boolean afou;

	public Boolean and;
	public Boolean filter;
	public Boolean optional;
	public Boolean union;
	public Boolean graph;
	public Boolean subquery;
	public Boolean exists;
	public Boolean notExists;
	public Boolean service;
	public Boolean bind;
	public Boolean assign;
	public Boolean minus;
	public Boolean data;
	public Boolean dataset;


	public Boolean HAVING;
	public Boolean GROUP__BY;
	public Boolean ORDER__BY;
	public Boolean OFFSET;
	public Boolean VALUES;
	public Boolean SAMPLE;
	public Boolean GROUP_CONCAT;

	public Boolean cq;      
	public Boolean cq_f;    
	public Boolean cq_fo;   
	public Boolean cq_fov;  
	public Boolean cq_fox;  
	public Boolean nc;  

	public Boolean shapeless;

	public Boolean noNode;
	public Boolean singleNode;
	public Boolean noEdge;
	public Boolean singleEdge;
	public Boolean singleEdgeSet;
	public Boolean nonBranching;
	public Boolean nonBranchingSet;
	public Boolean limitedBranching;
	public Boolean limitedBranchingSet;
	public Boolean selfLoops;
	public Boolean parallelEdges;
	public Boolean chain;
	public Boolean chainSet;
	public Boolean star;
	public Boolean circle;
	public Boolean utree;
	public Boolean uforest;
	public Boolean cycletree;
	public Boolean bicycle;
	public Boolean flower;
	public Boolean flowerSet;
	public Boolean spFlower;
	public Boolean spFlowerSet;





















	public Integer varCount;
	public Integer constCount;
	public Integer optNestCount;


	public Boolean hasNoVarPredicateReuse;  
	public Boolean asRegularGraph;          
	public Boolean isCyclic;                
	public Integer componentCount;          
	public Boolean permit_service;          
	public Boolean permit_bind;             
	public Boolean permit_data;             
	public Boolean permit_filter;           

	public Integer tripleSymbolCount;
	public Integer fca_htw;
	public Boolean fca;              
	public Integer tw;               
	public Boolean treePattern;      
	public Integer cl_min;           
	public Integer cl_max;           
	public Integer depth_max;        
	public Integer degree_max;       
	public Double inner_degree_avg;  
	public Integer split_tot;        
	public Integer inner_tot;        
	public Double split_rel;         
	public Double inner_rel;         





























	public Integer tripleCount;
	public Double edgeCover;
	public Integer htw;  

	@Override
	public String toString() {
		return ReflectionToStringBuilder.reflectionToString(this,
			ToStringStyle.MULTI_LINE_STYLE);
	}
}
