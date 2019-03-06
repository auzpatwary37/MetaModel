package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TripChain;


/**
 * 
 * @author Ashraf
 *
 */

public class CNLTripChain extends TripChain{
	
	private final String mode;

	public CNLTripChain(Plan plan, TransitSchedule ts,Scenario scenario) {
		super(plan, ts,scenario);
		mode = getModeFromPlan(plan);
	}
	
	private static String getModeFromPlan(Plan plan) {
		for (PlanElement pe: plan.getPlanElements()) {
			if(pe instanceof Leg) {
				Leg l = (Leg) pe;
				return l.getMode();
			}
		}
		throw new IllegalArgumentException("The plan does not have route!");
	}
	
	String getMode() {return mode;}

	@Override
	protected AnalyticalModelRoute createRoute(Route r) {
		
		return new CNLRoute(r);
	}

	@Override
	protected AnalyticalModelTransitRoute getTransitRoute(ArrayList<Leg> ptlegList, ArrayList<Activity> ptactivityList, TransitSchedule ts,Scenario scenario) {
		return new CNLTransitRoute(ptlegList,ptactivityList,ts,scenario);
	}


	
}
