package multipath;

import java.util.ArrayList;
import java.util.List;

import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.common.soap.gen.OSCARSFaultReport;

/**
 * SubrequestTuple.java
 * @author Jeremy Plante
 * 
 * This class represents a wrapper used in conjunction with ManycastOSCARSClient.java in the same package.
 * Used as a Tuple to maintain state info for individual Unicast subrequests when creating Manycast circuit requests.
 * 
 * This class is easily extensible. It is used a certain way with the current constructors, but if you wish 
 * to write your own to get more types of encapsulated details about a given request, it is easy.
 * For example, if you have an application which requires you to also get the description in addition to the provide parameters,
 * just add a private global variable and a constructor with the description parameter. 
 */
public class SubrequestTuple
{
	private String subrequestGRI;
	private String subrequestStatus;
	private String subrequestVlan;
	private String subrequestGroupGRI;
	private ResDetails subrequestDetails;
	private List<OSCARSFaultReport> subrequestErrors;
	
	private ArrayList<ResDetails> subrequestDetailsList;			// Used for Manycast Queries
	private ArrayList<List<OSCARSFaultReport>> subrequestErrorsList;	// Used for Manycast Queries
		
	public SubrequestTuple(String gri, String stats, String vlan)
	{
		subrequestGRI = gri;
		subrequestStatus = stats;
	}
	
	public SubrequestTuple(String gri, String stats)
	{
		subrequestGRI = gri;
		subrequestStatus = stats;
	}

	public SubrequestTuple(ResDetails details, List<OSCARSFaultReport> errors)
	{
		subrequestDetails = details;
		subrequestErrors = errors;
	}
	
	public SubrequestTuple(String mcGri, ArrayList<ResDetails> allDetails, ArrayList<List<OSCARSFaultReport>> allErrors)
	{
		subrequestDetailsList = allDetails;
		subrequestErrorsList = allErrors;
		subrequestGroupGRI = mcGri;
	}
	
	public String getGRI()
	{
		return subrequestGRI;
	}
	
	public String getStatus()
	{
		return subrequestStatus;
	}
	
	public String getVlan()
	{
		return subrequestVlan;
	}
	
	public ResDetails getDetails()
	{
		return subrequestDetails;
	}
	
	public List<OSCARSFaultReport> getErrors()
	{
		return subrequestErrors;
	}
	
	public String getGroupGRI()
	{
		return subrequestGroupGRI;
	}
	
	public ArrayList<ResDetails> getAllDetails()
	{
		return subrequestDetailsList;
	}
	
	public ArrayList<List<OSCARSFaultReport>> getAllErrors()
	{
		return subrequestErrorsList;
	}
}