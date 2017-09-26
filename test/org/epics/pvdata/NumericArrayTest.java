/*
 * Copyright information and license terms for this software can be
 * found in the file LICENSE that is included with the distribution
 */
package org.epics.pvdata;


import junit.framework.TestCase;

import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.PVIntArray;
import org.epics.pvdata.pv.PVNumberArray;
import org.epics.pvdata.pv.ScalarType;
import org.epics.util.array.ArrayDouble;
import org.epics.util.array.ArrayInt;
import org.epics.util.array.CollectionNumbers;
import org.epics.util.array.ListNumber;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

public class NumericArrayTest extends TestCase {

    public void testPutDoubleArray1() {
        PVDataCreate factory = PVDataFactory.getPVDataCreate();
        PVDoubleArray pvArray = (PVDoubleArray) factory.createPVScalarArray(ScalarType.pvDouble);
        assertThat(pvArray.get(), instanceOf(ArrayDouble.class));
        pvArray.put(0, CollectionNumbers.toListDouble(0,1,2,3,4,5,6,7,8,9));
        assertThat(pvArray.get(), equalTo(CollectionNumbers.toListDouble(0,1,2,3,4,5,6,7,8,9)));
        pvArray.put(2, CollectionNumbers.toListDouble(3,2));
        assertThat(pvArray.get(), equalTo(CollectionNumbers.toListDouble(0,1,3,2,4,5,6,7,8,9)));
    }

    public void testPutIntArray1() {
        PVDataCreate factory = PVDataFactory.getPVDataCreate();
        PVIntArray pvArray = (PVIntArray) factory.createPVScalarArray(ScalarType.pvInt);
        assertThat(pvArray.get(), instanceOf(ArrayInt.class));
        pvArray.put(0, CollectionNumbers.toListInt(0,1,2,3,4,5,6,7,8,9));
        assertThat(pvArray.get(), equalTo(CollectionNumbers.toListInt(0,1,2,3,4,5,6,7,8,9)));
        pvArray.put(2, CollectionNumbers.toListInt(3,2));
        assertThat(pvArray.get(), equalTo(CollectionNumbers.toListInt(0,1,3,2,4,5,6,7,8,9)));
    }

    public void testPutNumericArray1() {
        PVDataCreate factory = PVDataFactory.getPVDataCreate();
        PVNumberArray pvArray = (PVNumberArray) factory.createPVScalarArray(ScalarType.pvInt);
        assertThat(pvArray.get(), instanceOf(ArrayInt.class));
        pvArray.put(0, CollectionNumbers.toListInt(0,1,2,3,4,5,6,7,8,9));
        assertThat(pvArray.get(), equalTo((ListNumber) CollectionNumbers.toListInt(0,1,2,3,4,5,6,7,8,9)));
        pvArray.put(2, CollectionNumbers.toListInt(3,2));
        assertThat(pvArray.get(), equalTo((ListNumber) CollectionNumbers.toListInt(0,1,3,2,4,5,6,7,8,9)));
    }
}
