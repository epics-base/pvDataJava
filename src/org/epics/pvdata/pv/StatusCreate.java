/*
 * Copyright information and license terms for this software can be
 * found in the file LICENSE that is included with the distribution
 */
package org.epics.pvdata.pv;

import java.nio.ByteBuffer;

import org.epics.pvdata.pv.Status.StatusType;

/**
 * Interface for creating status.
 * @author mse
 */
public interface StatusCreate {
	
	/**
	 * Get OK status. Static instance should be returned.
	 * 
	 * @return OK <code>Status</code> instance
	 */
	Status getStatusOK();
	
	/**
	 * Create status.
	 * 
	 * @param type status type, non-<code>null</code>
	 * @param message message describing an error, non-<code>null</code>.
	 * 		  NOTE: Do NOT use <code>throwable.getMessage()</code> as message, since it will be supplied with the <code>cause</code>.
	 * @param cause exception that caused an error (optional)
	 * @return status instance.
	 */
	Status createStatus(StatusType type, String message, Throwable cause);
	
	/**
	 * Deserialize status.
	 * NOTE: use this method instead of <code>Status.deserialize()</code>, since this allows OK status optimization. 
	 *
	 * @param buffer deserialization buffer
	 * @param control deserialization control
	 * @return status instance
	 */
	Status deserializeStatus(ByteBuffer buffer, DeserializableControl control);
}
