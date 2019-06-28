package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;

/**
 * It is a basic class for all the measurements
 * @author envf
 *
 */
public abstract class Measurement {
	protected final Id<Measurement> id;
	protected Map<String,Object> attributes=new HashMap<>();
	protected final Map<String,Tuple<Double,Double>> timeBean;
	
	protected Measurement(String id, Map<String,Tuple<Double,Double>> timeBean) {
		this.id=Id.create(id, Measurement.class);
		this.timeBean=timeBean;
	}
	
	public Id<Measurement> getId() {
		return id;
	}

	public Map<String, Tuple<Double, Double>> getTimeBean() {
		return timeBean;
	}
	
	public abstract Measurement clone();
	
	/**
	 * This function is for some Measurement that are only valid in certain timeBeans
	 * @return A set of timeBeans that has a measurement, e.g. 9 and 18.
	 */
	public abstract Set<String> getValidTimeBeans();
	
}
