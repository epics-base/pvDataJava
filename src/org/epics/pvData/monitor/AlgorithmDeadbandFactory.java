/**
 * Copyright - See the COPYRIGHT that is included with this distribution.
 * EPICS JavaIOC is distributed subject to a Software License Agreement found
 * in file LICENSE that is included with this distribution.
 */
package org.epics.pvData.monitor;


import org.epics.pvData.factory.ConvertFactory;
import org.epics.pvData.factory.PVDataFactory;
import org.epics.pvData.pv.Convert;
import org.epics.pvData.pv.MessageType;
import org.epics.pvData.pv.PVBoolean;
import org.epics.pvData.pv.PVDataCreate;
import org.epics.pvData.pv.PVDouble;
import org.epics.pvData.pv.PVField;
import org.epics.pvData.pv.PVRecord;
import org.epics.pvData.pv.PVScalar;
import org.epics.pvData.pv.PVString;
import org.epics.pvData.pv.PVStructure;
import org.epics.pvData.pv.Scalar;
import org.epics.pvData.pv.Type;

/**
 * Factory that implements alarm deadband.
 * @author mrk
 *
 */
public class AlgorithmDeadbandFactory {
    private static final String name = "deadband";
    private static final Deadband deadband = new Deadband();
    protected static final PVDataCreate pvDataCreate = PVDataFactory.getPVDataCreate();
    protected static final Convert convert = ConvertFactory.getConvert();

    /**
     * Register the create factory.
     */
    public static void register() {
    	MonitorFactory.registerMonitorAlgorithmCreater(deadband);
    }
    
    private static class Deadband implements MonitorAlgorithmCreate {
        /* (non-Javadoc)
         * @see org.epics.ioc.channelAccess.MonitorCreate#getName()
         */
        @Override
        public String getAlgorithmName() {
            return name;
        }
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.MonitorAlgorithmCreate#create(org.epics.pvData.pv.PVRecord, org.epics.pvData.monitor.MonitorRequester, org.epics.pvData.pv.PVField, org.epics.pvData.pv.PVStructure)
		 */
		@Override
		public MonitorAlgorithm create(PVRecord pvRecord,
				MonitorRequester monitorRequester, PVField fromPVRecord,
				PVStructure pvOptions)
		{
			if(fromPVRecord.getField().getType()!=Type.scalar) return null;
			Scalar scalar = (Scalar)fromPVRecord.getField();
			if(!scalar.getScalarType().isNumeric()) return null;
			PVStructure pvRecordDeadband = null;
			PVStructure pvParent = fromPVRecord.getParent();
			PVField pvField = pvParent.getSubField("deadband");
			if(pvField!=null && (pvField instanceof PVStructure)) pvRecordDeadband = (PVStructure)pvField;
		    PVStructure pvCopyDeadband = null;
			if(pvOptions!=null) {
				pvField = pvOptions.getSubField("deadband");
				if(pvField!=null && (pvField instanceof PVStructure)) pvCopyDeadband = (PVStructure)pvField;
			}
			if(pvRecordDeadband==null && pvCopyDeadband==null) return null;
			boolean isDisplayDeadband= true;
			double deadbandCopy = 0.0;
			boolean isPercentCopy = false;
			if(pvCopyDeadband!=null) {
				pvField = pvCopyDeadband.getSubField("type");
				if(pvField!=null && (pvField instanceof PVString)) {
					PVString pvString = (PVString)pvField;
					if(pvString.get().equals("archive")) isDisplayDeadband = false;
				}
				pvField = pvCopyDeadband.getSubField("isPercent");
				if(pvField!=null && (pvField instanceof PVString)) {
					PVString pvString = (PVString)pvField;
					if(pvString.get().equals("true")) isPercentCopy = true;
				}
				pvField = pvCopyDeadband.getSubField("value");
				if(pvField!=null && (pvField instanceof PVString)) {
					PVString pvString = (PVString)pvField;
					try {
						deadbandCopy = Double.parseDouble(pvString.get());
					} catch (NumberFormatException e) {
						monitorRequester.message("deadband " + e.getMessage(), MessageType.error);
						return null;
					}
				}
			}
			double deadbandRecord = 0.0;
			boolean isPercentRecord = false;
			if(pvRecordDeadband!=null) {
				PVStructure pvStruct = null;
				if(isDisplayDeadband) {
					pvField = pvRecordDeadband.getSubField("display");
				} else {
					pvField = pvRecordDeadband.getSubField("archive");
				}
				if(pvField!=null && (pvField instanceof PVStructure)) pvStruct = (PVStructure)pvField;
				if(pvStruct!=null) {
					pvField = pvStruct.getSubField("isPercent");
					if(pvField!=null && (pvField instanceof PVBoolean)) {
						PVBoolean pvBoolean = (PVBoolean)pvField;
						isPercentRecord = pvBoolean.get();
					}
					pvField = pvStruct.getSubField("value");
					if(pvField!=null && (pvField instanceof PVDouble)) {
						PVDouble pvDouble = (PVDouble)pvField;
						deadbandRecord = pvDouble.get();
					}
				}
			}
			boolean isPercent = (pvCopyDeadband==null) ? isPercentRecord : isPercentCopy;
			double deadband = (pvCopyDeadband==null) ? deadbandRecord : deadbandCopy;
			if(deadband<=0.0) return null;
			return new MonitorAlgorithmImpl((PVScalar)fromPVRecord,deadband,isPercent);
		}
    }
    
    
    private static class MonitorAlgorithmImpl implements MonitorAlgorithm {
    	
    	
        private MonitorAlgorithmImpl(PVScalar pvFromRecord,double deadband,boolean isPercent)
        {
            this.pvFromRecord = pvFromRecord;
            this.deadband = deadband;
            this.isPercent = isPercent;
            prevValue = convert.toDouble(pvFromRecord);
        }
        
        private final PVScalar pvFromRecord;
        private final double deadband; 
        private final boolean isPercent;
        private double prevValue;
        private double currentValue;
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.MonitorAlgorithm#causeMonitor()
		 */
		@Override
		public boolean causeMonitor() {
			currentValue = convert.toDouble(pvFromRecord);
			double diff = Math.abs(currentValue-prevValue);
			if(isPercent) {
                if(currentValue!=0.0) {
                	return ((100.0*diff/Math.abs(currentValue))<deadband) ? false : true;
                } else {
                	return (prevValue==0.0) ? false : true;
                }
			} else {
				if(diff<=deadband) return false;
				return true;
			}
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.MonitorAlgorithm#getAlgorithmName()
		 */
		@Override
		public String getAlgorithmName() {
			return name;
		}
		/* (non-Javadoc)
		 * @see org.epics.pvData.monitor.MonitorAlgorithm#monitorIssued()
		 */
		@Override
		public void monitorIssued() {
			prevValue = currentValue;
		}
    }
}
