package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

public class MeasurementDataContainer {
	public Map<String,Map<Id<Link>,Double>> linkVolumes;
	public double profit;
}
