package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.TransitLink;
import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;

/**
 * 
 * @author Ashraf
 *
 */
/*
 * TODO: Fix Route Utility
 */
public class CNLTransitRoute implements AnalyticalModelTransitRoute{
	
	private final Logger logger=Logger.getLogger(CNLTransitRoute.class);
	
	private TransitSchedule transitSchedule;
	private Scenario scenario;
	private final Id<AnalyticalModelTransitRoute> trRouteId;
	private double routeTravelTime=0;
	private double routeWalkingDistance=0;
	private double routeWaitingTime=0;
	private double routeFare=-1;
	
	private double busFare = -1;
	private double MTRFare = -1;
	
	private ArrayList<CNLTransitDirectLink> directLinks=new ArrayList<>();
	private ArrayList<CNLTransitTransferLink> transferLinks=new ArrayList<>();
	private Map<Id<TransitLink>, TransitLink> trLinks=new HashMap<>();
	private Map<String,Double> routeCapacity=new HashMap<>();
	
	/**
	 * Constructor
	 * these two lists holds all the pt legs and pt activity sequentially for one single transit trip
	 * @param ptlegList
	 * @param ptactivityList
	 */
	public CNLTransitRoute(ArrayList<Leg> ptlegList,ArrayList<Activity> ptactivityList, 
			TransitSchedule ts,Scenario scenario) {
		this.scenario=scenario;
		this.transitSchedule=ts;
		
		try {
		if(!(ptlegList.get(0).getMode().equals("transit_walk") && ptlegList.get(ptlegList.size()-1).getMode().equals("transit_walk"))) {
			logger.error("Invalid trip legs, The trip must have at least two walk legs at the start and end");
			throw new IllegalArgumentException("Invalid input for creating transit route");
		}else if (ptactivityList.size()!=ptlegList.size()+1) {
			logger.error("There must be exactly one more activity than no of trip legs");
			throw new IllegalArgumentException("Invalid input for creating transit route");
		}
		
		}catch(Exception e) {
			logger.error("could not create transit route, see error log.");
		}
		
		
		/*
		 * coming from backward and assuming there will always be at least one more transfer links than the direct links
		 */
		int transferLinkCount=0;
		int directLinkCount=0;
		String idstring;
		int a=(ptlegList.size()-1)/2-1;
		int b=(ptlegList.size()-1)/2;
		Map<Integer,CNLTransitDirectLink> tempDirectLinks=new HashMap<>();
		Map<Integer, CNLTransitTransferLink> tempTransferLinks=new HashMap<>();
		for(int i=ptlegList.size()-1;i>=0;i--) {
			Leg l=ptlegList.get(i);
			String startStopId=null;
			String endStopId=null;
			Id<Link> startLinkId=l.getRoute().getStartLinkId();
			Id<Link> endLinkId=l.getRoute().getEndLinkId();
			//System.out.println("testing");
			if(l.getMode().equals("transit_walk")) {
				transferLinkCount++;
				this.routeWalkingDistance+=l.getRoute().getDistance();
				CNLTransitTransferLink t;
				if (i==ptlegList.size()-1) {
					t=new CNLTransitTransferLink(startStopId, 
							endStopId, startLinkId, endLinkId, ts,null);	
				}else {
					t=new CNLTransitTransferLink(startStopId, 
							endStopId, startLinkId, endLinkId, ts,tempDirectLinks.get(a+1));
				}
				tempTransferLinks.put(b, t);
				b--;
			}else{
				directLinkCount++;
				CNLTransitDirectLink dlink=new CNLTransitDirectLink(l.getRoute().getRouteDescription(), 
						startLinkId, endLinkId, ts, scenario.getTransitVehicles());
				tempDirectLinks.put(a, dlink);
				a--;
				
			}
			
		}
		
		for(int i=0;i<=Collections.max(tempTransferLinks.keySet());i++) {
			this.transferLinks.add(tempTransferLinks.get(i));
		}
		for(int i=0;i<=Collections.max(tempDirectLinks.keySet());i++) {
			this.directLinks.add(tempDirectLinks.get(i));
		}
		
		idstring=this.transferLinks.get(0).getTrLinkId().toString();
		for(int i=0;i<this.directLinks.size();i++) {
			idstring+=this.directLinks.get(i).getTrLinkId().toString()+this.transferLinks.get(i+1).getTrLinkId().toString();
		}
		this.trRouteId=Id.create(idstring,AnalyticalModelTransitRoute.class);
		
		this.trLinks.put(this.transferLinks.get(0).getTrLinkId(), this.transferLinks.get(0));
		for(int i=0;i<this.directLinks.size();i++) {
			this.trLinks.put(this.directLinks.get(i).getTrLinkId(), this.directLinks.get(i));
			this.trLinks.put(this.transferLinks.get(i+1).getTrLinkId(),this.transferLinks.get(i+1));
		}
	}
	
	public CNLTransitRoute(ArrayList<CNLTransitTransferLink> transferLinks,ArrayList<CNLTransitDirectLink>dlink,Scenario scenario,TransitSchedule ts,
			double routeWalkingDistance,String routeId) {
		this.directLinks=dlink;
		this.transferLinks=transferLinks;
		this.transitSchedule=ts;
		this.routeWalkingDistance=routeWalkingDistance;
		this.trRouteId=Id.create(routeId, AnalyticalModelTransitRoute.class);

		this.trLinks.put(this.transferLinks.get(0).getTrLinkId(), this.transferLinks.get(0));
		for(int i=0;i<this.directLinks.size();i++) {
			this.trLinks.put(this.directLinks.get(i).getTrLinkId(), this.directLinks.get(i));
			this.trLinks.put(this.transferLinks.get(i+1).getTrLinkId(),this.transferLinks.get(i+1));
		}
		
	}
	
	@Override
	public double calcRouteUtility(Map<String, Double> params,Map<String, Double> anaParams,AnalyticalModelNetwork network,
			Map<String,FareCalculator>farecalc, TransferDiscountCalculator tdc, Tuple<Double,Double>timeBean) {
		
		double MUTravelTime=params.get(CNLSUEModel.MarginalUtilityofTravelptName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUDistance=params.get(CNLSUEModel.MarginalUtilityOfDistancePtName);
		double MUWalkTime=params.get(CNLSUEModel.MarginalUtilityOfWalkingName)/3600.0-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double MUWaitingTime=params.get(CNLSUEModel.MarginalUtilityofWaitingName)/3600-params.get(CNLSUEModel.MarginalUtilityofPerformName)/3600.0;
		double ModeConstant=params.get(CNLSUEModel.ModeConstantPtname);
		double MUMoney=params.get(CNLSUEModel.MarginalUtilityofMoneyName);
		double DistanceBasedMoneyCostWalk=params.get(CNLSUEModel.DistanceBasedMoneyCostWalkName);
		double fare=this.getFare(transitSchedule, farecalc, tdc);
		double travelTime=this.calcRouteTravelTime(network,timeBean,params,anaParams);
		double walkTime=routeWalkingDistance/1.4;
		double waitingTime=this.getRouteWaitingTime(anaParams,network);
		double distance=this.getRouteDistance(network);
		double utility=0;
		double MUTransfer=params.get(CNLSUEModel.UtilityOfLineSwitchName);
		
		utility=ModeConstant+
				travelTime*MUTravelTime-
				MUMoney*fare+ //The fare should be negative
				MUWalkTime*walkTime+
				MUMoney*DistanceBasedMoneyCostWalk*routeWalkingDistance+
				MUWaitingTime*waitingTime
				+MUTransfer*(this.transferLinks.size()-1)
				+MUDistance*distance*MUMoney;
		if(utility==0) {
			logger.warn("Stop!!! route utility is zero.");
		}
		return utility*anaParams.get(CNLSUEModel.LinkMiuName);
	}
	
	public void resetFare() {
		this.routeFare = -1;
	}

	@Override
	public double getFare(TransitSchedule ts, Map<String, FareCalculator> farecalc, TransferDiscountCalculator tdc) {
		if(ts==null) {
			ts=this.transitSchedule;
		}
		if(this.routeFare!=-1) {
			return this.routeFare;
		}
		this.routeFare = 0; //Start from 0.
		this.busFare = 0;
		this.MTRFare = 0;
		String startStopIdTrain=null;
		String endStopIdTrain=null;
		
		Id<TransitLine> lastTLineId = null;
		Id<TransitRoute> lastTRouteId = null;
		String lastMode = null;
		double lastFare = 0.;
		
		int k=0;
		double interchangeDiscount = -1;
		for(CNLTransitDirectLink dlink :this.directLinks) {
			k++;
			Id<TransitLine> tlineId = dlink.getLineId();
			Id<TransitRoute> trouteId = dlink.getRouteId();
			String mode = ts.getTransitLines().get(tlineId).getRoutes().get(trouteId).getTransportMode();
			double thisFare = 0;
			
			//Handling the train fare
			if(mode.equals("train")) { //If it is a train trip, just store the start stop Id.
				if(startStopIdTrain == null) {
					startStopIdTrain=dlink.getStartStopId(); //Train trip not yet started
					if(lastTLineId != null && interchangeDiscount == -1) { //If there is previous trip, try to enquire the transfer discount.
						interchangeDiscount = tdc.getInterchangeDiscount(lastTLineId, tlineId, lastTRouteId, trouteId, lastMode, mode, 0, 0, 0, lastFare, thisFare);
					}
				}
				endStopIdTrain=dlink.getEndStopId(); //Store the end stop.
				if(k == this.directLinks.size()) { //Already the last leg
					MTRFareCalculator mtrFare = (MTRFareCalculator) farecalc.get("train");
					double fare = mtrFare.getMinFare(null, null, Id.create(startStopIdTrain, TransitStopFacility.class),
							Id.create(endStopIdTrain, TransitStopFacility.class));
					if(fare > interchangeDiscount) { //It would only be charged if the interchange discount is lower
						this.routeFare += fare;
						this.MTRFare += fare;
						this.routeFare -= interchangeDiscount;
						this.MTRFare -= interchangeDiscount;
					}
				}
			}else{//not a train trip leg, so two possibilities, train trip just ended in the previous trip or completely new trip
				if(startStopIdTrain!=null) {//train trip just ended, the MTR fare will be added.
					MTRFareCalculator mtrFare=(MTRFareCalculator) farecalc.get("train");
					lastFare = mtrFare.getMinFare(null, null, Id.create(startStopIdTrain, TransitStopFacility.class),
							Id.create(endStopIdTrain, TransitStopFacility.class));
					if(lastFare > interchangeDiscount) { //It would only be charged if the interchange discount is lower
						this.routeFare += lastFare;
						this.MTRFare += lastFare;
						this.routeFare -= interchangeDiscount;
						this.MTRFare -= interchangeDiscount;
					}
					startStopIdTrain=null;
					endStopIdTrain=null;
				}
				FareCalculator thisLegFareCal = farecalc.get(mode);
				thisFare = thisLegFareCal.getFares(trouteId, tlineId, Id.create(dlink.getStartStopId(), TransitStopFacility.class), 
						Id.create(dlink.getEndStopId(), TransitStopFacility.class)).get(0);
				interchangeDiscount = tdc.getInterchangeDiscount(lastTLineId, tlineId, lastTRouteId, trouteId, lastMode, mode, 0, 0, 0, lastFare, thisFare);
				if(thisFare > interchangeDiscount) { //The fare collected would only be changed if the discount is smaller.
					this.routeFare += thisFare;
					this.routeFare -= interchangeDiscount;
					if(mode.equals("bus")) {
						this.busFare += thisFare;
						this.busFare -= interchangeDiscount;
					}
				}
				interchangeDiscount = -1;
			}
			lastTLineId = tlineId;
			lastTRouteId = trouteId;
			lastMode = mode;
			lastFare = thisFare;
		}
		return this.routeFare;
	}
	
	public double getBusFareCollected() {
		if(busFare == -1) {
			throw new RuntimeException("The bus fare is not yet processed!");
		}
		return busFare;
	}
	
	public double getMTRFareCollected() {
		if(MTRFare == -1) {
			throw new RuntimeException("The MTR fare is not yet processed!");
		}
		return MTRFare;
	}

	@Override
	public double calcRouteTravelTime(AnalyticalModelNetwork network,Tuple<Double,Double>timeBean, 
			Map<String,Double>params, Map<String,Double>anaParams) {
		double routeTravelTime=0;
		for(CNLTransitDirectLink dlink:this.directLinks) {
			routeTravelTime+=dlink.getLinkTravelTime(network,timeBean,params,anaParams);
			if(routeTravelTime > 3e5) {
				return 2e5;
			}
		}
		return routeTravelTime;
	}

	@Override
	public double getRouteWalkingDistance() {
		return this.routeWalkingDistance;
	}

	@Override
	public double getRouteWaitingTime(Map<String,Double> anaParams,AnalyticalModelNetwork network) {
		double routeWaitingTime=0;
		for(CNLTransitTransferLink tlink:this.transferLinks) {
			routeWaitingTime+=tlink.getWaitingTime(anaParams,network);
		}
		return routeWaitingTime;
	}
	
	
	/**
	 * Convenient method to break down route description
	 * has not been used in the current setup
	 * @param s
	 * @return
	 */
	private HashMap<String,String> parsedirectTransitLink(String s){
		HashMap<String, String> parsedOutput=new HashMap<>();
		
		String[] part=s.split("===");
		parsedOutput.put("startStopId",part[1].trim());
		parsedOutput.put("endStopId",part[4].trim());
		parsedOutput.put("lineId",part[2].trim());
		parsedOutput.put("routeId",part[3].trim());
		
		return parsedOutput;
	}



	@Override
	public Id<AnalyticalModelTransitRoute> getTrRouteId() {
		return this.trRouteId;
	}


	private double getRouteDistance(AnalyticalModelNetwork network) {
		double d=0;
		for(CNLTransitDirectLink l:this.directLinks) {
			for(Id<Link> lid:l.getLinkList()) {
				d+=network.getLinks().get(lid).getLength();
			}
		}
		return d;
	}

	@Override
	public ArrayList<Id<TransitLink>> getTrLinkIds() {
		return new ArrayList<Id<TransitLink>>(this.trLinks.keySet());
	}
	
	public Map<Id<TransitLink>,TransitLink> getTransitLinks(Map<String, Tuple<Double, Double>> timeBean,String timeBeanId){
		for(CNLTransitDirectLink dl:this.directLinks) {
			this.trLinks.put(dl.getTrLinkId(), dl);
		}
		for(CNLTransitTransferLink tl:this.transferLinks) {
			this.trLinks.put(tl.getTrLinkId(), tl);
		}
		
		return this.trLinks;
	}
	
	/**
	 * This function calculate and add the route capacity by the minimum capacity in the link in the route.
	 */
	@Override
	public void calcCapacityHeadway(Map<String,Tuple<Double,Double>>timeBeans,String timeBeanId) {
		double routecapacity=Double.MAX_VALUE;
		for(CNLTransitDirectLink dl:this.directLinks) {
			//System.out.println("test");
			dl.calcCapacityAndHeadway(timeBeans, timeBeanId);
			routecapacity=Double.min(routecapacity, dl.capacity);
		}
		this.routeCapacity.put(timeBeanId, routecapacity);
	}
	@Override
	public Map<String, Double> getRouteCapacity() {
		return routeCapacity;
	}

	@Override
	public AnalyticalModelTransitRoute cloneRoute() {
		ArrayList<CNLTransitDirectLink> dlinks=new ArrayList<>();
		ArrayList<CNLTransitTransferLink> transferLinks=new ArrayList<>();
		int i=0;
		for(CNLTransitTransferLink tl:this.transferLinks) {
			if(tl.getNextdLink()!=null) {
			dlinks.add(tl.getNextdLink().cloneLink(tl.getNextdLink()));
			transferLinks.add(tl.cloneLink(tl, dlinks.get(i)));
			}else {
				transferLinks.add(tl.cloneLink(tl, null));
			}
			i++;
		}
		CNLTransitRoute trRoute=new CNLTransitRoute(transferLinks,dlinks,this.scenario,this.transitSchedule,this.routeWalkingDistance,this.trRouteId.toString());
		
		return trRoute ; 
	}

	@Override
	public List<Leg> getLegListRoute(double departureTime) {
		// TODO Not implemented!
		return null;
	}
}	
