package ust.hk.praisehk.metamodelcalibration.measurements;

import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;
import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLTransitRoute;

public class MeasurementDataContainer {
	private Map<String,Map<Id<Link>,Double>> linkVolumes = new HashMap<>();
	//public double profit;
	private Map<String, Double> busFareReceived = new HashMap<>();
	private Map<String, Double> metroFareReceived = new HashMap<>();
	
	public void clear() {
		linkVolumes.clear();
		busFareReceived.clear();
		metroFareReceived.clear();
	}
	
	public void resetTransitFares(String timeBin) {
		busFareReceived.put(timeBin, 0.);
		metroFareReceived.put(timeBin, 0.);
	}
	
	public void setLinkVolumes(Map<String, Map<Id<Link>, Double>> linkVolumes) {
		this.linkVolumes = linkVolumes;
	}

	public void setBusFareReceived(Map<String, Double> busFareReceived) {
		this.busFareReceived = busFareReceived;
	}

	public void setMetroFareReceived(Map<String, Double> metroFareReceived) {
		this.metroFareReceived = metroFareReceived;
	}
	
	public void addBusFareReceived(String timeBinId, double amount) {
		Double profit = busFareReceived.get(timeBinId);
		if(profit==null) {
			profit = 0.;
		}
		busFareReceived.put(timeBinId, profit + amount);
	}
	
	public void addMTRFareReceived(String timeBinId, double amount) {
		Double profit = metroFareReceived.get(timeBinId);
		if(profit==null) {
			profit = 0.;
		}
		metroFareReceived.put(timeBinId, profit + amount);
	}
	
	public Measurements getMeasurements(Map<String, Tuple<Double, Double>> timeBins) {
		Measurements m = Measurements.createMeasurements(timeBins);
		m.initializeFareMeasurements();
		m.setBusAndMetroProfit(busFareReceived, metroFareReceived);
		m.updateLinkVolumes(linkVolumes);
		return m;
	}
}
