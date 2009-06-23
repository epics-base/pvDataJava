/**
 * 
 */
package org.epics.pvData.factory;

import java.nio.ByteBuffer;

import org.epics.pvData.misc.SerializeHelper;
import org.epics.pvData.pv.MessageType;
import org.epics.pvData.pv.PVString;
import org.epics.pvData.pv.PVStructure;
import org.epics.pvData.pv.Scalar;

/**
 * Base class for PVString.
 * It provides a complete implementation can be extended.
 * @author mrk
 *
 */
public class BasePVString extends AbstractPVScalar implements PVString
{
    protected String value;
    
    public BasePVString(PVStructure parent,Scalar scalar) {
        super(parent,scalar);
        value = null;
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.PVString#get()
     */
    @Override
    public String get() {
        return value;
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.PVString#put(java.lang.String)
     */
    @Override
    public void put(String value) {
        if(super.isImmutable()) {
            super.message("field is immutable", MessageType.error);
            return;
        }
        this.value = value;
        super.postPut();
        
    }
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return toString(0);
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.factory.AbstractPVField#toString(int)
     */
    @Override
    public String toString(int indentLevel) {
        return convert.getString(this, indentLevel)
        + super.toString(indentLevel);
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.Serializable#serialize(java.nio.ByteBuffer)
     */
    @Override
    public void serialize(ByteBuffer buffer) {
        SerializeHelper.serializeString(value, buffer);
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.SerializableArray#serialize(java.nio.ByteBuffer, int, int)
     */
    @Override
	public void serialize(ByteBuffer buffer, int offset, int count) {
		// check bounds
    	final int length = (value == null) ? 0 : value.length();
		if (offset < 0) offset = 0;
		else if (offset > length) offset = length;
		if (count < 0) count = length;

		final int maxCount = length - offset;
		if (count > maxCount)
			count = maxCount;
		
		// write
		SerializeHelper.serializeSubstring(value, offset, count, buffer);
	}
	/* (non-Javadoc)
     * @see org.epics.pvData.pv.Serializable#deserialize(java.nio.ByteBuffer)
     */
    @Override
    public void deserialize(ByteBuffer buffer) {
        value = SerializeHelper.deserializeString(buffer);
    }
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        // TODO anything else?
        if (obj instanceof PVString) {
            PVString b = (PVString)obj;
            final String bv = b.get();
            if (bv != null)
                return bv.equals(value);
            else
                return bv == value;
        }
        else
            return false;
    }
}
