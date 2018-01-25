/*
 * Copyright (c) 2018 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:58:25 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1406 $'
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
 */
package org.kepler.webview.server.app;

import java.io.InputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/** App to perform HTTP GET and return body. Useful for sites
 *  without CORS or JSONP.
 *
 * @author Daniel Crawl
 */
public class GetURL implements App {

    /** Do nothing. */
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public JsonArray exec(User user, JsonObject inputs) throws Exception {
        
        if(!inputs.containsKey("url")) {
            throw new Exception("Missing url parameter.");
        }
        
        JsonObject data = new JsonObject();
        JsonArray responses = new JsonArray().add(data);
        
        String urlString = inputs.getString("url");

        // FIXME would like to use vertx HttpClient, but requires response handler
        
        HttpClient client = new HttpClient();
        GetMethod method = new GetMethod(urlString);

        try {
            int status = client.executeMethod(method);
            
            data.put("status", status);
            
            try(InputStream stream = method.getResponseBodyAsStream()) {           
                data.put("body", IOUtils.toString(stream));
            }
                    
        } finally {
            method.releaseConnection();
        }                
        
        return responses;
    }

}
