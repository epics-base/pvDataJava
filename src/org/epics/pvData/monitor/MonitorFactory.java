/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS pvData is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvData.monitor;

import java.util.ArrayList;

import org.epics.pvData.factory.ConvertFactory;
import org.epics.pvData.factory.PVDataFactory;
import org.epics.pvData.factory.StatusFactory;
import org.epics.pvData.misc.BitSet;
import org.epics.pvData.misc.ThreadPriority;
import org.epics.pvData.misc.Timer;
import org.epics.pvData.misc.TimerFactory;
import org.epics.pvData.pv.Convert;
import org.epics.pvData.pv.Field;
import org.epics.pvData.pv.MessageType;
import org.epics.pvData.pv.PVBoolean;
import org.epics.pvData.pv.PVDataCreate;
import org.epics.pvData.pv.PVField;
import org.epics.pvData.pv.PVRecord;
import org.epics.pvData.pv.PVString;
import org.epics.pvData.pv.PVStructure;
import org.epics.pvData.pv.Scalar;
import org.epics.pvData.pv.ScalarType;
import org.epics.pvData.pv.Status;
import org.epics.pvData.pv.StatusCreate;
import org.epics.pvData.pv.Type;
import org.epics.pvData.pvCopy.BitSetUtil;
import org.epics.pvData.pvCopy.BitSetUtilFactory;
import org.epics.pvData.pvCopy.PVCopy;
import org.epics.pvData.pvCopy.PVCopyFactory;
import org.epics.pvData.pvCopy.PVCopyMonitor;
import org.epics.pvData.pvCopy.PVCopyMonitorRequester;

/**
 * @author mrk
 *
 */
public class MonitorFactory {
	
	/**
	 * Create a monitor.
	 * @param pvRecord The record to monitor.
	 * @param monitorRequester The requester.
	 * @param pvRequest Then request structure defining the monitor options.
	 * @return The Monitor interface.
	 */
	public static Monitor create(PVRecord pvRecord,MonitorRequester monitorRequester,PVStructure pvRequest)
	{
		MonitorImpl monitor = new MonitorImpl(pvRecord,monitorRequester);
		if(!monitor.init(pvRequest)) return null;
		return monitor;
	}
	
	public static void registerMonitorAlgorithmCreater(MonitorAlgorithmCreate monitorAlgorithmCreate) {
		synchronized(monitorAlgorithmCreateList) {
            monitorAlgorithmCreateList.add(monitorAlgorithmCreate);
        }
	}
	private static final StatusCreate statusCreate = StatusFactory.getStatusCreate();
    private static final Status okStatus = statusCreate.getStatusOK();
	private static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
	private static final ArrayList<MonitorAlgorithmCreate> monitorAlgorithmCreateList = new ArrayList<MonitorAlgorithmCreate>();
	private static final BitSetUtil bitSetUtil = BitSetUtilFactory.getCompressBitSet();
	private static final Convert convert = ConvertFactory.getConvert();
	private static final Timer timer = TimerFactory.create("periodicMonitor", ThreadPriority.high);
	private static PVStructure pvTimeStampRequest;
	private static MonitorAlgorithmCreate algorithmOnChangeCreate;
	private static MonitorAlgorithmCreate algorithmDeadband;
	
	static {
		AlgorithmOnChangeFactory.register();
		AlgorithmDeadbandFactory.register();
		pvTimeStampRequest = pvDataCreate.createPVStructure(null, "", new Field[0]);
		PVString pvAlgorithm = (PVString)pvDataCreate.createPVScalar(pvTimeStampRequest, "algorithm", ScalarType.pvString);
		pvAlgorithm.put("onChange");
		pvTimeStampRequest.appendPVField(pvAlgorithm);
		PVBoolean pvCauseMonitor = (PVBoolean)pvDataCreate.createPVScalar(pvTimeStampRequest, "causeMonitor", ScalarType.pvBoolean);
		pvCauseMonitor.put(false);
		pvTimeStampRequest.appendPVField(pvCauseMonitor);
		for(int i=0; i<monitorAlgorithmCreateList.size(); i++) {
			MonitorAlgorithmCreate algorithmCreate = monitorAlgorithmCreateList.get(i);
			if(algorithmCreate.getAlgorithmName().equals("onChange")) {
				algorithmOnChangeCreate = algorithmCreate;
			}
			if(algorithmCreate.getAlgorithmName().equals("deadband")) {
				algorithmDeadband = algorithmCreate;
			}
		}
	}
	
	private static class MonitorFieldNode {
		MonitorAlgorithm monitorAlgorithm;
		int bitOffset; // in pvCopy
		
		MonitorFieldNode(MonitorAlgorithm monitorAlgorithm,int bitOffset) {
			this.monitorAlgorithm = monitorAlgorithm;
			this.bitOffset = bitOffset;
		}
	}
	
	private interface QueueImpl {
        public MonitorElement init(MonitorImpl monitorImpl,int queueSize);
        public Status start();
        public void stop();
    	public boolean dataChanged();
    	public MonitorElement poll();
    	public void release(MonitorElement monitorElement);
    }
	
	
	
	private static class MonitorImpl implements Monitor,PVCopyMonitorRequester {
		private final PVRecord pvRecord;
		private final MonitorRequester monitorRequester;
		
		private boolean isPeriodic = false;
		private double periodicRate = 1.0;
		private PVCopy pvCopy = null;
		private QueueImpl queueImpl = null;
		private PVCopyMonitor pvCopyMonitor;
		private final ArrayList<MonitorFieldNode> monitorFieldList = new ArrayList<MonitorFieldNode>();
		
        private volatile boolean firstMonitor = false;
        private volatile boolean gotMonitor = false;
        private BitSet changedBitSet = null;
        private BitSet overrunBitSet = null;
        
        private BitSet notMonitoredBitSet = null;
        
		private MonitorImpl(PVRecord pvRecord,MonitorRequester monitorRequester) {
			this.pvRecord = pvRecord;
			this.monitorRequester = monitorRequester;
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.Monitor#poll()
		 */
		@Override
		public MonitorElement poll() {
			return queueImpl.poll();
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.Monitor#release(org.epics.pvData.monitor.MonitorElement)
		 */
		@Override
		public void release(MonitorElement currentElement) {
			queueImpl.release(currentElement);
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.Monitor#start()
		 */
		@Override
		public Status start() {
			firstMonitor = true;
			gotMonitor = false;
			Status status = queueImpl.start();
			if(!status.isSuccess()) return status;
			changedBitSet.clear();
    		overrunBitSet.clear();
			pvCopyMonitor.startMonitoring(changedBitSet, overrunBitSet);
			return status;
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.Monitor#stop()
		 */
		@Override
		public Status stop() {
			pvCopyMonitor.stopMonitoring();
			queueImpl.stop();
	        return okStatus;
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.misc.Destroyable#destroy()
		 */
		@Override
		public void destroy() {
			stop();
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.pvCopy.PVCopyMonitorRequester#dataChanged()
		 */
		@Override
		public void dataChanged() {
			if(firstMonitor) {
				queueImpl.dataChanged();
				firstMonitor = false;
				monitorRequester.monitorEvent(this);
				gotMonitor = false;
				return;
			}
			if(!gotMonitor) {
				for(int i=0; i<monitorFieldList.size(); i++) {
					MonitorFieldNode node = monitorFieldList.get(i);
					boolean result = node.monitorAlgorithm.causeMonitor();
					if(result) gotMonitor = true;
				}
			}
			if(!gotMonitor) {
				int nextBit = notMonitoredBitSet.nextSetBit(0);
				while(nextBit>=0) {
					if(changedBitSet.get(nextBit)) {
						gotMonitor = true;
					}
					nextBit = notMonitoredBitSet.nextSetBit(nextBit+1);
				}
			}
			if(!gotMonitor) return;
			if(queueImpl.dataChanged()) {
				monitorRequester.monitorEvent(this);
				for(int i=0; i<monitorFieldList.size(); i++) {
					MonitorFieldNode node = monitorFieldList.get(i);
					node.monitorAlgorithm.monitorIssued();
				}
				gotMonitor = false;
			}
		}	
		/* (non-Javadoc)
		 * @see org.epics.pvData.pvCopy.PVCopyMonitorRequester#unlisten()
		 */
		@Override
		public void unlisten() {
			monitorRequester.unlisten(this);
		}


		private boolean init(PVStructure pvRequest) {
			//Marty onPut changeTimeStamp
			int queueSize = 2;
			PVField pvField = pvRequest.getSubField("record.queueSize");
			if(pvField!=null && (pvField instanceof PVString)) {
				PVString pvString = pvRequest.getStringField("record.queueSize");
				String value = pvString.get();
				try {
					queueSize = Integer.parseInt(value);
				} catch (NumberFormatException e) {
					monitorRequester.message("queueSize " + e.getMessage(), MessageType.error);
					return false;
				}
			}
			if(queueSize<1) {
				monitorRequester.message("queueSize must be >-1", MessageType.error);
				return false;
			}
			pvField = pvRequest.getSubField("record.periodicRate");
			if(pvField!=null && (pvField instanceof PVString)) {
				PVString pvString = pvRequest.getStringField("record.periodicRate");
				String value = pvString.get();
				try {
					periodicRate = Double.parseDouble(value);
				} catch (NumberFormatException e) {
					monitorRequester.message("periodicRate " + e.getMessage(), MessageType.error);
					return false;
				}
				isPeriodic = true;
			}
			pvField = pvRequest.getSubField("field");
			if(pvField==null) {
				pvCopy = PVCopyFactory.create(pvRecord, pvRequest, "");
				if(pvCopy==null) {
					monitorRequester.message("illegal pvRequest", MessageType.error);
					return false;
				}
			} else {
				if(!(pvField instanceof PVStructure)) {
					monitorRequester.message("illegal pvRequest.field", MessageType.error);
					return false;
				}
				pvCopy = PVCopyFactory.create(pvRecord, pvRequest, "field");
				if(pvCopy==null) {
					monitorRequester.message("illegal pvRequest", MessageType.error);
					return false;
				}
				pvRequest = pvRequest.getStructureField("field");
			}
			pvCopyMonitor = pvCopy.createPVCopyMonitor(this);
			MonitorElement monitorElement = null;
			if(isPeriodic) {
				queueImpl = new PeriodicNoQueue();
			} else if(queueSize>1) {
				queueImpl = new Queue();
			} else {
				queueImpl = new NoQueue();
			}
			monitorElement = queueImpl.init(this,queueSize);
			notMonitoredBitSet = (BitSet)monitorElement.getChangedBitSet().clone();
			notMonitoredBitSet.clear();
			boolean result = initField(pvRequest,"",monitorElement);
			if(result) {
				initTimeStamp(monitorElement);
				initNumericFields(monitorElement.getPVStructure());
				notMonitoredBitSet.flip(0, notMonitoredBitSet.size());
			}
			return result;
		}
		
		private boolean initField(PVStructure pvRequest,String copyFullFieldName,MonitorElement monitorElement) {
			PVField[] pvFields = pvRequest.getPVFields();
			for(int i=0; i<pvFields.length; i++) {
				PVField pvField = pvFields[i];
				if(pvField.getField().getType()!=Type.structure) continue;
				PVStructure pvStruct = (PVStructure)pvField;
				PVField pv = pvStruct.getSubField("leaf");
				if(pv!=null) {
					if(pvStruct.getSubField("algorithm")==null) continue;
					PVString pvFullName = pvStruct.getStringField("leaf");
					PVField pvRecordField = pvRecord.getPVStructure().getSubField(pvFullName.get());
					if(pvRecordField==null) return false;
					String name = copyFullFieldName;
					if(name.length()!=0) name += ".";
					name += pvRecordField.getField().getFieldName();
					PVField pvCopyField = monitorElement.getPVStructure().getSubField(name);
					boolean result = initMonitorField(
							pvStruct,pvCopyField,pvRecordField,monitorElement);
					if(!result) return false;
					continue;
				}
				String name = copyFullFieldName;
				if(name.length()!=0) name += ".";
				name += pvStruct.getField().getFieldName();
				// Note that next call is recursive
				boolean result = initField(pvStruct,name,monitorElement);
				if(!result) return false;
			}
			return true;
		}
		
		private boolean initMonitorField(
				PVStructure pvMonitor,PVField pvCopyField,
				PVField pvRecordField,MonitorElement monitorElement)
		{
			PVString pvAlgorithm = pvMonitor.getStringField("algorithm");
			if(pvAlgorithm==null) return false;
			String algorithm = pvAlgorithm.get();
			if(algorithm.equals("onPut")) return true;
			MonitorAlgorithmCreate monitorAlgorithmCreate = null;
			for(int i=0; i<monitorAlgorithmCreateList.size(); i++) {
				monitorAlgorithmCreate = monitorAlgorithmCreateList.get(i);
				if(monitorAlgorithmCreate.getAlgorithmName().equals(algorithm)) break;
			}
			if(monitorAlgorithmCreate==null) {
				monitorRequester.message("algorithm not registered", MessageType.error);
				return false;
			}
			MonitorAlgorithm monitorAlgorithm = monitorAlgorithmCreate.create(pvRecord,monitorRequester,pvRecordField,pvMonitor);
			int bitOffset = pvCopyField.getFieldOffset();
			int numBits = pvCopyField.getNumberFields();
			notMonitoredBitSet.set(bitOffset, bitOffset+numBits-1);
			MonitorFieldNode node = new MonitorFieldNode(monitorAlgorithm,bitOffset);
			monitorFieldList.add(node);
			return true;
		}
		
		private void initTimeStamp(MonitorElement monitorElement) {
			PVField pvField = pvRecord.getPVStructure().getSubField("timeStamp");
			if(pvField==null) return;
			int bitOffset = pvCopy.getCopyOffset(pvField);
			if(bitOffset<0) return;
			for(int i=0; i<monitorFieldList.size(); i++) {
				MonitorFieldNode monitorFieldNode = monitorFieldList.get(i);
				if(monitorFieldNode.bitOffset==bitOffset) return;

			}
			MonitorAlgorithm monitorAlgorithm = algorithmOnChangeCreate.create(pvRecord,monitorRequester,pvField,pvTimeStampRequest);
			MonitorFieldNode node = new MonitorFieldNode(monitorAlgorithm,bitOffset);
			monitorFieldList.add(node);
			PVField pvCopyField = monitorElement.getPVStructure().getSubField("timeStamp");
			int numBits = pvCopyField.getNumberFields();
			notMonitoredBitSet.set(bitOffset, bitOffset+numBits);
		}
		
		private void initNumericFields(PVStructure pvStructure) {
			PVField[] pvFields = pvStructure.getPVFields();
			outer:
			for(int i=0; i<pvFields.length; i++) {
				PVField pvField = pvFields[i];
				Field field = pvField.getField();
				Type type = field.getType();
				if(type==Type.structure) {
					initNumericFields((PVStructure)pvField);
				} else if(type==Type.scalar) {
					Scalar scalar = (Scalar)field;
					if(scalar.getScalarType().isNumeric()) {
						int bitOffset = pvField.getFieldOffset();
						for(int j=0; j<monitorFieldList.size(); j++) {
							MonitorFieldNode monitorFieldNode = monitorFieldList.get(j);
							if(monitorFieldNode.bitOffset==bitOffset) continue outer; // already monitored
						}
						PVField pvRecordField = pvCopy.getRecordPVField(bitOffset);
						MonitorAlgorithm monitorAlgorithm = algorithmDeadband.create(pvRecord,monitorRequester,pvRecordField,null);
						if(monitorAlgorithm!=null) {
							int numBits = pvField.getNumberFields();
							notMonitoredBitSet.set(bitOffset, bitOffset+numBits);
						    MonitorFieldNode node = new MonitorFieldNode(monitorAlgorithm,bitOffset);
						    monitorFieldList.add(node);
						}
					}
				}
			}
		}
		
		private class NoQueue implements QueueImpl {
			private PVStructure pvCopyStructure = null;
			private MonitorElement monitorElement = null;
			private volatile boolean gotMonitor = false;
			private volatile boolean wasReleased = true;
	        private BitSet noQueueChangeBitSet = null;
	        private BitSet noQueueOverrunBitSet = null;
			
			@Override
			public MonitorElement init(MonitorImpl monitorImpl,int queueSize) {
				monitorElement = MonitorQueueFactory.createMonitorElement(pvCopy.createPVStructure());
				pvCopyStructure = monitorElement.getPVStructure();
	        	changedBitSet = monitorElement.getChangedBitSet();
	        	overrunBitSet = monitorElement.getOverrunBitSet();
	        	noQueueChangeBitSet = (BitSet)monitorElement.getChangedBitSet().clone();
	        	noQueueOverrunBitSet = (BitSet)monitorElement.getChangedBitSet().clone();
	        	return monitorElement;
			}
			@Override
			public Status start() {
				synchronized(monitorElement) {
		    		gotMonitor = true;
		    		wasReleased = true;
		    		noQueueChangeBitSet.clear();
		    		noQueueOverrunBitSet.clear();
	        	}
	            return okStatus;
			}
			@Override
			public void stop() {}
			@Override
			public boolean dataChanged() {
				pvCopy.updateCopyFromBitSet(pvCopyStructure, changedBitSet, false);
				bitSetUtil.compress(changedBitSet, pvCopyStructure);
	            bitSetUtil.compress(overrunBitSet, pvCopyStructure);
				synchronized(monitorElement) {
					gotMonitor = true;
					return wasReleased ? true : false;
				}
			}
			@Override
			public MonitorElement poll() {
				synchronized(monitorElement) {
					if(!gotMonitor) return null;
					noQueueChangeBitSet.clear();
					noQueueOverrunBitSet.clear();
					return monitorElement;
				}
			}
			@Override
			public void release(MonitorElement monitorElement) {
				synchronized(monitorElement) {
	        		changedBitSet.xor(noQueueChangeBitSet);
	                overrunBitSet.xor(noQueueOverrunBitSet);
	                gotMonitor = false;
	                wasReleased = true;
	            }
			}
		}
		
		private class Queue implements QueueImpl {
			private MonitorImpl monitorImpl;
			private MonitorQueue monitorQueue = null;
			private MonitorElement monitorElement = null;
			private volatile boolean queueIsFull = false;
			
			@Override
			public MonitorElement init(MonitorImpl monitorImpl,int queueSize) {
				this.monitorImpl = monitorImpl;
				MonitorElement[] elements = new MonitorElement[queueSize];
				for(int i=0; i<elements.length;i++) elements[i] = MonitorQueueFactory.createMonitorElement(pvCopy.createPVStructure());
				monitorQueue = MonitorQueueFactory.create(elements);
				monitorElement = monitorQueue.getFree();
				return monitorElement;
			}
			@Override
			public Status start() {
				firstMonitor = true;
	    		monitorQueue.clear();
	    		monitorElement = monitorQueue.getFree();
	    		changedBitSet = monitorElement.getChangedBitSet();
	    		overrunBitSet = monitorElement.getOverrunBitSet();
	            return okStatus;
			}
			@Override
			public void stop() {}
			@Override
			public boolean dataChanged() {
				PVStructure pvStructure = monitorElement.getPVStructure();
				pvCopy.updateCopyFromBitSet(pvStructure, changedBitSet, false);
				synchronized(monitorQueue) {
					MonitorElement newElement = monitorQueue.getFree();
					if(newElement==null) {
						queueIsFull = true;
						return true;
					}
					bitSetUtil.compress(changedBitSet, pvStructure);
					bitSetUtil.compress(overrunBitSet, pvStructure);
					convert.copy(pvStructure, newElement.getPVStructure());
					changedBitSet = newElement.getChangedBitSet();
					overrunBitSet = newElement.getOverrunBitSet();
					changedBitSet.clear();
					overrunBitSet.clear();
					pvCopyMonitor.switchBitSets(changedBitSet, overrunBitSet, false);
					monitorQueue.setUsed(monitorElement);
					monitorElement = newElement;
				}
				return true;
			}
			@Override
			public MonitorElement poll() {
				synchronized(monitorQueue) {
					return monitorQueue.getUsed();
				}
			}
			@Override
			public void release(MonitorElement currentElement) {
				synchronized(monitorQueue) {
					monitorQueue.releaseUsed(currentElement);
					currentElement.getOverrunBitSet().clear();
					currentElement.getChangedBitSet().clear();
					if(!queueIsFull) return;
					queueIsFull = false;
					PVStructure pvStructure = monitorElement.getPVStructure();
					MonitorElement newElement = monitorQueue.getFree();
					bitSetUtil.compress(changedBitSet, pvStructure);
					bitSetUtil.compress(overrunBitSet, pvStructure);
					convert.copy(pvStructure, newElement.getPVStructure());
					changedBitSet = newElement.getChangedBitSet();
					overrunBitSet = newElement.getOverrunBitSet();
					changedBitSet.clear();
					overrunBitSet.clear();
					pvCopyMonitor.switchBitSets(changedBitSet, overrunBitSet, true);
					monitorQueue.setUsed(monitorElement);
					monitorElement = newElement;
				}
			}
		}
		
		private class PeriodicNoQueue implements QueueImpl,Timer.TimerCallback {
			private MonitorImpl monitorImpl = null;
			private PVStructure pvCopyStructure = null;
			private MonitorElement monitorElement = null;
			private volatile boolean gotMonitor = false;
			private volatile boolean wasReleased = true;
			private volatile boolean timerExpired = false;
	        private BitSet noQueueChangeBitSet = null;
	        private BitSet noQueueOverrunBitSet = null;
	        private Timer.TimerNode timerNode = TimerFactory.createNode(this);
			
			@Override
			public MonitorElement init(MonitorImpl monitorImpl,int queueSize) {
				this.monitorImpl = monitorImpl;
				monitorElement = MonitorQueueFactory.createMonitorElement(pvCopy.createPVStructure());
				pvCopyStructure = monitorElement.getPVStructure();
	        	changedBitSet = monitorElement.getChangedBitSet();
	        	overrunBitSet = monitorElement.getOverrunBitSet();
	        	noQueueChangeBitSet = (BitSet)monitorElement.getChangedBitSet().clone();
	        	noQueueOverrunBitSet = (BitSet)monitorElement.getChangedBitSet().clone();
	        	return monitorElement;
			}
			@Override
			public Status start() {
				synchronized(monitorElement) {
		    		gotMonitor = true;
		    		wasReleased = true;
		    		noQueueChangeBitSet.clear();
		    		noQueueOverrunBitSet.clear();
	        	}
				timer.schedulePeriodic(timerNode, periodicRate, periodicRate);
	            return okStatus;
			}
			@Override
			public void stop() {
				timer.stop();
			}
			@Override
			public boolean dataChanged() {
				if(!timerExpired) return false;
				timerExpired = false;
				if(changedBitSet.isEmpty()) return false;
				pvCopy.updateCopyFromBitSet(pvCopyStructure, changedBitSet, false);
				bitSetUtil.compress(changedBitSet, pvCopyStructure);
	            bitSetUtil.compress(overrunBitSet, pvCopyStructure);
				synchronized(monitorElement) {
					gotMonitor = true;
					return wasReleased ? true : false;
				}
			}
			@Override
			public MonitorElement poll() {
				synchronized(monitorElement) {
					if(!gotMonitor) return null;
					noQueueChangeBitSet.clear();
					noQueueOverrunBitSet.clear();
					return monitorElement;
				}
			}
			@Override
			public void release(MonitorElement monitorElement) {
				synchronized(monitorElement) {
	        		changedBitSet.xor(noQueueChangeBitSet);
	                overrunBitSet.xor(noQueueOverrunBitSet);
	                gotMonitor = false;
	                wasReleased = true;
	            }
			}
			/* (non-Javadoc)
			 * @see org.epics.pvData.misc.Timer.TimerCallback#callback()
			 */
			@Override
			public void callback() {
				timerExpired = true;
				monitorImpl.dataChanged();
			}
			/* (non-Javadoc)
			 * @see org.epics.pvData.misc.Timer.TimerCallback#timerStopped()
			 */
			@Override
			public void timerStopped() {
				monitorRequester.message("periodicTimer stopped", MessageType.error);
			}
		}
	}
}
