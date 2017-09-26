/*
 * Copyright information and license terms for this software can be
 * found in the file LICENSE that is included with the distribution
 */
package org.epics.pvdata;


import junit.framework.TestCase;

import org.epics.pvdata.factory.PVDataFactory;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVDoubleArray;
import org.epics.pvdata.pv.ScalarType;
import org.epics.util.array.ArrayDouble;
import org.epics.util.array.CollectionNumbers;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

public class NumericArrayTest extends TestCase {

    public void testPutDoubleArray1() {
        PVDataCreate factory = PVDataFactory.getPVDataCreate();
        PVDoubleArray pvDoubleArray = (PVDoubleArray) factory.createPVScalarArray(ScalarType.pvDouble);
        assertThat(pvDoubleArray.get(), instanceOf(ArrayDouble.class));
        pvDoubleArray.put(0, CollectionNumbers.toListDouble(0,1,2,3,4,5,6,7,8,9));
        assertThat(pvDoubleArray.get(), equalTo(CollectionNumbers.toListDouble(0,1,2,3,4,5,6,7,8,9)));
        pvDoubleArray.put(2, CollectionNumbers.toListDouble(3,2));
        assertThat(pvDoubleArray.get(), equalTo(CollectionNumbers.toListDouble(0,1,3,2,4,5,6,7,8,9)));
    }
}
