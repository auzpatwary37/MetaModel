package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitDirectLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;



/**
 * 
 * @author Ashraf
 *
 */

public class CNLTransitDirectLink extends TransitDirectLink{
	//private Scenario scenario;
	Vehicles transitVehicles;
	
	public CNLTransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, Id<Link> endLinkId,
			TransitRoute tr, Id<TransitLine> lineId, Vehicles transitVehicles) {
		super(startStopId, endStopId, startLinkId, endLinkId, tr, lineId);
		this.transitVehicles = transitVehicles;
		this.TrLinkId=Id.create(startStopId.replaceAll("\\s+","")+"_"+endStopId.replaceAll("\\s+","")+"_"+
		lineId.toString().replaceAll("\\s+","")+"_"+ tr.getId().toString().replaceAll("\\s+",""),TransitLink.class);
	}
	
	@Deprecated
	public CNLTransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, Id<Link> endLinkId,
			TransitSchedule ts, Id<TransitLine> lineId, Id<TransitRoute> routeId, Vehicles transitVehicles) {
		this(startStopId, endStopId, startLinkId, endLinkId, ts.getTransitLines().get(lineId).getRoutes().get(routeId),
				lineId, transitVehicles);
	}
	
	public CNLTransitDirectLink(String RouteDescription, Id<Link> startLinkId, Id<Link> endLinkId,
			TransitSchedule ts, Vehicles transitVehicles){
		this(RouteDescription.split("===")[1].trim(), RouteDescription.split("===")[4].trim(), 
				startLinkId, endLinkId, ts, Id.create(RouteDescription.split("===")[2].trim(), TransitLine.class),
				Id.create(RouteDescription.split("===")[3].trim(), TransitRoute.class),transitVehicles);
	}
	

	//capacity of that link (60 by default)
	protected double capacity=60;
	protected double frequency=1;
	//time difference between two vehicles in second. default 300s
	protected double headway=300;
	
	private final Id<TransitLink> TrLinkId;
	
	/**
	 * calculates the link travel time 
	 */
	@Override
	public double getLinkTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams) {
		double travelTime=0;
		for(Id<Link> lId:this.linkList) {
			travelTime+=((AnalyticalModelLink)network.getLinks().get(lId)).getLinkTravelTime(timeBean,params,anaParams);
		}
		
		return travelTime;
	}
	@Override
	public void addPassanger(double d,AnalyticalModelNetwork network) {
		this.passangerCount+=d;
		for(Id<Link> clId:this.linkList) {
			((CNLLink)network.getLinks().get(clId)).addTransitPassengerVolume(this.lineId+"_"+this.routeId, d);
		}
	}
	public double getCapacity() {
		return capacity;
	}
	public double getHeadway() {
		return headway;
	}
	@Override
	public Id<TransitLink> getTrLinkId() {
		return TrLinkId;
	}
	
	public void calcCapacityAndHeadway(Map<String, Tuple<Double, Double>> timeBeans,String timeBeanId) {
		Map<Id<Departure>,Departure> departures= route.getDepartures();
		int noofVehicle=0;
		for(Departure d : departures.values()) {
			double time=d.getDepartureTime();
			if(time>=timeBeans.get(timeBeanId).getFirst() && time<timeBeans.get(timeBeanId).getSecond()) {
				noofVehicle++;
				Id<Vehicle> vehicleId = d.getVehicleId();
				this.capacity += (transitVehicles.getVehicles().get(vehicleId).getType().getCapacity().getSeats()+
						transitVehicles.getVehicles().get(vehicleId).getType().getCapacity().getStandingRoom());
			}
		}
		this.frequency=noofVehicle;
		if(noofVehicle==0) {
			capacity=0;
			headway=timeBeans.get(timeBeanId).getSecond()-timeBeans.get(timeBeanId).getFirst();
		}else {
			this.capacity=this.capacity/noofVehicle;
			this.headway=(timeBeans.get(timeBeanId).getSecond()-timeBeans.get(timeBeanId).getFirst())/noofVehicle;
		}
	}
	
	public CNLTransitDirectLink cloneLink(CNLTransitDirectLink tL) {
		return new CNLTransitDirectLink(tL.startStopId, tL.endStopId, tL.startingLinkId,
				tL.endingLinkId, tL.route, tL.lineId, tL.transitVehicles);
		
	}
	protected double getFrequency() {
		return frequency;
	}

}
