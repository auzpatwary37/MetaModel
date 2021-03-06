package ust.hk.praisehk.shortestpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.algorithms.NetworkExpandNode.TurnInfo;
import org.matsim.core.network.algorithms.NetworkTurnInfoBuilderI;
import org.matsim.lanes.Lane;
import org.matsim.lanes.Lanes;
import org.matsim.lanes.LanesToLinkAssignment;

/**
 * The only thing this turn info do is consider the 'bus' mode and 'car' mode is
 * the same and allow turn between them.
 * 
 * @author eleead
 *
 */
public class CustomNetworkTurnInfoBuilder implements NetworkTurnInfoBuilderI {

	protected final Scenario scenario;
	protected final Set<String> equalModes;

	public CustomNetworkTurnInfoBuilder(Scenario scenario, Set<String> equalModes) {
		this.scenario = scenario;
		this.equalModes = equalModes;
	}

	@Override
	public Map<Id<Link>, List<TurnInfo>> createAllowedTurnInfos() {
		Map<Id<Link>, List<TurnInfo>> allowedInLinkTurnInfoMap = new HashMap<>();

		createAndAddTurnInfo(allowedInLinkTurnInfoMap);

		if (scenario.getConfig().network().getLaneDefinitionsFile() != null || //
				scenario.getConfig().qsim().isUseLanes()) {
			scenario.getLanes();
			Lanes ld = scenario.getLanes();
			Map<Id<Link>, List<TurnInfo>> lanesTurnInfoMap = createTurnInfos(ld);
			mergeTurnInfoMaps(allowedInLinkTurnInfoMap, lanesTurnInfoMap);
		}
		return allowedInLinkTurnInfoMap;
	}

	private Map<Id<Link>, List<TurnInfo>> createTurnInfos(Lanes laneDefs) {
		Map<Id<Link>, List<TurnInfo>> inLinkIdTurnInfoMap = new HashMap<>();
		Set<Id<Link>> toLinkIds = new HashSet<>();
		for (LanesToLinkAssignment l2l : laneDefs.getLanesToLinkAssignments().values()) {
			toLinkIds.clear();
			for (Lane lane : l2l.getLanes().values()) {
				if (lane.getToLinkIds() != null && (lane.getToLaneIds() == null || lane.getToLaneIds().isEmpty())) { 
					// make sure that it is a lane at the end of a link
					toLinkIds.addAll(lane.getToLinkIds());
				}
			}
			if (!toLinkIds.isEmpty()) {
				List<TurnInfo> turnInfoList = new ArrayList<TurnInfo>();
				for (Id<Link> toLinkId : toLinkIds) {
					turnInfoList.add(new TurnInfo(l2l.getLinkId(), toLinkId));
				}
				inLinkIdTurnInfoMap.put(l2l.getLinkId(), turnInfoList);
			}
		}

		return inLinkIdTurnInfoMap;
	}

	/**
	 * Creates a List of TurnInfo objects for every existing link of the network. If
	 * the links have mode attributes set, those are considered in TurnInfo
	 * creation.
	 */
	private void createAndAddTurnInfo(Map<Id<Link>, List<TurnInfo>> inLinkTurnInfoMap) {
		TurnInfo turnInfo = null;
		List<TurnInfo> turnInfosForInLink = null;
		for (Node node : scenario.getNetwork().getNodes().values()) {
			for (Link inLink : node.getInLinks().values()) {
				turnInfosForInLink = inLinkTurnInfoMap.get(inLink.getId());
				if (turnInfosForInLink == null) {
					turnInfosForInLink = new ArrayList<TurnInfo>();
					inLinkTurnInfoMap.put(inLink.getId(), turnInfosForInLink);
				}

				for (Link outLink : node.getOutLinks().values()) {
					if (!inLink.getAllowedModes().isEmpty() && !outLink.getAllowedModes().isEmpty()) {
						if (!Collections.disjoint(inLink.getAllowedModes(), this.equalModes)
								&& !Collections.disjoint(outLink.getAllowedModes(), this.equalModes)) {
							turnInfo = new TurnInfo(inLink.getId(), outLink.getId(), equalModes);
							turnInfosForInLink.add(turnInfo);
						}
					} else { // we have no mode information at all
						turnInfo = new TurnInfo(inLink.getId(), outLink.getId(), equalModes);
						turnInfosForInLink.add(turnInfo);
					}
				}
			}
		}
	}

	/**
	 * Modifies the first Map containing the allowed turning moves: All turning
	 * moves of a fromLink for that the second Map contains (only in this case!) an
	 * entry are checked for differences concerning outLinks and modes. If an
	 * outLink or mode is not contained in the restriction, the corresponding
	 * TurnInfo or mode is removed or modified in the first map.
	 */
	public final void mergeTurnInfoMaps(Map<Id<Link>, List<TurnInfo>> allowedInLinkTurnInfoMap,
			Map<Id<Link>, List<TurnInfo>> restrictingTurnInfoMap) {
		for (Map.Entry<Id<Link>, List<TurnInfo>> e : allowedInLinkTurnInfoMap.entrySet()) {
			Id<Link> inLinkId = e.getKey();
			List<TurnInfo> restrictingTurnInfos = restrictingTurnInfoMap.get(inLinkId);

			if (restrictingTurnInfos != null) { // there are restrictions for the inLink
				List<TurnInfo> allowedTurnInfos = new ArrayList<TurnInfo>(e.getValue());
				for (TurnInfo allowedForOutlink : allowedTurnInfos) {
					TurnInfo restrictionForOutlink = getTurnInfoForOutlinkId(restrictingTurnInfos,
							allowedForOutlink.getToLinkId());
					if (restrictionForOutlink == null) { // there is no turn at all allowed from the inLink to the
															// outLink
						allowedInLinkTurnInfoMap.get(inLinkId).remove(allowedForOutlink);
					} else { // turns are restricted to some modes or allowed without any mode information
						if (restrictionForOutlink.getModes() != null && allowedForOutlink.getModes() != null) {
							Set<String> commonModes = this.calculateCommonModes(restrictionForOutlink,
									allowedForOutlink);
							Set<String> allowedModes = allowedForOutlink.getModes();
							for (String mode : allowedModes) {
								if (!commonModes.contains(mode)) {
									allowedForOutlink.getModes().remove(mode);
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Linear search for the TurnInfo describing the turn to the outLinkId
	 */
	static TurnInfo getTurnInfoForOutlinkId(List<TurnInfo> turnInfoList, Id<Link> outLinkId) {
		for (TurnInfo ti : turnInfoList) {
			if (ti.getToLinkId().equals(outLinkId)) {
				return ti;
			}
		}
		return null;
	}

	private Set<String> calculateCommonModes(TurnInfo first, TurnInfo second) {
		Set<String> modes = new HashSet<String>();
		for (String mode : first.getModes()) {
			if (second.getModes().contains(mode)) {
				modes.add(mode);
			}
		}
		return modes;
	}
}
