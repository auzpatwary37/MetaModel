package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.lanes.Lane;
import org.matsim.lanes.LanesToLinkAssignment;

public class CNLLinkToLink extends CNLLink {
	
	//Key: toLinkId, value: volume
	private Map<Id<Link>, Double> toLinkCarVolume = new HashMap<>();
	private Map<Id<Link>, Double> toLinkTransitVolume = new HashMap<>(); 
	private Map<Id<Link>, Double> toLinkCarConstantVolume = new HashMap<>(); //A constant term added to the volume
	private Map<Id<Link>, Double> toLinkGcRatio = new HashMap<>();
	
	private final Map<Id<Link>, Double> toLinkCapacity; //We are lenient here.

	public CNLLinkToLink(Link link, LanesToLinkAssignment l2l) {
		super(link);
		Map<Id<Link>, Double> toLinkCapacityMap = new LinkedHashMap<>();
		if(l2l!=null) {
			for(Link toLink: link.getToNode().getOutLinks().values()) {
				for(Lane lane: l2l.getLanes().values()) {
					if(lane.getToLinkIds()!=null && lane.getToLinkIds().contains(toLink.getId())) {
						if(!toLinkCapacityMap.containsKey(toLink.getId())) {
							toLinkCapacityMap.put(toLink.getId(), lane.getCapacityVehiclesPerHour()); //Add the capacity if there is a link.
						}else {
							toLinkCapacityMap.put(toLink.getId(), toLinkCapacityMap.get(toLink.getId()) + lane.getCapacityVehiclesPerHour());
						}
					}
				}
			}
		}else {
			for(Link toLink: link.getToNode().getOutLinks().values()) {
				toLinkCapacityMap.put(toLink.getId(), link.getCapacity());
			}
		}
		toLinkCapacity = Collections.unmodifiableMap(toLinkCapacityMap); //Make it unmodifiable to prevent accidents
	}
	
	public Set<Id<Link>> getToLinks(){
		return toLinkCapacity.keySet();
	}
	
	public void addLinkToLinkTransitVolume(Id<Link> toLinkId, double pcuVolume) {
		double currValue = getLinkTransitVolume(toLinkId);
		this.toLinkTransitVolume.put(toLinkId, currValue + pcuVolume);
	}
	
	public double getMaximumToLinkFlowCapacity(Id<Link> toLinkId, Tuple<Double,Double> timeBean, Map<String, Double> params) {
		return toLinkCapacity.get(toLinkId)*(timeBean.getSecond()-timeBean.getFirst())/3600*params.get("All "+CNLSUEModel.CapacityMultiplierName) 
				* this.toLinkGcRatio.get(toLinkId) * effectiveCapacity;
	}
	
	public double getLinkCarVolume(Id<Link> toLinkId) {
		if(toLinkCarVolume.containsKey(toLinkId)) {
			return toLinkCarVolume.get(toLinkId).doubleValue();
		}else {
			return 0;
		}
	}
	
	public double getLinkTransitVolume(Id<Link> toLinkId) {
		if(toLinkTransitVolume.containsKey(toLinkId)) {
			return toLinkTransitVolume.get(toLinkId).doubleValue();
		}else {
			return 0;
		}
	}
	
	public double getLinkCarConstantVolume(Id<Link> toLinkId) {
		if(toLinkCarConstantVolume.containsKey(toLinkId)) {
			return toLinkCarConstantVolume.get(toLinkId).doubleValue();
		}else {
			return 0;
		}
	}
	
	public void addLinkToLinkCarVolume(Id<Link> toLinkId, double volume) {
		if(toLinkCarVolume.containsKey(toLinkId)) {
			toLinkCarVolume.put(toLinkId, toLinkCarVolume.get(toLinkId) + volume);
		}else {
			toLinkCarVolume.put(toLinkId, volume);
		}
	}
	
	public void setToLinkGCRatio(Id<Link> toLinkId, double ratio) {
		toLinkGcRatio.put(toLinkId, ratio);
	}

	@Override
	public double getLinkTravelTime(Tuple<Double, Double> timeBean, Map<String, Double> params,
			Map<String, Double> anaParams) {
		return super.getLinkTravelTime(timeBean, params, anaParams);
	}
	
	public double getLinkToLinkTravelTime(Id<Link> toLinkId, Tuple<Double, Double> timeBean, Map<String, Double> params,
			Map<String, Double> anaParmas) {
		double totalpcu = getLinkCarVolume(toLinkId) + getLinkTransitVolume(toLinkId) + getLinkCarConstantVolume(toLinkId);
		double capacity = getMaximumToLinkFlowCapacity(toLinkId, timeBean, params);
		double freeflowTime= super.getLength()/super.getFreespeed();
		
		double toLinkTravelTime = freeflowTime*(1+ anaParmas.get(CNLSUEModel.BPRalphaName)*
								Math.pow(totalpcu/capacity, anaParmas.get(CNLSUEModel.BPRbetaName))) / this.gcRatio;
		if(totalpcu / capacity > 3) {
			return toLinkTravelTime * Math.exp(4); //15 = 2 * (3 - 1)
		}else if(totalpcu > capacity) {
			return toLinkTravelTime * Math.exp(2 * (totalpcu/capacity - 1));
		}
		
		return Math.max(toLinkTravelTime, getLinkTravelTime(timeBean, params, anaParmas)); //Whatever larger limits the link travel time
	}

}
