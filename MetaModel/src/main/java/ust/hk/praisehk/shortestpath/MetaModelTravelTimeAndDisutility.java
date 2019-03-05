package ust.hk.praisehk.shortestpath;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.util.LinkToLinkTravelTime;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.vehicles.Vehicle;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModel;

public class MetaModelTravelTimeAndDisutility implements TravelDisutility, TravelTime, LinkToLinkTravelTime{
	private static final Logger log = Logger.getLogger(MetaModelTravelTimeAndDisutility.class);
	private final AnalyticalModel trafficModel;
	private final double travelCostFactor;
	private final double marginalUtlOfDistance;
	private static int wrnCnt = 0 ;
	
	public MetaModelTravelTimeAndDisutility(double scaledMarginalUtilityOfTraveling,
			double scaledMarginalUtilityOfPerforming, double scaledMarginalUtilityOfDistance,AnalyticalModel model) {
		//super(scaledMarginalUtilityOfTraveling, scaledMarginalUtilityOfPerforming, scaledMarginalUtilityOfDistance);
		// usually, the travel-utility should be negative (it's a disutility)
				// but for the cost, the cost should be positive.
				this.travelCostFactor = -scaledMarginalUtilityOfTraveling + scaledMarginalUtilityOfPerforming;

				if ( wrnCnt < 1 ) {
					wrnCnt++ ;
					if (this.travelCostFactor <= 0) {
						log.warn("The travel cost in " + this.getClass().getName() + " under normal circumstances should be > 0. " +
								"Currently, it is " + this.travelCostFactor + "." +
								"That is the sum of the costs for traveling and the opportunity costs." +
								" Please adjust the parameters" +
								"'traveling' and 'performing' in the module 'planCalcScore' in your config file to be" +
						" lower or equal than 0 when added.");
						log.warn(Gbl.ONLYONCE) ;
					}
				}

				this.marginalUtlOfDistance = scaledMarginalUtilityOfDistance;
				this.trafficModel=model;
	}

	@Override
	public double getLinkTravelDisutility(final Link link, final double time, final Person person, final Vehicle vehicle) {
		if (this.marginalUtlOfDistance == 0.0) {
			return (this.getLinkTravelDisutility(link, time, person, vehicle)) * this.travelCostFactor;
		}
		return (this.getLinkTravelTime(link, time, person, vehicle)) * this.travelCostFactor - this.marginalUtlOfDistance * link.getLength();
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		if (this.marginalUtlOfDistance == 0.0) {
			return (link.getLength() / link.getFreespeed()) * this.travelCostFactor;
		}
		return (link.getLength() / link.getFreespeed()) * this.travelCostFactor
		- this.marginalUtlOfDistance * link.getLength();
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return this.trafficModel.getAverageLinkTravelTime(link.getId());
	}

	/**
	 * If travelling freespeed the turning move travel time is not relevant
	 */
	@Override
	public double getLinkToLinkTravelTime(Link fromLink, Link toLink, double time) {
		return this.getLinkTravelTime(fromLink, time, null, null);
	}
	
}
