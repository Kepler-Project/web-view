/*
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 22:46:56 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1378 $'
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

import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.provenance.QueryException;
import org.kepler.provenance.Queryable;
import org.kepler.provenance.RecordingException;
import org.kepler.webview.server.WebViewServer;

/** Handler that queries from provenance.
 * 
 *  @author Daniel Crawl
 *  @version $Id: ProvenanceHandler.java 1378 2017-08-24 05:46:56Z crawl $
 *  
 */
public class ProvenanceHandler extends BaseHandler {

    public ProvenanceHandler(WebViewServer server) {
        super(server);
        try {
            _queryable = ProvenanceRecorder.getDefaultQueryable(null);
        } catch (QueryException | RecordingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    protected Queryable _queryable;
}
