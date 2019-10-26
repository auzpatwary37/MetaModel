package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

/**
 * It is a basic class for all the measurements
 * @author envf
 *
 */
public abstract class Measurement {
	protected final Id<Measurement> id;
	protected Map<String,Object> attributes = new HashMap<>();
	protected final Map<String,Tuple<Double,Double>> timeBean;
	protected Map<String,Double> values = new HashMap<>();
	private static final Logger logger=Logger.getLogger(Measurement.class);
	
	protected Measurement(String id, Map<String, Tuple<Double, Double>> timeBean2) {
		this.id = Id.create(id, Measurement.class);
		this.timeBean=timeBean2;
	}
	
	/**
	 * Call to this method with this.linkListAttributeName String will 
	 * return a ArrayList<Id<Link>> containing the link Ids of all the links in this measurement
	 * @param attributeName
	 * @return
	 */
	public Object getAttribute(String attributeName) {
		return this.attributes.get(attributeName);
	}

	public void setAttribute(String attributeName, Object attribute) {
		this.attributes.put(attributeName, attribute);
	}
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}
	
	public Id<Measurement> getId() {
		return id;
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}
	
	/**
	 * It PUTS a volume inside the LinkMeasurement
	 * @param timeBeanId
	 * @param volume
	 */
	public void setValue(String timeBeanId,double volume) {
		if(!this.timeBean.containsKey(timeBeanId)){
			logger.error("timeBean do not contain timeBeanId"+timeBeanId+", please check.");
			logger.warn("Ignoring volume for timeBeanId"+timeBeanId);
		}else {
			this.values.put(timeBeanId, volume);
		}
	}
	
	public Map<String,Double> getValues(){
		return this.values;
	}
	
	public abstract Measurement clone();
	
	/**
	 * This function is for some Measurement that are only valid in certain timeBeans
	 * @return A set of timeBeans that has a measurement, e.g. 9 and 18.
	 */
	public abstract Set<String> getValidTimeBeans();
	
}
