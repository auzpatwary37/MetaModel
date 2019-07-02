package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;

public class CNLRoute implements AnalyticalModelRoute{

	private final Id<AnalyticalModelRoute> routeId;
	//private double travelTime=0;
	private double distanceTravelled=0;
	private ArrayList<Id<Link>>links=new ArrayList<>();
	private double RouteUtility=0;
	private final Route route;
	
	public CNLRoute(Route r) {
		String[] part=r.getRouteDescription().split(" ");
		for(String s:part) {
			links.add(Id.createLinkId(s.trim()));
			}
		this.distanceTravelled=r.getDistance();
		this.routeId=Id.create(r.getRouteDescription(), AnalyticalModelRoute.class);
		this.route=r;
	}
	
	public CNLRoute(Path p,Link startLink, Link endLink) {
		this(AnalyticalModelRoute.PathToRouteConverter(p,startLink,endLink));
	}
	
	@Override
	public Route getRoute() {
		return route;
	}

	@Override
	public double getTravelTime(AnalyticalModelNetwork network, Tuple<Double,Double> timeBean, LinkedHashMap<String,Double> params, 
			LinkedHashMap<String,Double> anaParams) {
		double travelTime = 0.;
		for(int i = 0; i < this.links.size(); i++) {
			Link thisLink = network.getLinks().get(this.links.get(i));
			if(thisLink instanceof CNLLinkToLink && i < this.links.size() - 1) {
				travelTime += ((CNLLinkToLink)thisLink).getLinkToLinkTravelTime(this.links.get(i+1), timeBean, params, anaParams);
			}else {
				travelTime += ((CNLLink)thisLink).getLinkTravelTime(timeBean,params,anaParams);
			}
			if(travelTime > 2e6) {
				return 2e6;
			}
		}                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       		return travelTime;
	}
	@Override
	public double getRouteDistance() {
		return this.distanceTravelled;
	}
	
	/**
	 * This is one of the most important and tricky function
	 * Takes all the parameters as input and calculates the route utility
	 * 
	 * The current utility function: 
	 * Will be designed later
	 *  
	 */
	@Override
	public double calcRouteUtility(LinkedHashMap<String, Double> params,LinkedHashMap<String, Double> anaParmas,AnalyticalModelNetwork network,Tuple<Double,Double>timeBean) {
		
		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelCarName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant;
		if(params.get(CNLSUEModel.ModeConstantCarName)==null) {
			ModeConstant=0;
		}else {
			ModeConstant=params.get(CNLSUEModel.ModeConstantCarName);
		}
		Double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
		if(MUMoney==null) {
			MUMoney=1.;
		}
		Double DistanceBasedMoneyCostCar=params.get(CNLSUEModel.DistanceBasedMoneyCostCarName);
		if(DistanceBasedMoneyCostCar==null) {
			DistanceBasedMoneyCostCar=0.;
		}
		double MUDistanceCar=params.get(CNLSUEModel.MarginalUtilityofDistanceCarName);
		
		this.RouteUtility = ModeConstant+
				this.getTravelTime(network,timeBean,params,anaParmas)*MUTravelTime+
				(MUDistanceCar+MUMoney*DistanceBasedMoneyCostCar)*this.distanceTravelled;
				
 		if(this.RouteUtility==0) {
 			System.out.println("Debug!!!!");
 		}
		return this.RouteUtility*anaParmas.get(CNLSUEModel.LinkMiuName);
	}
	@Override
	public double getOtherMoneyCost() {
		// TODO This method is for future expansion
		return 0;
	}
	
	@Override
	public String getRouteDescription() {
		return this.routeId.toString();
	}

	@Override
	public Id<AnalyticalModelRoute> getRouteId(){
		return this.routeId;
	}

	@Override
	public ArrayList<Id<Link>> getLinkIds() {
		return this.links;
	}
}
