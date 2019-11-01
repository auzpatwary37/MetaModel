package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.lanes.Lanes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelNetwork;
import ust.hk.praisehk.shortestpath.SignalFlowReductionGenerator;



/**
 * 
 * @author Ashraf
 *
 */
public class CNLNetwork extends AnalyticalModelNetwork{
	
	boolean considerLinkToLink = false;
	/**
	 * constructor same as SUE Network
	 */
	public CNLNetwork(Network network, Lanes lanes, double flowCapFactor){
		
		for(Id<Node> NodeId:network.getNodes().keySet()){
			this.network.addNode(cloneNode(network.getNodes().get(NodeId),network.getFactory()));
		}
		for(Id<Link> linkId:network.getLinks().keySet()){
			if(lanes == null)
				this.network.addLink(new CNLLink(network.getLinks().get(linkId), flowCapFactor));
			else {
				this.network.addLink(new CNLLinkToLink(network.getLinks().get(linkId), lanes.getLanesToLinkAssignments().get(linkId), flowCapFactor));
				considerLinkToLink = true;
			}
		}
		
	}
	
	
//	public Map<Id<Link>, CNLLink> getCNLLinks(){
//		return (Map<Id<Link>,CNLLink>)this.network.getLinks();
//	}
//	
	
	@Override
	public CNLNetwork createNetwork(Network network, double flowCapFactor) {
		CNLNetwork newNetwork=new CNLNetwork(network, null, flowCapFactor);
		return newNetwork;
	}


	@Override
	public void clearLinkVolumesfull() {
		for(Link link: this.network.getLinks().values()) {
			((CNLLink) link).clearLinkCarFlow();
			((CNLLink) link).clearTransitPassangerFlow();
		}
	}

	@Override
	public void clearLinkCarVolumes() {
		for(Link link: this.network.getLinks().values()) {
			((CNLLink) link).clearLinkCarFlow();
		}
		
	}

	@Override
	public void clearLinkTransitPassangerVolume() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearTransitPassangerFlow();
		}
	}
	
	public void clearLinkCarVolume() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearLinkCarFlow();
		}
	}
	@Override
	public void clearLinkNANVolumes() {
		for(CNLLink cl:this.getLinks().values()) {
			cl.clearNANFlow();
		}
	}

	@Override
	public Map<Id<Link>, CNLLink> getLinks() {		
		return (Map<Id<Link>,CNLLink>)this.network.getLinks();
	}


	@Override
	public void addLinkTransitVolume(TransitSchedule ts) {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * !!!CAUTION!!!
	 * This method overlays the transit vehicles on the links of the network
	 * @param ts
	 * @param scenario
	 * This do not work please do not use
	 */
	@Override
	public void overlayTransitVehicles(TransitSchedule ts, Scenario scenario) {
		throw new IllegalArgumentException("Not implemented!");
	}


	/**
	 * This method will update the g/c ratio of the link calculated from the network
	 * the capacity will be multiplied with this ratio in the calculation of travel time 
	 * default is set to 1
	 */
	@Override
	public void updateGCRatio(SignalFlowReductionGenerator signalGC) {
		for(Link link: this.network.getLinks().values()) {
			System.out.print("");
			double gcRatio = signalGC.getGCratio(link, null); //Try this
			if(gcRatio>0 && gcRatio <=1)
				((CNLLink) link).setGcRatio(gcRatio);
			else {
				throw new RuntimeException("The GC ratio is wrong!");
			}
		}
		if(considerLinkToLink) {
			for(Link link: this.network.getLinks().values()) {
				for(Id<Link> toLinkId: ((CNLLinkToLink) link).getToLinks()) {
					double gcRatio = signalGC.getGCratio(link, toLinkId);
					if(gcRatio>=0 && gcRatio <=1)
						((CNLLinkToLink) link).setToLinkGCRatio(toLinkId, gcRatio);
					else {
						throw new RuntimeException("The GC ratio is wrong!");
					}
				}
			}
		}
	}

}
