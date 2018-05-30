/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-24 12:45:52 -0700 (Thu, 24 Aug 2017) $' 
 * '$Revision: 1388 $'
 * 
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the above
 * copyright notice and the following two paragraphs appear in all copies
 * of this software.
 *
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 * FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 * THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 * PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 * CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 * ENHANCEMENTS, OR MODIFICATIONS.
 *
 */
package org.kepler.webview.server.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.kepler.util.WorkflowRun;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.ASTPtRootNode;
import ptolemy.data.expr.ParseTreeEvaluator;
import ptolemy.data.expr.ParserScope;
import ptolemy.data.expr.PtParser;
import ptolemy.data.expr.UndefinedConstantOrIdentifierException;
import ptolemy.data.type.Type;
import ptolemy.graph.InequalityTerm;
import ptolemy.kernel.util.IllegalActionException;

/** Handler to get information about workflow runs.
 * 
 * @author Daniel Crawl
 * @version $Id: RunsHandler.java 1388 2017-08-24 19:45:52Z crawl $
 * 
 */
public class RunsHandler extends ProvenanceHandler {

    public RunsHandler(WebViewServer server) {
        super(server);
    }

    @Override
    public void handle(RoutingContext context) {
        
        /*
        System.out.println("in runs handler");
        for(Entry<String, String> e : context.request().params()) {            
            System.out.println(e.getKey() + " -> " + e.getValue());
        }
        */

        _server.getVertx().<JsonObject>executeBlocking(future -> {
            try {
                JsonArray jsonArray = new JsonArray();
                
                MultiMap params = context.request().params();
                
                String name = params.get("name");
                String parameters = params.get("parameters");

                PtParser parser;
                ASTPtRootNode parseTree = null;
                ParseTreeEvaluator parseTreeEvaluator = null;   
                ParametersScope scope = null;

                // if evaluating parameters expression, create parser objects
                if(parameters != null) {
                    parser = new PtParser();
                    parseTree = parser.generateParseTree(parameters);
                    parseTreeEvaluator = new ParseTreeEvaluator();
                    scope = new ParametersScope();
                }
                
                for(WorkflowRun run : _queryable.getWorkflowRunsForUser(context.user().principal().getString("username"))) {

                    // if search for name, check if run name matches
                    // TODO perform this in queryable
                    if(name != null && !name.equals(run.getWorkflowName())) {
                        continue;
                    }
                    
                    if(parameters != null) {
                        
                        // add the run's parameter names and values to the parser's scope
                        Map<String,String> map = _queryable.getParameterNameValuesOfSpecificTypeForExecution(run.getExecId());                        
                        scope.setParametersValues(map);

                        // attempt to evaluate the expression.
                        Token result = null;
                        try {
                            result = parseTreeEvaluator.evaluateParseTree(parseTree, scope);
                        } catch(UndefinedConstantOrIdentifierException e) {
                            // ignore undefined identifiers since the workflow may not
                            // have a parameter with that name.
                            continue;
                        }
                        
                        // make sure result is a boolean
                        if(!(result instanceof BooleanToken)) {
                           throw new Exception("Parameter search expression must evaluate to true or false."); 
                        }
                        
                        // if expression evaluates to false, do not add this run to the results.
                        if(!((BooleanToken)result).booleanValue()) {
                            continue;
                        }
                    }
                    
                    jsonArray.add(new JsonObject().put("id", run.getExecLSID().toString())
                       .put("start", run.getStartTimeISO8601())
                       .put("status", run.getType())
                       .put("workflowName", run.getWorkflowName()));
                }
                future.complete(new JsonObject().put("runs", jsonArray));

            } catch(Exception e) {
                future.fail(e);
            }
        }, false, result -> {
            if(result.succeeded()) {
                _sendResponseWithSuccessJson(context.request(), result.result());
            } else {
                _sendResponseWithError(context.request(), "Could not get runs: " + result.cause().getMessage());
            }
        });   
    }

    /** A scope for expressions that references parameters from a workflow run. */
    private static class ParametersScope implements ParserScope {

        /** Get the value from an identifier in the expression. */
        @Override
        public Token get(String name) throws IllegalActionException {
            
            // see if the identifier was a parameter in the workflow run
            String value = _map.get(name);
            if(value != null) {
                //System.out.println("found " + name + " = " + value);
                // first try casting value as a double
                try {
                    return new DoubleToken(value);
                } catch(Throwable t) {
                    // default to a string
                    return new StringToken(value);
                }
            }
            
            return null;
        }

        /** Set the parameters from the workflow run. */
        public void setParametersValues(Map<String, String> map) {
            _map.clear();
            for(Map.Entry<String, String> entry : map.entrySet()) {
                // add the parameter name and value.
                // NOTE: remove the first character of the name since it begins with "."
                _map.put(entry.getKey().substring(1), entry.getValue());
            }
        }

        @Override
        public Type getType(String name) throws IllegalActionException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public InequalityTerm getTypeTerm(String name) throws IllegalActionException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Set<?> identifierSet() throws IllegalActionException {
            // TODO Auto-generated method stub
            return null;
        }
        
        private Map<String, String> _map = new HashMap<>();
    }
    
}
