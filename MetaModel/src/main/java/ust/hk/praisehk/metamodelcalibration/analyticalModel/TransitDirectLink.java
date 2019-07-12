package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * 
 * @author Ashraf
 *
 * @param <anaNet>Type of network used
 */
public abstract class TransitDirectLink extends TransitLink{
	
	
	//direct link parameters
	
	protected ArrayList<Id<Link>> linkList=new ArrayList<>();
	protected Id<TransitLine> lineId;
	protected Id<TransitRoute> routeId;
	//protected TransitSchedule ts;
	protected TransitRoute route;
	protected double distance=0;
	
	/**
	 * Constructor
	 * 
	 * @param startStopId
	 * @param endStopId
	 * @param startLinkId
	 * @param endLinkId
	 */
	@Deprecated
	public TransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, 
			Id<Link> endLinkId, TransitSchedule ts, Id<TransitLine> lineId, Id<TransitRoute> routeId) {
		//super(startStopId, endStopId, startLinkId, endLinkId);
		this(startStopId, endStopId, startLinkId, endLinkId, 
				ts.getTransitLines().get(lineId).getRoutes().get(routeId), lineId);
	}
	
	/**
	 * A new constructor taking more suitable data.
	 * @param startStopId Start stop Id of the route
	 * @param endStopId End stop Id of the route
	 * @param startLinkId Start link Id
	 * @param endLinkId End link Id
	 * @param tr transitRoute
	 * @param lineId lineId for later usage
	 */
	public TransitDirectLink(String startStopId, String endStopId, Id<Link> startLinkId, 
			Id<Link> endLinkId, TransitRoute tr, Id<TransitLine> lineId) {
		super(startStopId, endStopId, startLinkId, endLinkId);
		this.route = tr;
		this.lineId=lineId;
		this.routeId = tr.getId();
		ArrayList<Id<Link>> routeLinks=new ArrayList<>();
		routeLinks.add( tr.getRoute().getStartLinkId() );
		routeLinks.addAll(tr.getRoute().getLinkIds());
		routeLinks.add( tr.getRoute().getEndLinkId() );
		
		//Get the corresponding analytical model link for the transitDirectLink
		boolean passedStartStop = false;
		for(Id<Link> linkId:routeLinks) {
			if(linkId.toString().equals(this.startingLinkId.toString())){
				passedStartStop = true;
			}else if(passedStartStop && linkId.toString().equals(this.endingLinkId.toString())) {
				break;
			}
			
			if(passedStartStop) {
				this.linkList.add(linkId);
			}
		}
	}
	
	public ArrayList<Id<Link>> getLinkList() {
		return linkList;
	}
	public Id<TransitLine> getLineId() {
		return lineId;
	}
	public Id<TransitRoute> getRouteId() {
		return routeId;
	}
	public abstract double getLinkTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,Map<String,Double>params,Map<String,Double>anaParams);
//	public TransitSchedule getTs() {
//		return ts;
//	}
	

}
