/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvData.pvCopy;

import java.util.regex.Pattern;

import org.epics.pvData.factory.PVDataFactory;
import org.epics.pvData.pv.Field;
import org.epics.pvData.pv.PVDataCreate;
import org.epics.pvData.pv.PVString;
import org.epics.pvData.pv.PVStructure;
import org.epics.pvData.pv.ScalarType;

/**
 * @author mrk
 *
 */
class CreateRequestImpl {
    private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    private static final Pattern commaPattern = Pattern.compile("[,]");
    private static final Pattern equalPattern = Pattern.compile("[=]");
    
	
	static PVStructure createRequest(String request) {
		if(request!=null) request = request.trim();
    	if(request==null || request.length()<=0) {
    		PVStructure pvStructure =  pvDataCreate.createPVStructure(null,"", new Field[0]);
    		return pvStructure;
    	}
    	if(request.startsWith("record") || request.startsWith("field") || request.startsWith("putField")|| request.startsWith("getField")) {
    		return createBigRequest(request);
    	}
    	PVStructure pvStructure =  pvDataCreate.createPVStructure(null,"", new Field[0]);
    	createFieldRequest(pvStructure,request,true);
    	return pvStructure;
	}
	
	private static PVStructure createBigRequest(String request) {
		if(request!=null) request = request.trim();
    	PVStructure pvStructure =  pvDataCreate.createPVStructure(null,"", new Field[0]);
    	if(request==null || request.length()<=0)  return pvStructure;
    	if(request.startsWith("record")) {
    		int indLeft = request.indexOf('[');
    		int indRight = request.indexOf(']');
    		if(indLeft<=0 | indRight<=0) {
    			throw new IllegalArgumentException(request + " no [] for record");
    		}
    		PVStructure pvStruct = pvDataCreate.createPVStructure(pvStructure, "record", new Field[0]);
    		createRequestOptions(pvStruct,request.substring(indLeft+1, indRight));
    		pvStructure.appendPVField(pvStruct);
    		request = request.substring(indRight+1);
    	}
    	while(true) {
    		request = request.trim();
    		if(request.length()==0) break;
    		String fieldName = null;
    		if(request.startsWith("field")) {
    			fieldName = "field";
    		} else if(request.startsWith("putField")) {
    			fieldName = "putField";
    		} else if(request.startsWith("getField")) {
    			fieldName = "getField";
    		} else {
    			throw new IllegalArgumentException(request + "must start with field or putField or getField");
    		}
    		int indStart = request.indexOf('(');
    		int indEnd = request.indexOf(')');
    		PVStructure pvStruct = pvDataCreate.createPVStructure(pvStructure,fieldName, new Field[0]);
    		createFieldRequest(pvStruct,request.substring(indStart+1,indEnd),true);
    		pvStructure.appendPVField(pvStruct);
    		request = request.substring(indEnd+1);
    	}
    	return pvStructure;
	}

	private static int findMatchingBrace(String request,int index) {
		int closeBrace = request.indexOf('}', index+1);
		if(closeBrace==-1) return -1;
		int openBrace = request.indexOf('{', index+1);
		if(openBrace==-1) return closeBrace;
		if(openBrace>closeBrace) return closeBrace;
		int nextClose = request.indexOf('}', openBrace+1);
		if(nextClose==-1) return -1;
		return findMatchingBrace(request,nextClose);
	}
    private static void createFieldRequest(PVStructure pvParent,String request,boolean fieldListOK) {
    	request = request.trim();
    	if(request.length()<=0) return;
    	int comma = request.indexOf(',');
    	int openBrace = request.indexOf('{');
    	int openBracket = request.indexOf('[');
    	if(openBrace>=0 || openBracket>=0) fieldListOK = false;
    	if(openBrace>=0 && (comma==-1 || comma>openBrace)) {
    		//find matching brace
    		int closeBrace = findMatchingBrace(request,openBrace);
    		if(closeBrace==-1) {
    			throw new IllegalArgumentException(request + "mismatched { }");
    		}
    		String fieldName = request.substring(0,openBrace);
    		PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
    		createFieldRequest(pvStructure,request.substring(openBrace+1,closeBrace),false);
    		pvParent.appendPVField(pvStructure);
    		if(request.length()>closeBrace+1) {
    			if(request.charAt(closeBrace+1) != ',') {
    				throw new IllegalArgumentException(request + "misssing , after }");
    			}
    			createFieldRequest(pvParent,request.substring(closeBrace+2),false);
    		}
    		return;
    	}
    	if(openBracket==-1 && fieldListOK) {
    			PVString pvString = (PVString)pvDataCreate.createPVScalar(pvParent, "fieldList", ScalarType.pvString);
    			pvString.put(request);
    			pvParent.appendPVField(pvString);
    			return;
    	}
    	if(openBracket!=-1 && (comma==-1 || comma>openBracket)) {
    		int closeBracket = request.indexOf(']');
			if(closeBracket==-1) {
				throw new IllegalArgumentException(request + "option does not have matching []");
			}
			if(request.lastIndexOf(',')>closeBracket) {
				createLeafFieldRequest(pvParent,request.substring(0, closeBracket+1));
				int nextComma = request.indexOf(',', closeBracket);
				createFieldRequest(pvParent,request.substring(nextComma+1),false);
			}
			return;
    	}
    	if(comma!=-1) {
    		createLeafFieldRequest(pvParent,request.substring(0, comma));
    		createFieldRequest(pvParent,request.substring(comma+1),false);
    		return;
    	}
        createLeafFieldRequest(pvParent,request);
    }
   
    public static void createLeafFieldRequest(PVStructure pvParent,String request) {
    	int openBracket = request.indexOf('[');
    	String fullName = request;
    	if(openBracket>=0) fullName = request.substring(0,openBracket);
    	int indLast = fullName.lastIndexOf('.');
		String fieldName = fullName;
		if(indLast>1) fieldName = fullName.substring(indLast+1);
    	PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
		PVStructure pvLeaf = pvDataCreate.createPVStructure(pvStructure,"leaf", new Field[0]);
		PVString pvString = (PVString)pvDataCreate.createPVScalar(pvLeaf, "source", ScalarType.pvString);
		pvString.put(fullName);
		pvLeaf.appendPVField(pvString);
		if(openBracket>0) {
			int closeBracket = request.indexOf(']');
			if(closeBracket==-1) {
				throw new IllegalArgumentException(request + "option does not have matching []");
			}
			createRequestOptions(pvLeaf,request.substring(openBracket+1, closeBracket));
		}
		pvStructure.appendPVField(pvLeaf);
		pvParent.appendPVField(pvStructure);
    }
    
    private static void createRequestOptions(PVStructure pvParent,String request) {
		request = request.trim();
		if(request.length()<=1) return;
    	String[] items = commaPattern.split(request);
    	for(int j=0; j<items.length; j++) {
    		String[] names = equalPattern.split(items[j]);
    		if(names.length!=2) {
    			throw new IllegalArgumentException(request + "illegal field name " + request);
    		}
    		PVString pvString = (PVString)pvDataCreate.createPVScalar(pvParent, names[0], ScalarType.pvString);
    		pvString.put(names[1]);
    		pvParent.appendPVField(pvString);
        }
    	return;
    }
}
