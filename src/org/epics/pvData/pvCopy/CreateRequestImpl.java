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
	
	private static void createRequestOptions(PVStructure pvParent,String request) {
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
    
    private static void createFieldRequest(PVStructure pvParent,String request,boolean fieldListOK) {
    	request = request.trim();
    	if(request.length()<=0) return;
    	int firstBrace = request.indexOf('{');
    	if(firstBrace>=0) {
    		int firstComma = request.indexOf(',');
    		while(firstComma!=-1 && firstComma<firstBrace) {
    			String firstPart = request.substring(0, firstComma);
    			createFieldRequest(pvParent,firstPart,false);
    			request = request.substring(firstComma+1);
    			firstBrace = request.indexOf('{');
    			firstComma = request.indexOf(',');
    		}
    		String fieldName = request.substring(0,firstBrace);
    		PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
    		int indRight = firstBrace + 1;
    		int numLeft = 1;
    		int numRight = 0;
    		while(true) { // find matching right brace for start brace
    			int indLeft = request.indexOf('{', indRight);
    			indRight = request.indexOf('}', indRight);
    			if(indRight==-1) {
    				throw new IllegalArgumentException(request + "mismatched { }");
    			}
    			if(indLeft!=-1 && indLeft<indRight) {
    				numLeft++;
    				indRight = indLeft + 1;
    				continue;
    			}
    			numRight++;
    			if(numRight==numLeft) break;
    			indRight++;
    		}
    		createFieldRequest(pvStructure,request.substring(firstBrace+1, indRight),false);
    		pvParent.appendPVField(pvStructure);
    		if(request.length()<=indRight+2) return;
    		createFieldRequest(pvParent,request.substring(indRight+2),false);
    		return;
    	}
    	int openBracket = request.indexOf('[');
    	if(openBracket==-1) {
    		if(fieldListOK) {
    			PVString pvString = (PVString)pvDataCreate.createPVScalar(pvParent, "fieldList", ScalarType.pvString);
    			pvString.put(request);
    			pvParent.appendPVField(pvString);
    		} else {
    			String[] fullFields = commaPattern.split(request);
    			for(int i=0; i<fullFields.length; i++) {
    				String fullName = fullFields[i];
    				int indLast = fullName.lastIndexOf('.');
    				String fieldName = fullName;
    				if(indLast>1) fieldName = fullName.substring(indLast+1);
    				PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
    				PVString pvString = (PVString)pvDataCreate.createPVScalar(pvStructure, "leaf", ScalarType.pvString);
    				pvString.put(fullName);
    				pvStructure.appendPVField(pvString);
     				pvParent.appendPVField(pvStructure);
    			}
    		}
    		return;
    	}
    	int firstComma = request.indexOf(',');
    	while(firstComma!=-1 && firstComma<openBracket) {
    		String fullName = request.substring(0,firstComma);
    		int indLast = fullName.lastIndexOf('.');
    		String fieldName = fullName;
    		if(indLast>1) fieldName = fullName.substring(indLast+1);
    		PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
    		PVString pvString = (PVString)pvDataCreate.createPVScalar(pvStructure, "leaf", ScalarType.pvString);
    		pvString.put(fullName);
    		pvStructure.appendPVField(pvString);
    		pvParent.appendPVField(pvStructure);
    		request = request.substring(firstComma+1);
    		openBracket = request.indexOf('[');
    		firstComma = request.indexOf(',');
    	}
    	int closeBracket = request.indexOf(']');
    	if(closeBracket==-1) {
    		throw new IllegalArgumentException(request + "option does not have matching []");
    	}
    	String fullName = request.substring(0,openBracket);
    	int indLast = fullName.lastIndexOf('.');
    	String fieldName = fullName;
    	if(indLast>1) fieldName = fullName.substring(indLast+1);
    	PVStructure pvStructure = pvDataCreate.createPVStructure(pvParent, fieldName, new Field[0]);
    	PVString pvString = (PVString)pvDataCreate.createPVScalar(pvStructure, "leaf", ScalarType.pvString);
    	pvString.put(fullName);
    	pvStructure.appendPVField(pvString);
    	createRequestOptions(pvStructure,request.substring(openBracket+1, closeBracket));
    	pvParent.appendPVField(pvStructure);
    	if(request.length()>0) createFieldRequest(pvParent,request.substring(closeBracket+2),false);
    	return;
    } 
}
