/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-29 15:27:08 -0700 (Tue, 29 Aug 2017) $' 
 * '$Revision: 1392 $'
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

package org.kepler.webview.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ptolemy.data.ArrayToken;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.LongToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.UnsignedByteToken;
import ptolemy.data.type.ArrayType;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.util.IllegalActionException;

public class TokenConverter {

    public TokenConverter() {
        // TODO Auto-generated constructor stub
    }
    
    public static void addConversion(Type type, Converter converter) {
        _conversions.put(type, converter);
    }

    /** Convert data from a Ptolemy token. */
    public Object convertFromToken(Token token) throws IllegalActionException {
        
        Type type = token.getType();
        
        if(type == BaseType.STRING) {
            return ((StringToken)token).stringValue();
        } else if(type == BaseType.DOUBLE) {
            return Double.valueOf(((DoubleToken)token).doubleValue());
        } else if(type == BaseType.INT) {
            return Integer.valueOf(((IntToken)token).intValue());
        } else if(type == BaseType.LONG) {
            return Long.valueOf(((LongToken)token).longValue());
        } else if(type == BaseType.BOOLEAN) {
            return Boolean.valueOf(((BooleanToken)token).booleanValue());
        } else if(type instanceof ArrayType) {
            //System.out.println("array = " + token);
            ArrayToken array = (ArrayToken)token;
            if(array.getElementType() == BaseType.UNSIGNED_BYTE) {
                byte[] bytes = new byte[array.length()];
                for(int i = 0; i < bytes.length; i++) {
                    bytes[i] = ((UnsignedByteToken)array.getElement(i)).byteValue();
                }
                return bytes;
            } else {
                JsonArray json = new JsonArray();
                for(int i = 0; i < array.length(); i++) {
                    json.add(convertFromToken(array.getElement(i)));
                }
                //System.out.println("json = " + json);
                return json;
            }
        } else if(type instanceof RecordType) {
            RecordToken record = (RecordToken)token;
            JsonObject json = new JsonObject();
            for(String name : record.labelSet()) {
                json.put(name, convertFromToken(record.get(name)));
            }
            return json;
        } else {
            Converter converter = _conversions.get(type);
            if(converter != null) {
                return converter.fromToken(token);
            }
        }

        System.err.println("WARNING: unknown type of token for webview conversion: " + type);
        return token.toString();
    }
    
    /** Convert data to a Ptolemy token with the specified type. */
    public Token convertToToken(String data, Type type) throws IllegalActionException {
                
        if(type.equals(BaseType.DOUBLE)) {
            return new DoubleToken(data);
        } else if(type.equals(BaseType.INT)) {
            Double val = Double.parseDouble(data);
            return new IntToken(val.intValue());
        } else if(type.equals(BaseType.LONG)) {
            Double val = Double.parseDouble(data);
            return new LongToken(val.longValue());
        } else if(type.equals(BaseType.STRING)) {
            return new StringToken(data);
        } else {
            Converter converter = _conversions.get(type);
            if(converter != null) {
                return converter.toToken(data, type);
            }
        }
        
        // FIXME throw exception?
        return null;
    }
    
    private final static Map<Type,Converter> _conversions = Collections.synchronizedMap(new HashMap<Type,Converter>());
}
