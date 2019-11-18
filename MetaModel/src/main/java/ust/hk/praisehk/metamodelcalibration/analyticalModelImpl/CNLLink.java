package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.matsim.api.core.v01.network.Link;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelLink;
import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;


/**
 * 
 * @author Ashraf
 *
 */
public class CNLLink extends AnalyticalModelLink{
	
	public static double effectiveCapacity = 0.9;
	
	/**
	 * a new parameter is required to store the link passenger volume by route and line Id 
	 * a HashMap<String, double> Has to be created with the String lineId_routeId as key and passenger volume as value 
	 */
	
	/**
	 * Constructor
	 * @param link
	 */
	public CNLLink(Link link, double flowCapFactor) {
		super(link, flowCapFactor);
	}
	
	private ConcurrentHashMap<String, Double> TransitMapping=new ConcurrentHashMap<>();
	
//  To be removed in April if no update.	
//	private double alpha=0.15;
//	private double beta=4;
//	
//	/**
//	 * 
//	 * @return: value of alpha in the BPR function
//	 */
//	public double getAlpha() {
//		return alpha;
//	}
//
//	/**
//	 * Set the value of alpha in the BPR function
//	 * @param alpha
//	 */
//	public void setAlpha(double alpha) {
//		this.alpha = alpha;
//	}
//	
//	/**
//	 * 
//	 * @return: value of beta in the BPR function 
//	 */
//
//	public double getBeta() {
//		return beta;
//	}
//
//	/**
//	 * Set the value of beta in the BPR function
//	 * @param beta
//	 */
//	public void setBeta(double beta) {
//		this.beta = beta;
//	}
	
	@Override
	public void clearTransitPassangerFlow() {
		this.linkTransitPassenger=0;
		this.TransitMapping.clear();
	}
	
	public double getMaximumFlowCapacity(Tuple<Double,Double> timeBean, Map<String,Double>params) {
		return super.getCapacity()*(timeBean.getSecond()-timeBean.getFirst())/3600*params.get("All "+CNLSUEModel.CapacityMultiplierName)*this.gcRatio * effectiveCapacity;
	}
	
	
	/**
	 * employs BPR travel time function
	 */
	@Override
	public double getLinkTravelTime(Tuple<Double,Double> timeBean, Map<String,Double>params, Map<String,Double>anaParams) {
		
		if(!this.link.getAllowedModes().contains("train")) {
			double totalpcu = super.getLinkAADTVolume();
			double capacity= getMaximumFlowCapacity(timeBean, params);
			double freeflowTime= super.getLength()/super.getFreespeed();
			
			double linkTravelTime = freeflowTime*(1+ anaParams.get(CNLSUEModel.BPRalphaName)*
									Math.pow(totalpcu/capacity, anaParams.get(CNLSUEModel.BPRbetaName))) / this.gcRatio;
			if(totalpcu / capacity > 3) {
				return linkTravelTime * Math.exp(6); //15 = 5 * (4 - 1)
			}else if(totalpcu > capacity) {
				return linkTravelTime * Math.exp(2* (totalpcu/capacity - 1));
			}
			
			return linkTravelTime;
		}else {
			return this.link.getLength()/(this.link.getFreespeed()*1000/3600); //Just approximate for links
		}
	}
	
//	public double getLinkTravelTimeSubPop(Tuple<Double,Double> timeBean,Map<String,Double>fullparams,Map<String,Double>anaParams,String subPopName) {
//		Map<String,Double>params=CNLSUEModelSubPop.generateSubPopSpecificParam(fullparams, subPopName);
//		if(!this.link.getAllowedModes().contains("train")) {
//		double totalpcu=super.getLinkCarVolume()+super.getLinkTransitVolume();
//		double capacity=super.getCapacity()*(timeBean.getSecond()-timeBean.getFirst())/3600*params.get(CNLSUEModel.CapacityMultiplierName)*this.gcRatio;
//		double freeflowTime=super.getLength()/super.getFreespeed();
//		double linkTravelTime=freeflowTime*(1+anaParams.get(CNLSUEModel.BPRalphaName)*Math.pow(totalpcu/capacity, anaParams.get(CNLSUEModel.BPRbetaName)));
//		return linkTravelTime;
//		}else {
//			linkTravelTime=this.link.getLength()/(this.link.getFreespeed()*1000/(3600));
//			return linkTravelTime;
//		}
//	}
	
	
	/**
	 * Use this function to store transit line and route specific passenger count 
	 * @param lineId_routeId
	 * @param volume
	 */
	//TODO: make this method additive and add zero passenger count during the transit vehicle loading 
	public void addTransitPassengerVolume(String lineId_routeId, double volume) {
		this.linkTransitPassenger+=volume;
		if(this.TransitMapping.containsKey(lineId_routeId)) {
			this.TransitMapping.put(lineId_routeId, this.TransitMapping.get(lineId_routeId)+ volume);
		}else {
			this.TransitMapping.put(lineId_routeId, volume);
		}
	}
	
	public double getTransitPassengerVolume(String lineId_routeId) {
		if(this.TransitMapping.containsKey(lineId_routeId)) {
			return this.TransitMapping.get(lineId_routeId);
		}else {
			return 0;
		}
	}
}
