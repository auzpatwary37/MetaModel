package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;
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
	public abstract double getTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,LinkedHashMap<String,Double>params,LinkedHashMap<String,Double>anaParams);
	
	/**
	 * This one gives the total route distance 
	 * For distance based money cost
	 * @return
	 */
	public abstract double getRouteDistance();
	
	/**
	 * This calculates the route utility of the specific routes
	 * params are the linked Hash map containing all the parameter values
	 * @param parmas
	 * @return
	 */
	public abstract double calcRouteUtility(LinkedHashMap<String,Double> parmas,LinkedHashMap<String,Double> anaParam,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean);
	
	/**
	 * This is for link toll or other moneytery cost
	 * @return
	 */
	public abstract double getOtherMoneyCost();
	public abstract String getRouteDescription();
	public abstract Id<AnalyticalModelRoute> getRouteId();
	public abstract ArrayList<Id<Link>> getLinkIds();
	
	public static Route PathToRouteConverter(Path p) {
		Id<Link> startLinkId=null;
		Id<Link>endLinkId=null;
		List<Id<Link>>linkList=new ArrayList<>();
		for(int i=0;i<p.links.size();i++) {
			if(i==0) {
				startLinkId=p.links.get(i).getId();
			}else if(i>0 &&i<p.links.size()-1) {
				linkList.add(p.links.get(i).getId());
			}else if(i==p.links.size()-1) {
				endLinkId=p.links.get(i).getId();
			}
		}
		Route r=RouteUtils.createLinkNetworkRouteImpl(startLinkId, linkList, endLinkId);
		return r;
	}

	public Route getRoute();
}
