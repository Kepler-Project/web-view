/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-07-01 16:08:56 -0700 (Sat, 01 Jul 2017) $' 
 * '$Revision: 1242 $'
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
package org.kepler.webview.server.auth;

import java.util.HashSet;
import java.util.Set;

import io.vertx.ext.auth.User;

/** Utilities for authentication. 
 * 
 *  @author Daniel Crawl
 *  @version $Id: AuthUtilities.java 1242 2017-07-01 23:08:56Z crawl $
 */
public class AuthUtilities {

    /** Get the groups for a vertx user. 
     * @param user The vertx user.
     * @return a set of group names that the user belongs to.
     */
    public static Set<String> getGroups(User user) {
        
        Set<String> groupSet = new HashSet<String>();        
        String groups = user.principal().getString("groups");
        if(groups != null) {
            for(String group : groups.split(",")) {
                groupSet.add(group.trim());
            }
        }
        return groupSet;
    }
}
