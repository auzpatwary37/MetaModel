package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.utils.objectattributes.attributable.Attributes;

import ust.hk.praisehk.metamodelcalibration.analyticalModelImpl.CNLSUEModel;
import ust.hk.praisehk.metamodelcalibration.Utils.Tuple;


/**
 *
 * @author Ashraf
 *
 */

public abstract class AnalyticalModelLink implements Link{

	/**
	 * This is a wrapper class around the Link Interface of MATSim
	 * the variable link is the Link that the class is wrapped around
	 * the variable LinkCarVolume is the volume of car/car using passenger on the link. (same as the PCU of car is 1)
	 * the variable LinkTransitVolume is the volume of the transit vehicles converted into PCU. 
	 * 		(This is a constant and will not change)
	 * the variable LinkTransitPassengerVolume is the amount of passenger on the transit on that link.(It is not a constant).
	 * link TravelTime is the time needed for crossing the link. 
	 */
	
	
	protected Link link;
	protected double linkCarVolume=0;
	protected double linkTransitVolume=0;
	protected double linkTransitPassenger=0;
	//protected double linkTravelTime=0;
	protected double linkCarConstantVolume = 0; //A constant term added to the volume
	protected double gcRatio=1; //This is the gcRatio of the links.
	protected final double flowCapFactor;
	/**
	 * Constructor
	 * @param link: The wrapped Link
	 */
	public AnalyticalModelLink(Link link, double flowCapFactor) {
		this.link=link;
		this.flowCapFactor = flowCapFactor;
	}
	/**
	 * -------------------------------Wrapper class functions---------------------------------------------------------------
	 * 
	 */
	
	/**
	 * Calculate the link Travel Time
	 * @return
	 * has to be overridden in the actual class
	 * Can Be BPR 
	 * 
	 */
	
	public abstract double getLinkTravelTime(Tuple<Double,Double> timeBean,Map<String, Double> params,Map<String, Double> analyticalModelInternalParams);
	
	//It is probably not used?
//	public void resetLinkVolume(double linkCarConstant) {
//		this.linkCarVolume=0;
//		this.linkTransitPassenger=0;
//		this.linkCarConstantVolume = linkCarConstant;
//	}
	
	public void addLinkCarVolume(double lVolume) {
		this.linkCarVolume+=lVolume;
	}
	public void addLinkTransitPassengerVolume(double lVolume) {
		this.linkTransitPassenger+=lVolume;
	}
	
	public void addLinkTransitVolume(double lVolume) {
		this.linkTransitVolume+=lVolume;
	}
	
	public double getLinkCarVolume() {
		return linkCarVolume;
	}
	
	public double getLinkAADTVolume() {
		return linkCarVolume + linkTransitVolume + linkCarConstantVolume;
	}

	public double getLinkTransitVolume() {
		return linkTransitVolume;
	}

	public double getLinkTransitPassenger() {
		return linkTransitPassenger;
	}

	public void clearTransitPassangerFlow() {
		this.linkTransitPassenger=0;
	}
	
	public void clearLinkCarFlow() {
		this.linkCarVolume=0;
	}
	
	public double getGcRatio() {
		return gcRatio;
	}

	public void setGcRatio(double gcRatio) {
		if(gcRatio<0 || gcRatio>1) {
			throw new IllegalArgumentException("The g/c ratio should between 0 and 1!");
		}
		this.gcRatio = gcRatio;
	}
	
	public void setLinkCarConstantVolume(double linkCarConstantFlow) {
		if(linkCarConstantFlow<0) {
			throw new IllegalArgumentException("The link car constant should not be less than 0!");
		}
		this.linkCarConstantVolume = linkCarConstantFlow;
	}
	
	public void clearNANFlow() {
		if(Double.isNaN(this.linkCarVolume)) {
			this.linkCarVolume=0;
		}else if(Double.isNaN(this.linkTransitPassenger)) {
			this.linkTransitPassenger=0;
		}
	}
	
	/**
	 * -----------------------Wrapped Class Functions------------------------------------------------------
	 */
	@Override
	public Coord getCoord() {
		return link.getCoord();
	}

	@Override
	public Id<Link> getId() {
		return link.getId();
	}

	@Override
	public Attributes getAttributes() {
		return link.getAttributes();
	}

	@Override
	public boolean setFromNode(Node node) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//return link.setFromNode(node);
	}

	@Override
	public boolean setToNode(Node node) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//return link.setToNode(node);
	}

	@Override
	public Node getToNode() {
		return link.getToNode();
	}

	@Override
	public Node getFromNode() {
		return link.getFromNode();
	}

	@Override
	public double getLength() {
		return link.getLength();
	}

	@Override
	public double getNumberOfLanes() {
		return link.getNumberOfLanes();
	}

	@Override
	public double getNumberOfLanes(double time) {
		return link.getNumberOfLanes(time);
	}

	@Override
	public double getFreespeed() {
		return link.getFreespeed();
	}

	@Override
	public double getFreespeed(double time) {
		return link.getFreespeed(time);
	}

	@Override
	public double getCapacity() {
		return link.getCapacity() * flowCapFactor;
	}

	@Override
	public double getCapacity(double time) {
		return link.getCapacity(time);
	}

	@Override
	public void setFreespeed(double freespeed) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//link.setFreespeed(freespeed);
	}

	@Override
	public void setLength(double length) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//link.setLength(length);
	}

	@Override
	public void setNumberOfLanes(double lanes) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//link.setNumberOfLanes(lanes);
	}

	@Override
	public void setCapacity(double capacity) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//link.setCapacity(capacity);
	}

	@Override
	public void setAllowedModes(Set<String> modes) {
		throw new IllegalArgumentException("Not proper to modify it!");
		//link.setAllowedModes(modes);
	}

	@Override
	public Set<String> getAllowedModes() {
		return link.getAllowedModes();
	}

	@Override
	public double getFlowCapacityPerSec() {
		return link.getFlowCapacityPerSec();
	}

	@Override
	public double getFlowCapacityPerSec(double time) {
		return link.getFlowCapacityPerSec(time);
	}
}
