package ust.hk.praisehk.metamodelcalibration.analyticalModel;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.collections.Tuple;






public class Trip {
	public static int weirdTrip=0;
	public void setPersonId(Id<Person> personId) {
		this.PersonId = personId.toString();
	}
	private String[] part;
	private String Oact;
	private String Dact;
	private String mode;
	private String PersonId;
	private double Originx;
	private double Originy;
	private double Destinationx;
	private double Destinationy;
	private double startTime;
	private double endTime;
	protected Coord activity1coord;
	protected Coord activity2coord;
	protected Id<AnalyticalModelODpair> ODpairId;
	protected Node originNode;
	
	protected Node destinationNode;
	private double expansionFactor=1;
	private double tripWalkingTime=0;
	private AnalyticalModelRoute route;
	private AnalyticalModelTransitRoute trRoute;
	private String subPopulationName=null;
	private static final Logger logger=Logger.getLogger(Trip.class);
	private double CarPCU=1;
	private Id<Link> startLinkId=null;
	private Id<Link> endLinkId=null;
	/**
	 * 
	 * @param line - containing all the data probably from a file (.csv)
	 * @param ind_O index of origin Id
	 * @param ind_D index of destination Id
	 * @param ind_Ox index of Origin X
	 * @param ind_Oy
	 * @param ind_Dx
	 * @param ind_Dy
	 * @param ind_mode
	 * @param ind_strt index of trip start time 
	 * @param ind_end index of trip end time
	 */
	
	
	public double getCarPCU() {
		return CarPCU;
	}

	public void setCarPCU(double carPCU) {
		CarPCU = carPCU;
	}

	/**
	 * Convert the TCSTime to MATSim time
	 * @param TCSTime
	 * @return
	 */
	private double fixtime(int TCSTime) {
		int hour=TCSTime/100;
		int min=TCSTime%100;
		
		return hour*3600+min*60;
	}
	
	/**
	 * Generate a Origin Destination Pair ID
	 * @param networkTPUSB The network with all centroids of TPUSB
	 * @return
	 */
	public Id<AnalyticalModelODpair> generateODpairId(Network networkTPUSB) {
		this.originNode=NetworkUtils.getNearestNode(networkTPUSB, activity1coord);
		this.destinationNode=NetworkUtils.getNearestNode(networkTPUSB, activity2coord);
		String ODPairID = this.getOriginNode().getId().toString()+"_"+this.getDestinationNode().getId().toString();
		if(this.getSubPopulationName()!=null) {
			ODpairId=Id.create(ODPairID+"_"+this.getSubPopulationName() , AnalyticalModelODpair.class);
		}else {
			ODpairId=Id.create(ODPairID , AnalyticalModelODpair.class);
		}
		return ODpairId;

	}
	
	
	public Id<Link> getStartLinkId() {
		return startLinkId;
	}

	public void setStartLinkId(Id<Link> startLinkId) {
		if(this.endLinkId!=null && this.endLinkId.equals(startLinkId)) {
			//throw new IllegalArgumentException("Same start and end linkId!!!! Please check.");
			Trip.weirdTrip++;
		}
		this.startLinkId = startLinkId;
	}
	
	public Tuple<Id<Link>,Id<Link>> getStartAndEndLinkId(){
		return new Tuple<Id<Link>,Id<Link>>(this.startLinkId,this.endLinkId);
	}

	public Id<Link> getEndLinkId() {
		return endLinkId;
	}

	public void setEndLinkId(Id<Link> endLinkId) {
		if(this.startLinkId!=null && this.startLinkId.toString().equals(endLinkId.toString())) {
			//throw new IllegalArgumentException("Same start and end linkId!!!! Please check.");
			
			Trip.weirdTrip++;
		}
		
		this.endLinkId = endLinkId;
	}

	/**
	 * ----------------------------------Getter and Setter---------------------------------------
	 * 
	 * 
	 */
	/**
	 * For TCS expansion Factor
	 * or for further population generation
	 * @return
	 */
	public double getExpansionFactor() {
		return expansionFactor;
	}
	
	public void setExpansionFactor(double expansionFactor) {
		this.expansionFactor = expansionFactor;
	}
	
	
	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Id<Person> getPersonId() {
		return Id.create(PersonId,Person.class);
	}

	public void setPersonId(String personId) {
		PersonId = personId;
	}

	public double getStartTime() {
		return startTime;
	}
	public void setStartTime(double strt) {
		this.startTime = strt;
	}
	public double getEndTime() {
		return endTime;
	}
	public void setEndTime(double endt) {
		this.endTime = endt;
	}
	public String getOriginActivity() {
		return Oact;
	}
	public void setOriginActivity(String oact) {
		Oact = oact;
	}
	public String getDestinationActivity() {
		return Dact;
	}
	public void setDestinationActivity(String dact) {
		Dact = dact;
	}
	
	public Coord getAct1coord() {
		return activity1coord;
	}
	public void setAct1coord(Coord act1coord) {
		this.activity1coord = act1coord;
	}
	public Coord getAct2coord() {
		return activity2coord;
	}
	public void setAct2coord(Coord act2coord) {
		this.activity2coord = act2coord;
	}
	public void setOriginNode(Node node){
		this.originNode=node;
	}
	public void setDestinationNode(Node node){
		this.destinationNode=node;
	}
	public Node getOriginNode(){
		return originNode;
	}
	public Node getDestinationNode(){
		return destinationNode;
	}

	public double getTripWalkingTime() {
		return tripWalkingTime;
	}

	public void setTripWalkingTime(double tripWalkingTime) {
		this.tripWalkingTime = tripWalkingTime;
	}

	public void setRoute(AnalyticalModelRoute route){		
		this.route=route;
	}
	
	public AnalyticalModelRoute getRoute() {
		return route;
	}
	public void setTrRoute(AnalyticalModelTransitRoute trRoute) {
		this.trRoute=trRoute;
	}
	public AnalyticalModelTransitRoute getTrRoute() {
		return this.trRoute;
	}
	
	public String getSubPopulationName() {
		return subPopulationName;
	}

	public void setSubPopulationName(String subPopulationName) {
		this.subPopulationName = subPopulationName;
	}
}
