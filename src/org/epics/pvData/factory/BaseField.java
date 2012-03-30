/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvData.factory;

import org.epics.pvData.pv.Field;
import org.epics.pvData.pv.Type;

/**
 * Base class for creating a Field.
 * It can also be a complete implementation.
 * @author mrk
 */
public abstract class BaseField implements Field
{
    private final Type type;

    /**
     * Constructor for BaseField.
     * @param fieldName The field fieldName.
     * @param type The field type.
     * @throws IllegalArgumentException if type is null;
     */
    public BaseField(Type type) {
        if(type==null) {
            throw new IllegalArgumentException("type is null");
        }
        this.type = type;
    }   
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.Field#getType()
     */
    public Type getType() {
        return type;
    }
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.Field#toString(java.lang.StringBuilder, int)
     */
    @Override
    public abstract void toString(StringBuilder buf, int indentLevel);
    /* (non-Javadoc)
     * @see org.epics.pvData.pv.Field#toString(java.lang.StringBuilder)
     */
    @Override
    public void toString(StringBuilder buf) {
        toString(buf,0);
    }
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        toString(builder);
        return builder.toString();
    }
}
