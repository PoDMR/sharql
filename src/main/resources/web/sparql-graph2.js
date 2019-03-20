exports.SparqlGraph2 = (function() {
	function SparqlGraph2(parent) {
		this.parent = parent;
	}

	SparqlGraph2.prototype = {
		
		traverseFSVB: function(pat, act) {
			act = function(a) {
				var sub = a[0];
				var obj = a[1];
				this.cy.addVertex(sub);
				this.cy.addVertex(obj);
				this.cy.addEdge(sub, obj);
			}.bind(this);

			function cont(a, act) {
				var f = a.filter(function(e) {
					return e.startsWith("?");
				});
				if (f.length === 2) {
					act(f);
				}
			}

			function traverseExpr(exp) {
				if (typeof (exp) == "object") {
					if (exp.type === "operation") {  
						return exp.args.flatMap(function(e) {
							return traverseExpr(e);
						});
					}
				} else {
					return exp;
				}
			}

			function traverseService(pat) {
				return pat.flatMap(function(pat) {
					if (pat.type === "bgp") {
						return pat.triples.flatMap(function(e) {
							return [e.subject, e.predicate, e.object];
						});
					}
				});
			}

			if (pat.type === "filter") {
				cont(traverseExpr(pat.expression), act);
			} else if (pat.type === "service") {
				cont(traverseService(pat.patterns), act);  
			} else if (pat.type === "bind") {
				cont([pat.variable].concat(traverseExpr(pat.expression)), act);
			} else if (pat.type === "values") {
				cont(Array.from(new Set(pat.values.flatMap(function(e) {
					return Object.keys(e);
				}))), act);
			}
		},


		
		drawGraphHybrid: function(queryStr) {
			var cy = this.parent.cy;
			if (!this.executed) {
				this.executed = true;
				this.hes = [];
				cy.on("render", ev => {
					this.clearCanvas();
					this.hes.forEach(heIds => this.envelope(heIds, ev.cy));
				});

				var hes = this.hes;
				var customReset = this.parent.cy.customReset.bind(this.parent.cy);
				this.parent.cy.customReset = function() {
					hes.splice(0, hes.length);
					customReset();
				};
			}
			var parsedQuery = this.tryParse(queryStr);
			if (parsedQuery) {
				this.hybridTraverseWhere(parsedQuery.where);
				this.hes.splice(0, this.hes.length);
				cy.layout({directed: true, name: this.parent.options.layout});
				this.applyStyle(cy);
			}
		},

		tryParse: function(queryStr) {
			var editor = this.parent.editor;
			try {
				editor.session.clearAnnotations();
				var SparqlParser = sparqljs.Parser;
				var parser = new SparqlParser();
				return parser.parse(queryStr);
			} catch (err) {
				console.warn(err);
				if ("hash" in err) {
					editor.getSession().setAnnotations([{
						row: err.hash.line,
						column: 0,
						text: err.message,
						type: "error"
					}]);
				}

			}
			return undefined;
		},

		applyStyle: function(cy) {

			cy.style().selector(".nolabel").style({
				'line-style': 'dashed',
				'text-opacity': 0
			}).update();

			cy.style().selector(".hoverlabel, .showlabel").style({
				'line-style': 'solid',
				'text-opacity': "1"
			}).update();
			cy.edges().on("mouseover", e => e.cyTarget.addClass("hoverlabel").addClass("showlabel"));
			cy.edges().on("mouseout", e => e.cyTarget.removeClass("hoverlabel").removeClass("showlabel"));
		},

		hybridTraverseWhere: function(where) {
			var soVars = new Set();
			var predList = [];
			var collected = where.flatMap(this.hybridTraversePat, this);
			collected.forEach(function(vs) {
				vs.vars = vs.filter(function(v) {
					return v.startsWith("?");
				});
				vs.maybeReg = !(vs.vars.length > 3 || (vs.he && vs.vars.length > 2));
				if (!vs.maybeReg) {
					this.makeHe(vs.vars);
					vs.vars.forEach(soVars.add, soVars);  
				} else {
					if (vs[0] && vs[0].startsWith("?")) {
						soVars.add(vs[0]);
					}
					if (vs[1] && vs[1].startsWith("?")) {
						soVars.add(vs[1]);
					}
					if (vs[2] && vs[2].startsWith("?")) {
						predList.push(vs[2]);
					}
				}
			}, this);
			collected.forEach(function(vs) {
				if (vs.maybeReg) {

					if (!(vs[2] && (soVars.has(vs[2]) || predList
						.filter(function(v) {
							return v === vs[2];
						}).length > 1))) {
						if (!vs.he) {
							this.drawEdge(vs);
						} else {
							this.drawEdge(vs.vars, true);
						}
					} else {
						this.makeHe(vs.vars);  
					}
				}
			}, this);
		},

		hybridTraversePat: function(pat) {
			var traverseExpr = function(exp) {
				if (exp['args']) {  
					return exp.args.flatMap(traverseExpr);
				} else if (exp.triples) {  
					return exp.triples.flatMap(this.triple2array);  
				} else if (typeof(exp) == "string") {
					return [exp];
				} else {
					return [];
				}
			}.bind(this);

			if (pat.type === "bgp") {
				return pat.triples.map(this.triple2array);
			} else if (pat.type === "group" ||
				pat.type === "optional" ||
				pat.type === "union" ||
				pat.type === "graph") {
				return pat.patterns.flatMap(this.hybridTraversePat, this);
			} else if (pat.type === "filter") {
				var expr = traverseExpr(pat.expression);
				expr.he = true;
				return [expr];
			} else if (pat.type === "service") {

				var serv = [pat.name].concat(pat.patterns.flatMap(this.hybridTraversePat, this).flatMap(e => e));
				serv.he = true;
				return [serv];
			} else if (pat.type === "bind") {
				var bound = [pat.variable].concat(traverseExpr(pat.expression));
				bound.he = true;
				return [bound];
			} else if (pat.type === "values") {
				var vals = Array.from(new Set(pat.values.flatMap(Object.keys)));
				vals.he = true;
				return [vals];
			} else if (pat.type === "query") {
				return pat.where.flatMap(this.hybridTraversePat, this);
			} else {
				return [];
			}
		},

		triple2array: function(tr) {
			return [tr["subject"], tr["object"], this.parent.toEntity(tr["predicate"])];
		},

		drawEdge: function(vs, withColor) {
			if (vs[0]) {
				this.parent.cy.addVertex(vs[0]);
			}
			if (vs[1]) {
				this.parent.cy.addVertex(vs[1]);
			}
			if (vs[0] && vs[1]) {

				var color = withColor ?
					this.stringToColor(vs[0] + "," + vs[1])
						.replace(/(,[\d.]+%?)\)/, ")", "$1") : undefined;

				var en = this.parent.cy.addEdge(vs[0], vs[1], vs[2], color);
				if (vs[2] && !vs[2].startsWith("?")) {
					this.parent.cy.edges("[id='" + en + "']").addClass('nolabel');
				}
			}
		},

		drawSet: function(vs) {

			if (vs.length > 0) {
				this.parent.cy.addVertex(vs[0]);
			}

			for (var i = 1; i < vs.length - 1; i++) {
				this.parent.cy.addVertex(vs[i]);
				this.parent.cy.addVertex(vs[i + 1]);
				var color = this.parent.options.hyperEdgeColor;
				if (i === 1) {
					this.hideEdge(this.parent.cy.addEdge(vs[0], vs[i], undefined, color));
				}
				if (i + 1 === vs.length - 1) {
					this.hideEdge(this.parent.cy.addEdge(vs[0], vs[i + 1], undefined, color));
				}
				this.hideEdge(this.parent.cy.addEdge(vs[i + 1], vs[i], undefined, color));
			}
		},

		makeHe: function(vs) {
			this.drawSet(vs);
			this.hes.push(vs);
		},

		hideEdge: function(id) {
			this.parent.cy.style().selector("[id='" + id + "']").style({
				'visibility': 'hidden'
			}).update();
		},

		envelope: function(heIds, cy) {
			function center(ps) {
				var x = 0, y = 0;
				for (var i = 0; i < ps.length; i++) {
					x += ps[i].x;
					y += ps[i].y;
				}
				return {x: x / ps.length, y: y / ps.length};
			}

			function outer(ps, m) {
				return ps.map(function(p) {
					var x = p.x - m.x;
					var y = p.y - m.y;
					var l = Math.sqrt((x * x) + (y * y));
					return {
						x: m.x + x + (x / l * 20),
						y: m.y + y + (y / l * 20),
					};
				});
			}

			var ps = heIds.map(function(id) {
				var nodes = cy.nodes("[id='" + id + "']");
				return nodes.length > 0 ? nodes.position() : [];
			});
			var m = center(ps);
			var canvas = this.getCanvas();
			var ctx = canvas.getContext("2d");


			ps = outer(ps, m);
			ctx.lineJoin = "round";
			ctx.lineWidth = 6;





			ctx.strokeStyle = this.stringToColor(heIds.join());
			ctx.fillStyle = this.stringToColor(heIds.join());
			ctx.beginPath();
			this.drawEnvelope(ctx, ps);
			ctx.closePath();
			ctx.fill();
			ctx.stroke();
		},

		drawEnvelope: function(ctx, ps) {
			var l = ps.length;
			if (l > 0) {
				ctx.moveTo(ps[0].x, ps[0].y);
			}
			for (var i = 0; i < l; i++) {
				let idx = (i + 1) % l;
				var cp = this.controlPoints(ps, idx - 1);
				ctx.bezierCurveTo(
					cp[0].x, cp[0].y,
					cp[1].x, cp[1].y,
					ps[idx].x, ps[idx].y);

			}
		},

		controlPoints: function(ps, i) {
			var l = ps.length;

			var x0 = ps[(l + i - 1) % l].x;
			var y0 = ps[(l + i - 1) % l].y;
			var x1 = ps[(l + i) % l].x;
			var y1 = ps[(l + i) % l].y;
			var x2 = ps[(i + 1) % l].x;
			var y2 = ps[(i + 1) % l].y;
			var x3 = ps[(i + 2) % l].x;
			var y3 = ps[(i + 2) % l].y;

			var xc1 = (x0 + x1) / 2.0;
			var yc1 = (y0 + y1) / 2.0;
			var xc2 = (x1 + x2) / 2.0;
			var yc2 = (y1 + y2) / 2.0;
			var xc3 = (x2 + x3) / 2.0;
			var yc3 = (y2 + y3) / 2.0;

			var len1 = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
			var len2 = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
			var len3 = Math.sqrt((x3 - x2) * (x3 - x2) + (y3 - y2) * (y3 - y2));

			var k1 = len1 / (len1 + len2);  
			var k2 = len2 / (len2 + len3);

			var xm1 = xc1 + (xc2 - xc1) * k1;
			var ym1 = yc1 + (yc2 - yc1) * k1;
			var xm2 = xc2 + (xc3 - xc2) * k2;
			var ym2 = yc2 + (yc3 - yc2) * k2;
			var sf = 0.5;

			var cp1_x = xm1 + (xc2 - xm1) * sf + x1 - xm1;
			var cp1_y = ym1 + (yc2 - ym1) * sf + y1 - ym1;
			var cp2_x = xm2 + (xc2 - xm2) * sf + x2 - xm2;
			var cp2_y = ym2 + (yc2 - ym2) * sf + y2 - ym2;
			return [{x: cp1_x, y: cp1_y}, {x: cp2_x, y: cp2_y}];
		},

		getCanvas: function() {
			return this.parent.cy.container().children[0].children[0];
		},

		clearCanvas: function() {
			var canvas = this.getCanvas();
			var ctx = canvas.getContext("2d");
			ctx.save();
			ctx.setTransform(1, 0, 0, 1, 0, 0);
			ctx.clearRect(0, 0, canvas.width, canvas.height);
			ctx.restore();
		},

		stringToColor: function(str) {
			if (!str) return "#000000";
			var hash = 0;
			for (var i = 0; i < str.length; i++) {

				hash = str.charCodeAt(i) + ((hash << 4) - hash);
			}






			return "hsla(" + (hash * (360 / (2 << 4)) % 360) + ",100%,50%,50%)";
		},

		decompose: function(p, options) {
			var queryStr = p.queryStr;
			var endpointBase = p['endpointBase'];  
			var xhr = new XMLHttpRequest();
			var endpoint = '/htd';
			if (endpointBase) {
				endpoint = endpointBase + endpoint;
			} else {
				if (window.location.hash) {
					var params = window.location.hash.substr(1)
						.split("&")
						.map(function(el) {
							return el.split("=");
						}).reduce(function(l, r) {
							l[r[0]] = r[1];
							return l;
						}, {});
					if (params['url']) {
						endpointBase = params['url'];
						endpoint = endpointBase + endpoint;
					}
				}
			}
			xhr.open('POST', endpoint);
			xhr.setRequestHeader('Content-Type', 'application/json');
			var that = this;
			xhr.onload = function() {
				if (xhr.status === 200) {
					var o = JSON.parse(xhr.responseText);
					that.drawDecomposition(o, options);
				}
			};
			xhr.send(JSON.stringify({
				queryStr: queryStr
			}));
		},


		drawDecomposition: function(o, options) {
			var edgeList = o['edges'];
			var labelDict = o['labels'];
			var cy = this.parent.cy;
			cy.customReset();
			edgeList.forEach(function(p) {
				let v1 = labelDict[p[0]];
				let v2 = labelDict[p[1]];
				if (!v1) v1 = p[0];
				if (!v2) v2 = p[1];
				if (v1) cy.addVertex(v1);
				if (v2) cy.addVertex(v2);
				if (v1 && v2) {
					cy.addEdge(v1, v2);
				}
			});
			options.layout = 'breadthfirst';
			cy.layout({directed: true, name: this.parent.options.layout});
		},
	};

	return SparqlGraph2;
}());

if (typeof window === 'undefined') {
	module.exports = exports.SparqlGraph2;
	module.exports.SparqlGraph2 = module.exports;
}
