var sparqljs = require('sparqljs');

var toEntity = require('toEntity');  
require('cose-bilkent');  
var SparqlGraph2 = require('sparql-graph2');

exports.SparqlGraph = (function() {
	function SparqlGraph(options) {
		this.options = options;
		this.cy = options.cy;
		this.editor = options.editor;
		this.extraRender = new SparqlGraph2(this);

		this.counter = 0;

		options.hyperNodeColor = options.hyperNodeColor || '#4466EE';
		options.hyperEdgeColor = options.hyperEdgeColor || '#0088FF';
		options.predicateColor = options.predicateColor || '#FF6600';




		function booleanDefault(value, defaultValue) {
			return value === undefined ? defaultValue || true : value;
		}

		options.showConstants  = booleanDefault(options.showConstants);
		options.showEdgeLabels = booleanDefault(options.showEdgeLabels);
		options.layout = options.layout || "cose-bilkent";

		options.layouts = options.layouts || ["cose-bilkent", "cose", "concentric", "breadthfirst", "grid", "circle"];
		this.drawGraphHybrid = this.extraRender.drawGraphHybrid.bind(this.extraRender);
		options.renderers = options.renderers || ['drawGraphReg', 'drawGraphHE', 'drawGraphHybrid'];
	}

	SparqlGraph.prototype = {
		nextLayout: function(offset) {
			var current = this.options.layouts.indexOf(this.options.layout);
			var length = this.options.layouts.length;
			var next = (length + current + offset) % length;
			this.options.layout = this.options.layouts[next];

		},

		nextRenderer: function(offset) {
			var current = this.options.renderers.indexOf(this.options.drawGraph);
			var length = this.options.renderers.length;
			var next = (length + current + offset) % length;
			this.options.drawGraph = this.options.renderers[next];

		},

		drawGraph: function(val) {
			this[this.options.drawGraph](val);

		},

		drawIt: function() {

			this.cy.customReset();

			this.drawGraph(this.editor.getValue());
			this.cy.nodes().on("click", function() {
				if (window.event.ctrlKey) this.remove();
			});
			this.cy.edges().on("click", function() {
				if (window.event.ctrlKey) this.remove();
			});
		},

		connectAll: function(heSet) {
			heSet.forEach(function(v1) {
				this.cy.addVertex(v1);
			}, this);
			var arr = Array.from(heSet);
			arr.some(function(v1) {
				return arr.some(function(v2) {
					if (!(v1 === v2)) {
						this.cy.addEdge(v1, v2);
						return true;
					}
				}, this);
			}, this);
		},

		makeStar: function(heSet) {
			var v0 = 'h' + this.counter++;
			this.cy.addVertex(v0, this.options.hyperNodeColor);
			var arr = Array.from(heSet);
			heSet.forEach(function(v1) {
				this.cy.addVertex(v1);

			}, this);
			this.cy.addEdge(arr[0], v0, undefined, this.options.hyperEdgeColor);
			this.cy.addEdge(v0, arr[1], undefined, this.options.predicateColor);
			this.cy.addEdge(v0, arr[2], undefined, this.options.hyperEdgeColor);

		},

		drawGraphGeneric: function(queryStr, drawTriple) {
			
			try {
				this.editor.session.clearAnnotations();
				this.drawGraphGenericImpl(queryStr, drawTriple.bind(this));
			} catch (err) {
				console.warn(err);
				if ("hash" in err) {
					this.editor.getSession().setAnnotations([{
						row: err.hash.line,
						column: 0,
						text: err.message,
						type: "error"
					}]);
				}

			}
		},

		drawGraphGenericImpl: function(queryStr, drawTriple) {
			var SparqlParser = sparqljs.Parser;
			var parser = new SparqlParser();
			var parsedQuery = parser.parse(queryStr);
			parsedQuery.where.forEach(function(pat) {
				var __traverse = function(pat) {
					if (pat.type === "bgp") {
						pat.triples.forEach(function(tr) {
							drawTriple(tr);
						});
					} else if (pat.type === "group" ||
							pat.type === "optional" ||
							pat.type === "union" ||
							pat.type === "graph") {
						pat.patterns.forEach(function(pat) {
							__traverse.bind(this)(pat);
						});
					}
					else this.extraRender.traverseFSVB.bind(this)(pat, drawTriple);

				}.bind(this);
				__traverse(pat);
			}, this);

			this.cy.layout({ directed: true, name: this.options.layout });
		},

		drawTripleReg: function(tr) {
			var sub = tr["subject"];
			var prd = toEntity(tr["predicate"]);
			var obj = tr["object"];
			this.cy.addVertex(sub);
			this.cy.addVertex(obj);
			if (this.options.showEdgeLabels) {
				this.cy.addEdge(sub, obj, prd);
			} else {
				this.cy.addEdge(sub, obj);
			}
		},

		drawTripleHE: function(tr) {  
			var sub = tr["subject"];
			var prd = toEntity(tr["predicate"]);
			var obj = tr["object"];




			var heList = [];
			heList.push(sub);
			heList.push(prd);
			heList.push(obj);
			if (!this.options.showConstants) {
				heList = heList.filter(function(val) { return val.match(/^\?/); });
			}
			var heSet = new Set(heList);

			if (heSet.size > 2) {
				this.makeStar(heSet);
			} else {
				this.connectAll(heSet);
			}
		},

		

		drawGraphReg: function(queryStr) {
			this.drawGraphGeneric(queryStr, this.drawTripleReg);
		},

		drawGraphHE: function(queryStr) {
			this.counter = 0;
			this.drawGraphGeneric(queryStr, this.drawTripleHE);
		}
	};
	return SparqlGraph;
}()); 

if (typeof window === 'undefined') {
	module.exports = exports.SparqlGraph;
	module.exports.SparqlGraph = module.exports;
}
