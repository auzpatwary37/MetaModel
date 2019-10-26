package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;

import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;


/**
 * 
 * @author Ashraf
 *
 * @param <anaNet> Type of network that has been used
 */

public interface AnalyticalModelTransitRoute{
	
	/**
	 * calculates the route utility
	 * @param params2
	 * @return
	 */
	public abstract double calcRouteUtility(Map<String, Double> params2, Map<String, Double> internalParams,
			AnalyticalModelNetwork network, Map<String, FareCalculator> farecalc, TransferDiscountCalculator tdc,
			Tuple<Double, Double> timeBean);
	
	/**
	 * Calculates the route fare
	 * @param fc
	 * @return
	 */
	public abstract double getFare(TransitSchedule ts,Map<String,FareCalculator> farecalc, TransferDiscountCalculator tdc);
	
	/**
	 * Calculates the route travel Time (Only direct Link Travel Times are taken)
	 * @param network
	 * @return
	 */
	public abstract double calcRouteTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean,Map<String,Double>params,Map<String,Double>anaParams);
	/**
	 * returns the route total walking distance 
	 * @return
	 */
	public abstract double getRouteWalkingDistance();
	/**
	 * Calculates and returns the route waiting time
	 * @return
	 */
	public abstract double getRouteWaitingTime(Map<String,Double> anaParams,AnalyticalModelNetwork network);

	public abstract Id<AnalyticalModelTransitRoute> getTrRouteId();
	
	public abstract ArrayList<Id<TransitLink>> getTrLinkIds();
	
	public Map<String, Double> getRouteCapacity();
	
	public List<Leg> getLegListRoute(double departureTime);
	
	public void calcCapacityHeadway(Map<String, Tuple<Double, Double>> timeBean,String timeBeanId);
	
	public abstract AnalyticalModelTransitRoute cloneRoute();
}
