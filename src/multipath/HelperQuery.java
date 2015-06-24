package multipath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneLinkContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlanePathContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneSwcapContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneSwitchingCapabilitySpecificInfo;

import net.es.oscars.api.soap.gen.v06.Layer2Info;
import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.QueryResContent;
import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.api.soap.gen.v06.ReservedConstraintType;
import net.es.oscars.api.soap.gen.v06.UserRequestConstraintType;
import net.es.oscars.common.soap.gen.OSCARSFaultReport;

/***********************************************************************************************************************
* This class provides helper methods needed for MultipathOSCARSClient method queryMPReservation() to work appropriately.
* This class exists solely to provide a higher layer of modularity and keep MultipathOSCARSClient.java clean.
* 
* @author Jeremy
***********************************************************************************************************************/
public class HelperQuery 
{
	private static final String mpQueryOut = MultipathOSCARSClient.mpQueryOut;	// File containing subrequest statuses for queried MP reservations.
	
	    	
	/*********************************************************************************************************************************************************
	* Parses an MP-GRI into its corresponding subrequest GRIs and builds a list of QueryResContent objects (one for each subrequest).
	* 
	* @param multipathGRI
	* @return List of subrequest Query objects
	*********************************************************************************************************************************************************/
	protected ArrayList<QueryResContent> buildAllQueryResContents(String multipathGRI)
	{
		ArrayList<QueryResContent> allQueryContents = new ArrayList<QueryResContent>();
		
		String allSubrequestGRIs[] = multipathGRI.split(":");
		
		for(int oneGRI = 2; oneGRI < allSubrequestGRIs.length; oneGRI++)
		{			
			QueryResContent oneQuery = new QueryResContent();
			oneQuery.setGlobalReservationId(allSubrequestGRIs[oneGRI]);
			
			allQueryContents.add(oneQuery);
		}
		
		// In case multipathGRI is actually a unicast GRI (will happen for some multipath subrequest queries) //
		if(allQueryContents.size() == 0)
		{
			QueryResContent oneQuery = new QueryResContent();
			oneQuery.setGlobalReservationId(allSubrequestGRIs[0]);
			
			allQueryContents.add(oneQuery);
		}
		
		return allQueryContents;	
	}
		
	
	/*********************************************************************************************************************************************************
	* Writes subrequest query info to static ouptput file. 
	* This method is a helper for both querying and cancelling reservations:
	* - Queries call this method to populate the output file.
	* - Cancels will then use the information in the output file to ensure attempting cancellations won't throw exceptions.
	* 
	* Lines (of importance) to be written are in the format: 'Unicast Request gri: <gri>, status: <status>', including the spacing.
	* 
	* @param gri
	* @param allMPGRI
	* @param allResDetails
	* @param allResExceptions
	* @param allFaultReports
	*********************************************************************************************************************************************************/
	protected void writeAllStatusesToQueryOutputFile(String gri, ArrayList<QueryResContent> allMPGRI, ArrayList<ResDetails> allResDetails, ArrayList<Exception> allResExceptions, ArrayList<List<OSCARSFaultReport>> allFaultReports)
	{
		try
		{			
	   		// Write status of MP query to log file to prevent failure in cancelRequest
	   		FileWriter fstream = new FileWriter(mpQueryOut, false);	// Overwrite
	   		BufferedWriter outp = new BufferedWriter(fstream);

	   		outp.write("[queryMultipathReservation]   gri= " + gri + "  complete.\n");
	   		
	   		for(int mpID = 0; mpID < allMPGRI.size(); mpID++)
	   		{
	   			outp.write("\tSubRequest gri: " + allMPGRI.get(mpID).getGlobalReservationId() + ", status: " + allResDetails.get(mpID).getStatus() + "\n");
	   		}
	   		
	       	outp.close();
	       	fstream.close();
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	/*********************************************************************************************************************************************************
	* Reads subrequest query info from static output file. 
	* This method is a helper for cancelling reservations:
	* - Cancels will then use the information in the output file to ensure attempting cancellations won't throw exceptions.
	* This method is a counterpart to another helper method: writeAllStatusesToQueryOutputFile().
	* 
	* Lines (of importance) to be read are in the format: 'Unicast Request gri: <gri>, status: <status>', including the spacing.
	*  
	* @param gri
	* @param allMPGRI
	* @param allResDetails
	* @param allResExceptions
	* @param allFaultReports
	*********************************************************************************************************************************************************/	
	protected ArrayList<SubrequestTuple> readQueryOutput()
	{
		ArrayList<SubrequestTuple> allSubrequests = new ArrayList<SubrequestTuple>();
		
		try
		{
			// Open the file containing output from previous call to queryMPReservation() to get status of all MP subrequests 
			FileInputStream fstream = new FileInputStream(mpQueryOut);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			
			// Read every line in mp_query_out.txt and make SubrequestTuple objects for each subrequest listed
			while(true)
			{
				strLine = br.readLine();
				
				if(strLine == null)
					break;
				
				if(strLine.contains("[queryMultipathReservation]"))	// Line of Junk separating sub-group recursive query output
					continue;
							
				String subrequestGRI = strLine.substring(strLine.indexOf("gri: ") + 5, strLine.indexOf(", status:"));
				String subrequestStatus = strLine.substring(strLine.indexOf("status: ") + 8);
				
				SubrequestTuple oneSubrequest = new SubrequestTuple(subrequestGRI, subrequestStatus);
				
				allSubrequests.add(oneSubrequest);
			}
		
		
			br.close();
			in.close();
			fstream.close();
		
		}
		catch(IOException e){ e.printStackTrace(); }
		
		return allSubrequests;
	}

	
    /*********************************************************************************************************************************************************
    * Print out pertinent information about a unicast reservation.
    * 
    * Most of this method is borrowed/repurposed from the OSCARS api test file IDCTest.java
    * 
    * @param resDetails, Must contain a userConstraint, may contain a reservedConstaint/
    *       If reservedConstraint exists, THEN use info from it, ELSE use userConstraint.     
    *********************************************************************************************************************************************************/
    protected void printResDetails(ResDetails resDetails) 
    {
        System.out.println("\nGRI: " + resDetails.getGlobalReservationId());
        System.out.println("Login: " + resDetails.getLogin());
        System.out.println("Description: " + resDetails.getDescription());
        System.out.println("Status: " + resDetails.getStatus().toString());
        
        UserRequestConstraintType userConstraint = resDetails.getUserRequestConstraint();
        ReservedConstraintType reservedConstraint = resDetails.getReservedConstraint();
        PathInfo pathInfo = null;
        String pathType = null;
        CtrlPlanePathContent path;
        List<CtrlPlaneHopContent> hops;

        if (reservedConstraint !=  null) 
        {
            System.out.println("startTime: " + new Date(reservedConstraint.getStartTime()*1000).toString());
            System.out.println("endTime: " + new Date(reservedConstraint.getEndTime()*1000).toString());
            System.out.println("bandwidth: " + Integer.toString(reservedConstraint.getBandwidth()));
            
            pathInfo=reservedConstraint.getPathInfo();
            pathType = "reserved";
        } 
        else 
        {            
            if (userConstraint != null) 
            {
            	System.out.println("startTime: " + new Date(userConstraint.getStartTime()*1000).toString());
                System.out.println("endTime: " + new Date(userConstraint.getEndTime()*1000).toString());
                System.out.println("bandwidth: " + Integer.toString(userConstraint.getBandwidth()));
                
                pathInfo=userConstraint.getPathInfo();
                pathType="requested";
                System.out.println("no path reserved, using requested path ");
            }
            else
            {
                System.out.println("invalid reservation, no reserved or requested path");
                return;
            }
        }
        
        path = pathInfo.getPath();
        
        if (path != null) 
        {
            hops = path.getHop();
            
            if (hops.size() > 0) 
            {
                System.out.println("Hops in " + pathType + " path are:");
            
                for ( CtrlPlaneHopContent ctrlHop : hops ) 
                {
                    CtrlPlaneLinkContent link = ctrlHop.getLink();
                    String vlanRangeAvail = "any";
                    
                    if (link != null ) 
                    {
                        CtrlPlaneSwcapContent swcap= link.getSwitchingCapabilityDescriptors();
                        
                        if (swcap != null) 
                        {
                            CtrlPlaneSwitchingCapabilitySpecificInfo specInfo = swcap.getSwitchingCapabilitySpecificInfo();
                            
                            if (specInfo != null) 
                            {
                                vlanRangeAvail = specInfo.getVlanRangeAvailability(); 
                            }
                        }
                        
                        System.out.println(link.getId() + " vlanRange: " + vlanRangeAvail);
                    } 
                    else 
                    {
                        String id = ctrlHop.getLinkIdRef();
                        System.out.println(id);
                    }
                }
            }
            else 
            {
                Layer2Info layer2Info = pathInfo.getLayer2Info();
                
                if (layer2Info != null) 
                {
                    String vlanRange = "any";
                    if (layer2Info.getSrcVtag() != null) 
                    {
                        vlanRange = layer2Info.getSrcVtag().getValue();
                    }
                    System.out.println("Source urn: " + layer2Info.getSrcEndpoint() + " vlanTag:" + vlanRange);
                    
                    vlanRange = "any";
                    if (layer2Info.getDestVtag() != null) 
                    {
                        vlanRange = layer2Info.getDestVtag().getValue();
                    }
                    System.out.println("Destination urn: " + layer2Info.getDestEndpoint() + " vlanTag:" + vlanRange);
                }
            }
        } 
        else 
        {
            System.out.println("no path information in " + pathType + " constraint");
        }
    }

   
    
    /*********************************************************************************************************************************************************
    * Print out pertinent information about a Multipath reservation: 
    * - MP-GRI
    * - List of subrequest GRIs and statuses.
    * 
    * This method is very light to reduce unnecessary output and was designed with a specific intention to facilitate testing.
    * More information is passed into this method than is actually printed out. 
    * - Modifications concerning the level of Multipath request output from queries can be altered here.
    * 
    * @param gri, MP-GRI
    * @param allQueries, set of the QueryResContents (including Multipath sub-Groups) for a Multipath GRI
    * @param allResDetails, set of Query Response details (including dummy Multipath sub-Groups)
    * @param allReports, set of OSCARSFaultReports from a Query Response (including dummies for successful subrequest reservations).
    *********************************************************************************************************************************************************/
    protected void printMPResDetails(String gri , ArrayList<QueryResContent> allQueries, ArrayList<ResDetails> allResDetails, ArrayList<List<OSCARSFaultReport>> allReports)
    {
    	String allOutput = "\n\n[queryMultipathReservation]   gri= " + gri + " complete.\n";
    	int mpID = -1;
    	
    	for(ResDetails details : allResDetails)
    	{
    		mpID++;
    		
	    	CtrlPlanePathContent path = null;
	    	ReservedConstraintType reservedConstraint;
	    	UserRequestConstraintType userConstraint;
	    	PathInfo pathInfo;
	    	String status = null;
	    	    		    		    	
	    	if(details.getGlobalReservationId().startsWith("MP"))
	    	{
	    		allOutput += "\t+ MP Sub-group gri:       " + details.getGlobalReservationId() + ", status: " +  details.getStatus() + "\n";
	    		continue;
	    	}
	    	else
	    	{
	    		allOutput += "\t+ Unicast subrequest gri: " + details.getGlobalReservationId() + ", status: " + details.getStatus() + "\n";
	    		
	    		if(details.getStatus().equals("BAD_GRI"))
	    			continue;
	    	}
	    		    	
	    	reservedConstraint = details.getReservedConstraint();
	    	userConstraint = details.getUserRequestConstraint();
	    	
	        if (reservedConstraint !=  null) 
	        {
	            pathInfo=reservedConstraint.getPathInfo();
	            path = pathInfo.getPath();
	            status = details.getStatus();
	            allOutput += "\t\tBandwidth: " + reservedConstraint.getBandwidth() + "\n";
	        }
	        else
	        {
	        	pathInfo = userConstraint.getPathInfo();
	        }
	  		
	        if (path != null) 
	        {
	        	List<CtrlPlaneHopContent> hops = path.getHop();
                  
                if (hops.size() > 0) 
                {
                	if(!(status.equals("CANCELLED") || status.equals("FINISHED")))
                		allOutput += "\t\tHops in " + status + " path are:\n";
                	else
                		allOutput += "\t\tHops in " + status + " path were:\n";
            	
                	for(CtrlPlaneHopContent ctrlHop : hops) 
                	{
                		CtrlPlaneLinkContent link = ctrlHop.getLink();
                		String vlanRangeAvail = "any";
                		
                		if (link != null ) 
                		{
                			CtrlPlaneSwcapContent swcap= link.getSwitchingCapabilityDescriptors();
                			if (swcap != null) 
                			{
                				CtrlPlaneSwitchingCapabilitySpecificInfo specInfo = swcap.getSwitchingCapabilitySpecificInfo();
                				if (specInfo != null) 
                				{
                					vlanRangeAvail = specInfo.getVlanRangeAvailability(); 
                				}
                			}
                			allOutput += "\t\t- " + link.getId() + ", vlanRange: " + vlanRangeAvail + "\n";
                		} 
                		else 
                		{
                			String id = ctrlHop.getLinkIdRef();
                			allOutput += id;
                		}
                	}
                }
            }
	        
	        // Include errors in the query report
	        if (allReports.get(mpID) != null && !allReports.get(mpID).isEmpty()) 
       		{
	        	allOutput += printFaultDetails(allReports.get(mpID));
       		}
  		}
    
    	System.out.println(allOutput);
    }
    
    /*********************************************************************************************************************************************************
    * Return (minimal) OSCARSFaultReport information about a reservation: 
    * - Useful to observe the causes of FAILED requests.
    *  
    * @param faultReports, Must contain a userConstraint, may contain a reservedConstaint/
    *       If reservedConstraint exists, THEN use info from it, ELSE use userConstraint.     
    *********************************************************************************************************************************************************/
    protected String printFaultDetails(List<OSCARSFaultReport> faultReports)
    {
    	String errorOutput = new String();
        
    	for (OSCARSFaultReport rep: faultReports) 
    		errorOutput += "\t\t! " + "Error:   " + rep.getErrorMsg() + "\n";
        
        return errorOutput;
    }
}
