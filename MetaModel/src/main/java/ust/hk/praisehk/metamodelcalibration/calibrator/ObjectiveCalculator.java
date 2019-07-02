package ust.hk.praisehk.metamodelcalibration.calibrator;



import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.matamodels.MetaModel;
import ust.hk.praisehk.metamodelcalibration.measurements.LinkMeasurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurement;
import ust.hk.praisehk.metamodelcalibration.measurements.Measurements;

/**
 * This class will basically calculate the objective 
 * same for both sim and anaMeasurements This can be AADT or specific measurement based 
 * Direct change should e implemented inside the method
 * @author h
 *
 */
public class ObjectiveCalculator {
	
	public static final String TypeAADT="AADT";
	public static final String TypeMeasurementAndTimeSpecific="MeasurementAndTimeSpecific";
	public static final String TypeProfit = "BusProfit";
	public static final Logger logger=Logger.getLogger(ObjectiveCalculator.class);
	public static final String Type=TypeMeasurementAndTimeSpecific;
	
	/**
	 * It is just the flow of few stations with AADT.
	 * @param simOrAnaMeasurements
	 * @return
	 */
	public static double calcFlowObjective(Measurements simOrAnaMeasurements) {
		double objective=0;
		double stationCountAnaOrSim=0;
		for(Measurement m:simOrAnaMeasurements.getMeasurements().values()) {
			if(m instanceof LinkMeasurement) {
				LinkMeasurement lm = (LinkMeasurement) m;
				for(String timeBeanId:lm.getVolumes().keySet()) {
					stationCountAnaOrSim+= ((LinkMeasurement) simOrAnaMeasurements.getMeasurements().get(lm.getId())).
							getVolumes().get(timeBeanId);
				}
				objective+=stationCountAnaOrSim;
			}
		}
		return objective;
	}
	
	/**
	 * Objective function 2: The fares
	 * @param simOrAnaMeasurements
	 * @return
	 */
	public static double calcFareObjective(Measurements simOrAnaMeasurements) {
		return simOrAnaMeasurements.getBusProfit();
	}
	
	public static double aadtDifferenceObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements) {
		double objective = 0;
		double stationCountReal=0;
		double stationCountAnaOrSim=0;
		for(Measurement m:realMeasurements.getMeasurements().values()) {
			if(m instanceof LinkMeasurement) {
				LinkMeasurement lm = (LinkMeasurement) m;
				for(String timeBeanId:lm.getVolumes().keySet()) {
					LinkMeasurement simLm = (LinkMeasurement) simOrAnaMeasurements.getMeasurements().get(m.getId());
					if(simLm==null) {
						logger.error("The Measurements entered are not comparable (measurement do not match)!!! This should not happen. Please check");
						
					}else if(simLm.getVolumes().get(timeBeanId)==null) {
						logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");
						
					}
					
					stationCountReal+=lm.getVolumes().get(timeBeanId);
					stationCountAnaOrSim+=simLm.getVolumes().get(timeBeanId);
				}
				objective+=Math.pow((stationCountReal-stationCountAnaOrSim),2);
			}
		}
		return objective;
	}
	
	/**
	 * TODO: Add some more types of the objective
	 * @param realMeasurements The real measurements from the file (?)
	 * @param simOrAnaMeasurement The simulation or analytical model measurements
	 * @param Type: AADT or linkAnadTimeSpecific(default)
	 * @return
	 */
	public static double calcObjective(Measurements realMeasurements,Measurements simOrAnaMeasurements,String Type) {
		double objective=0;
		if(Type.equals(TypeAADT)) {
			return aadtDifferenceObjective(realMeasurements, simOrAnaMeasurements);
		}else if(Type.equals(TypeProfit)){
			return calcFareObjective(simOrAnaMeasurements);
		}else {
			for(Measurement m:realMeasurements.getMeasurements().values()) {
				if(m instanceof LinkMeasurement) {
					LinkMeasurement lm = (LinkMeasurement) m;
					for(String timeBeanId:lm.getVolumes().keySet()) {
						LinkMeasurement simLm = (LinkMeasurement) simOrAnaMeasurements.getMeasurements().get(m.getId());
						if(simLm==null) {
							logger.error("The Measurements entered are not comparable (measuremtn do not match)!!! This should not happen. Please check");
							
						}else if(simLm.getVolumes().get(timeBeanId)==null) {
							logger.error("The Measurements entered are not comparable (volume timeBeans do not match)!!! This should not happen. Please check");
							
						}
						
						objective+=Math.pow((lm.getVolumes().get(timeBeanId)-simLm.getVolumes().get(timeBeanId)),2);
					}
				}
			}
			
		}
		return objective;
	}
	
	/**
	 * It is the calculate objective function for the interLogger class
	 * @param realMeasurements
	 * @param anaMeasurements
	 * @param metaModels
	 * @param param
	 * @param Type
	 * @return
	 */
	public static double calcObjective(Measurements realMeasurements, Measurements anaMeasurements, 
			Map<Id<Measurement>,Map<String,MetaModel>>metaModels, LinkedHashMap<String,Double>param, String Type) {
		Measurements metaMeasurements=realMeasurements.clone();
		for(Measurement m:realMeasurements.getMeasurements().values()) {
			if(m instanceof LinkMeasurement) {
				LinkMeasurement lm = (LinkMeasurement) m;
				for(String timeBeanid:lm.getVolumes().keySet()) {
					((LinkMeasurement) metaMeasurements.getMeasurements().get(m.getId())).
					addVolume(timeBeanid, metaModels.get(m.getId()).get(timeBeanid).calcMetaModel( anaMeasurements.getVolumes(m.getId()).get(timeBeanid), param));
				}
			}
		}
		return calcObjective(realMeasurements,metaMeasurements,Type);
	}
}
