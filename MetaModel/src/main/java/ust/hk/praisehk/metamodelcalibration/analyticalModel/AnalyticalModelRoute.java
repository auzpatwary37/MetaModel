package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

/**
 * 
 * @author Ashraf
 *
 * @param <anaNet> type of network used
 * must extend AnalyticalModelNetwork
 */
public interface AnalyticalModelRoute{
	
	/**
	 * This gives the travel time of the route 
	 * @return
	 */
	public abstract double getTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,Map<String,Double>params,Map<String,Double>anaParams);
	
	/**
	 * This one gives the total route distance 
	 * For distance based money cost
	 * @return
	 */
	public abstract double getRouteDistance();
	
	/**
	 * This calculates the route utility of the specific routes
	 * params are the linked Hash map containing all the parameter values
	 * @param params2
	 * @return
	 */
	public abstract double calcRouteUtility(Map<String, Double> params2,Map<String, Double> internalParams,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean);
	
	/**
	 * This is for link toll or other moneytery cost
	 * @return
	 */
	public abstract double getOtherMoneyCost();
	public abstract String getRouteDescription();
	public abstract Id<AnalyticalModelRoute> getRouteId();
	public abstract ArrayList<Id<Link>> getLinkIds();
	
	public static Route PathToRouteConverter(Path p, Link startLink, Link endLink) {
		double distance=0;
		Id<Link> startLinkId=startLink.getId();
		distance+=startLink.getLength();
		Id<Link>endLinkId=endLink.getId();
		distance+=endLink.getLength();
		List<Id<Link>>linkList=new ArrayList<>();
		for(int i=0;i<p.links.size();i++) {
			
			linkList.add(p.links.get(i).getId());
			distance+=p.links.get(i).getLength();
		}
		Route r=RouteUtils.createLinkNetworkRouteImpl(startLinkId, linkList, endLinkId);
		r.setDistance(distance);
		if(endLinkId.equals(null)) {
		System.out.println("Debug point");
		}
		return r;
	}

	public Route getRoute();
}
