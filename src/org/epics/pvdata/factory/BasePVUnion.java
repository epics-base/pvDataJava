/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvdata.factory;

import java.nio.ByteBuffer;

import org.epics.pvdata.misc.SerializeHelper;
import org.epics.pvdata.pv.DeserializableControl;
import org.epics.pvdata.pv.Field;
import org.epics.pvdata.pv.FieldCreate;
import org.epics.pvdata.pv.PVDataCreate;
import org.epics.pvdata.pv.PVField;
import org.epics.pvdata.pv.PVUnion;
import org.epics.pvdata.pv.SerializableControl;
import org.epics.pvdata.pv.Union;

/**
 * Base class for a PVUnion.
 * @author mse
 */
public class BasePVUnion extends AbstractPVField implements PVUnion
{
    private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    private static final FieldCreate fieldCreate = FieldFactory.getFieldCreate();

    private final Union union;
	private int selector = UNDEFINED_INDEX;
	private PVField value = null;
	private final boolean variant;

	/**
     * Constructor.
     * @param union the reflection interface for the PVUnion data.
     */
    public BasePVUnion(Union union) {
        super(union);
        this.union = union;
        variant = (union.getFields().length == 0);
    }

	@Override
	public Union getUnion() {
		return union;
	}

	@Override
	public PVField get() {
		return value;
	}

	@Override
	public int getSelectedIndex() {
		return selector;
	}

	@Override
	public String getSelectedFieldName() {
		// no name for undefined and for variant unions
		if (selector == UNDEFINED_INDEX)
			return null;
		else
			return union.getFieldName(selector);
	}

	@Override
	public PVField select(int index) {
		// no change
		if (selector == index)
			return value;
			
		if (index == UNDEFINED_INDEX)
		{
			selector = UNDEFINED_INDEX;
			value = null;
			return null;
		}
		else if (variant)
			throw new IllegalArgumentException("index out of bounds");
		else if (index < 0 || index > union.getFields().length)
			throw new IllegalArgumentException("index out of bounds");
		
		Field field = union.getField(index);
		selector = index;
		value = pvDataCreate.createPVField(field);

		return value;
	}

	@Override
	public PVField select(String fieldName) {
		int index = variant ? -1 : union.getFieldIndex(fieldName);
		if (index == -1)
			throw new IllegalArgumentException("no such fieldName");

		return select(index);
	}

	@Override
	public void set(PVField value) {
		set(selector, value);
	}

	@Override
	public void set(int index, PVField value) {
		if (variant && index != UNDEFINED_INDEX)
			throw new IllegalArgumentException("index out of bounds");
		else if (!variant)
		{
			if (index == UNDEFINED_INDEX)
			{
				// for undefined index we accept only null values
				if (value != null)
					throw new IllegalArgumentException("non-null value for index == UNDEFINED_INDEX");
			}
			else if (index < 0 || index > union.getFields().length)
				throw new IllegalArgumentException("index out of bounds");

			// value type must match
			if (!value.getField().equals(union.getField(index)))
				throw new IllegalArgumentException("selected field and its introspection data do not match");
		}
		
		this.selector = index;
		this.value = value;
	}

	@Override
	public void set(String fieldName, PVField value) {
		int index = variant ? -1 : union.getFieldIndex(fieldName);
		if (index == -1)
			throw new IllegalArgumentException("no such fieldName");

		set(index, value);
	}

	/* (non-Javadoc)
	 * @see org.epics.pvdata.pv.Serializable#serialize(java.nio.ByteBuffer, org.epics.pvdata.pv.SerializableControl)
	 */
	public void serialize(ByteBuffer buffer, SerializableControl flusher) {
		if (variant)
		{
			// write introspection data
			if (value == null)
				buffer.put((byte)-1);
			else
			{
				flusher.cachedSerialize(value.getField(), buffer);
				value.serialize(buffer, flusher);
			}
		}
		else
		{
			// write selector value
			SerializeHelper.writeSize(selector, buffer, flusher);
			// write value, no value for UNDEFINED_INDEX
			if (selector != UNDEFINED_INDEX) 
				value.serialize(buffer, flusher);

		}
	}
	/* (non-Javadoc)
	 * @see org.epics.pvdata.pv.Serializable#deserialize(java.nio.ByteBuffer, org.epics.pvdata.pv.DeserializableControl)
	 */
	public void deserialize(ByteBuffer buffer, DeserializableControl control) {
		if (variant)
		{
			Field field = fieldCreate.deserialize(buffer, control);
			if (field != null)
			{
				value = pvDataCreate.createPVField(field);
				value.deserialize(buffer, control);
			}
			else
				value = null;
		}
		else
		{
			selector = SerializeHelper.readSize(buffer, control);
			if (selector != UNDEFINED_INDEX)
			{
				Field field = union.getField(selector);
				value = pvDataCreate.createPVField(field);
				value.deserialize(buffer, control);
			}
			else
				value = null;
		}
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof PVUnion) {
			PVUnion b = (PVUnion)obj;
			if (selector == b.getSelectedIndex())
			{
				if (selector == UNDEFINED_INDEX || value.equals(b.get()))
					return true;
				else
					return false;
			}
			else
				return false;
		}
		else
			return false;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int PRIME = 31;
		return selector + PRIME * value.hashCode();
	}
}
