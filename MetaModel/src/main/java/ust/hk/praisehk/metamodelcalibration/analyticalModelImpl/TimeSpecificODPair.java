package ust.hk.praisehk.metamodelcalibration.analyticalModelImpl;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.Tuple;

import ust.hk.praisehk.metamodelcalibration.Utils.TimeBeanUtils;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelODpair;

public class TimeSpecificODPair extends AnalyticalModelODpair {
	private final String timeBin;

	public TimeSpecificODPair(Node onode, Node dnode, Network network, 
			Map<String, Tuple<Double, Double>> timeBean2,String subPopName, double time) {
		super(onode, dnode, network, timeBean2, subPopName);
		timeBin = TimeBeanUtils.findTimeBean(timeBean2, time);
	}

	@Override
	public Id<AnalyticalModelODpair> getODpairId(){
		return Id.create(super.getODpairId().toString()+"_"+timeBin, AnalyticalModelODpair.class);
	}
}
