package multipath;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneHopContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlaneLinkContent;
import org.ogf.schema.network.topology.ctrlplane.CtrlPlanePathContent;

import net.es.oscars.api.soap.gen.v06.CreateReply;
import net.es.oscars.api.soap.gen.v06.OptionalConstraintType;
import net.es.oscars.api.soap.gen.v06.OptionalConstraintValue;
import net.es.oscars.api.soap.gen.v06.PathInfo;
import net.es.oscars.api.soap.gen.v06.ResCreateContent;
import net.es.oscars.api.soap.gen.v06.ResDetails;
import net.es.oscars.api.soap.gen.v06.ReservedConstraintType;
import net.es.oscars.api.soap.gen.v06.UserRequestConstraintType;
import net.es.oscars.client.OSCARSClientException;
import net.es.oscars.common.soap.gen.OSCARSFaultMessage;

/***********************************************************************************************************************
* This class provides helper methods needed for MultipathOSCARSClient method groupReservations() to work appropriately.
* This class exists solely to provide a higher layer of modularity and keep MultipathOSCARSClient.java clean.
* 
* @author Jeremy
***********************************************************************************************************************/
public class HelperGroup 
{
    private static final String mpLookupGRI = MultipathOSCARSClient.mpLookupGRI;	// File which acts as the MP-GRI lookup table

	private HelperMiscellaneous miscHelper = new HelperMiscellaneous();	// Provides access to Miscellaneous helper methods
	private HelperCreate createHelper = new HelperCreate();				// Provides access to CreateMPReservation helper methods
	
	/*********************************************************************************************************************************************************
	* Handles the replacement or addition of MP-GRIs in the lookup table(file). It is assumed that the parameter updatedGRI 
	* is ready to be inserted into the table (it may be an updated version of a preexisting entry).
	* 
	* This helper method works for both MP-Group ADD & SUB operations.
	* 
	* @param updatedGRI
	*********************************************************************************************************************************************************/
	private String updateGroupInLookupTable(String updatedGRI)
	{
		boolean isExistingGroup = false;
		String shortGRI = miscHelper.getShortMPGri(updatedGRI);
		String returnGRI = null;
		
		try
		{
			FileInputStream griStream = new FileInputStream(mpLookupGRI);
       		DataInputStream griIn = new DataInputStream(griStream);
       		BufferedReader griBr = new BufferedReader(new InputStreamReader(griIn));
       		
       		FileWriter griOutStream = new FileWriter("temp.txt");
       		BufferedWriter griOutp = new BufferedWriter(griOutStream);

       		String strGriLine;     					                   		
       		       		
	        while(true)
	        {
	        	strGriLine = griBr.readLine();
	            	
	            if(strGriLine == null)
	            	break;
	            
	            // If GRI is already in lookup table, replace the entire line with updatedGRI -- Write to temp file first//
 	            if(strGriLine.substring(0, strGriLine.indexOf("_=_")).equals(shortGRI))
	            {
 	            	if(updatedGRI.contains("_0_"))
 	            	{
 	            		System.out.println("MP group is now empty, deleting superfluous MP_GRI: " + shortGRI);
 	            		returnGRI = "EMPTY";
 	            	}
 	            	else
 	            	{
 	            		System.out.println("UPDATING GROUP: " + shortGRI);
 	            		griOutp.write(updatedGRI + "\n");
 	            		returnGRI = shortGRI;
 	            	}
 	            	
 	            	isExistingGroup = true;
	            }
	            else
	            {
	            	griOutp.write(strGriLine + "\n");
	            }
	        }
	            		
	        griBr.close();
	        griIn.close();
	        griStream.close();
	            
	        // Add new group to Lookup Table
	        if(!isExistingGroup)
	        {
	        	griOutp.write(updatedGRI + "\n");
	        	returnGRI = shortGRI;
	        }
	        
	        griOutp.close();
	       	griOutStream.close();
	       	
	       	//File originalLookup = new File(mpLookupGRI);
	       	//File newLookup = new File("temp.txt");

	       	//originalLookup.delete();
	     // Apparently, this isn't reliable -- May cause problems on some platforms
       		//if(newLookup.renameTo(originalLookup) == false)
       		//{
       		//	originalLookup = newLookup;
       		//}
	       	copyFile("temp.txt", mpLookupGRI);
        }
       	catch(Exception e)
       	{
       		System.err.println("Problem adding group MP-GRI to \'mp_gri_lookup.txt\'. Operation failed, group not added.");
       		e.printStackTrace();
       		System.exit(-1);
       	}
		
		return returnGRI;
	}
	
	/*********************************************************************************************************************************************************
	* Inspects a unicast GRI that is or is not part of a Multipath group, and "clones" it. That is, a link-disjoint counterpart to the original GRI will be possibly
	* created, with the same start-time, end-time, description, source URN, destination URN, etc.
	* *NOTE: ONLY unicast reservations which are either ACTIVE or RESERVED may be "cloned".
	* 
	* @param gri, original unicast gri to "clone".
	* @param mpClient, instance of the MultipathOSCARSClient that directly calls this method.
	* @param numPathsToAdd, number of link-disjoint "clones" to attempt to create (not including the original).
	* @return MP-GRI: 	- A new one if gri isn't part of a group already.
	* 					- The MP-GRI to which the gri and its new "clones" belong. 
	* 					- The original unicast GRI if it is not ACTIVE or RESERVED and cloning would be futile.
	*********************************************************************************************************************************************************/
	protected String duplicateUnicast(String gri, MultipathOSCARSClient mpClient, int numPathsToAdd)
	{
		mpClient.silentQuery = true;
		ArrayList<SubrequestTuple> queryResults = mpClient.queryMPReservation(gri);
		ArrayList<ResDetails> mpResDetails = queryResults.get(0).getAllDetails();
		ResDetails originalReservation;
		String originalStatus;
		mpClient.silentQuery = false;
		
		if(mpResDetails != null)
			 originalReservation = queryResults.get(0).getAllDetails().get(0);
		else if(queryResults.get(0).getDetails() != null)
			 originalReservation = queryResults.get(0).getDetails();
		else
		{
			System.err.println("GRI is not valid");
			return gri;
		}

		originalStatus = originalReservation.getStatus();
		
		// No point in duplicating a reservation if it is FAILED, FINISHED, CANCELLED, etc. //
		if(!(originalStatus.contains("ACTIVE") || originalStatus.contains("RESERVED")))
		{
			System.err.println("The original GRI status is: " + originalStatus + ". Cannot \"clone\" this GRI.");
			return gri;
		}
			
		UserRequestConstraintType unicastUserConst = originalReservation.getUserRequestConstraint();
		ReservedConstraintType unicastResConst = originalReservation.getReservedConstraint();
		List<OptionalConstraintType> unicastOptConst = originalReservation.getOptionalConstraint();
		
		ResCreateContent disjointReservation = null;
				
		// UserRequestConstraints of original unicast reservation //
		String description = originalReservation.getDescription();
		String srcUrn = unicastUserConst.getPathInfo().getLayer2Info().getSrcEndpoint();
		boolean isSrcTagged = unicastUserConst.getPathInfo().getLayer2Info().getSrcVtag().isTagged();
		String srcTag = unicastUserConst.getPathInfo().getLayer2Info().getSrcVtag().getValue();
		String destUrn = unicastUserConst.getPathInfo().getLayer2Info().getDestEndpoint();
		boolean isDestTagged = unicastUserConst.getPathInfo().getLayer2Info().getDestVtag().isTagged();
		String destTag = unicastUserConst.getPathInfo().getLayer2Info().getDestVtag().getValue();
		int bandwidth = unicastUserConst.getBandwidth(); 
		String pathSetupMode = unicastUserConst.getPathInfo().getPathSetupMode();
		long startTimestamp = unicastUserConst.getStartTime();
		long endTimestamp = unicastUserConst.getEndTime();
		
		disjointReservation = createHelper.constructResCreateContent(description, srcUrn, isSrcTagged, srcTag, destUrn, isDestTagged, destTag, bandwidth, pathSetupMode, startTimestamp, endTimestamp);
		
		// OptionalConstraints of original unicast reservation //
		for(OptionalConstraintType oneOptConst : unicastOptConst)
		{
			if(oneOptConst.getCategory().equals("BASIC_MULTIPATH_SERVICE"))
			{
				disjointReservation.getOptionalConstraint().add(oneOptConst);
			}
		}
		
		// ReservedConstraints of original unicast reservation //
		if(unicastResConst != null)
		{
			PathInfo unicastPathInfo = unicastResConst.getPathInfo();
	        CtrlPlanePathContent unicastPath = unicastPathInfo.getPath();
	        List<CtrlPlaneHopContent> unicastHops = unicastPath.getHop();
	        String previousPath = "";
	        
	        for(CtrlPlaneHopContent ctrlHop : unicastHops) 
	        {
	        	CtrlPlaneLinkContent link = ctrlHop.getLink();
	                     
	        	previousPath += link.getId() + ";";
        	}
	        
	        OptionalConstraintType previousOpt = new OptionalConstraintType();
   	        previousOpt.setCategory("BASIC_MULTIPATH_SERVICE");
   	        OptionalConstraintValue previousOptValue = new OptionalConstraintValue();
   	        previousOptValue.setStringValue(previousPath);
   	        previousOpt.setValue(previousOptValue);
   	        
   	        disjointReservation.getOptionalConstraint().add(previousOpt);
		}
		
		String returnGRI = createAdditionalReservations(disjointReservation, mpClient, gri, numPathsToAdd);
		System.out.println("[MPDuplication] of GRI " + gri + " complete.");
		
		return returnGRI;
	}
	
	
	/*********************************************************************************************************************************************************
	* The method performs the creation of the new link-disjoint "clones" of a unicast GRI, as described by duplicateUnicast().
	* This method mirrors the MultipathOSCARSClient method createMPReservation(), and creates as many link-disjoint paths as possible, up to numPathsToAdd.
	* 
	* @param newReservation, Contains all specs for the first link-disjoint "clone", including OptionalConstraints containing previous paths.
	* @param mpClient, Instance of the MultipathOSCARSClient that indirectly calls this method.
	* @param originalUnicastGRI, GRI of the unicast GRI which is supposed to be "cloned".
	* @param numPathsToAdd, maximum number of disjoint "clones" to create.
	* @return MP-GRI: 	- A new one if gri isn't part of a group already.
	* 					- The MP-GRI to which the gri and its new "clones" belong. 
	*********************************************************************************************************************************************************/
	private String createAdditionalReservations(ResCreateContent newReservation, MultipathOSCARSClient mpClient, String originalUnicastGRI, int numPathsToAdd)
	{
		int requestNum = 0;
		ArrayList<OptionalConstraintType> allPreviousPaths = new ArrayList<OptionalConstraintType>();
		String updatedGroup = "";
		
		for(OptionalConstraintType existingDisjointPath : newReservation.getOptionalConstraint())
		{
			if(newReservation.getOptionalConstraint() != null)
				allPreviousPaths.add(existingDisjointPath);
		}
		
		for(int req = 0; req < numPathsToAdd; req++)
		{
			// Do this unless it is the first disjoint-path added
			if(req > 0)
			{
				for(OptionalConstraintType previousPath: allPreviousPaths)
				{
					newReservation.getOptionalConstraint().add(previousPath);
				}
			}
			
			try
			{
				CreateReply oneCreateResponse = mpClient.oscarsClient.createReservation(newReservation);
				String newResGRI = oneCreateResponse.getGlobalReservationId();
				        		    	    
			    requestNum++;
			     
			    // Take the current path and convert it to a String of the hops, and then add the path String to the list of OptionalConstraints //
			    System.out.println("Polling Multipath subrequest (" + requestNum + " of " + numPathsToAdd + ") for status. Please wait a moment...");
			    String thisPath = mpClient.convertPathToString(oneCreateResponse.getGlobalReservationId());
			        
			    if(thisPath == null)
			    {
			       	System.out.println("Only " + requestNum + " of the desired " + numPathsToAdd + " disjoint paths could be reserved.");
			        break;
			    }
						    
			    OptionalConstraintType previousPath = new OptionalConstraintType();
			    previousPath.setCategory("BASIC_MULTIPATH_SERVICE");
			    OptionalConstraintValue optValue = new OptionalConstraintValue();
			    optValue.setStringValue(thisPath);
			    previousPath.setValue(optValue);
				allPreviousPaths.add(previousPath);
				
				String thisGroup = isUnicastPartOfGroup(originalUnicastGRI); // Find out if original unicast GRI is part of a group

				// Original unicast GRI is NOT part of an existing group, make a new group containing original GRI //
				if(thisGroup.equals(""))
				{
					Integer thisMPGRI = new Integer(-1);
					thisMPGRI = miscHelper.getMPGri(thisMPGRI);
					thisGroup += "MP-" + thisMPGRI.intValue() + "_=_MP-" + thisMPGRI.intValue() + ":_1_:" + originalUnicastGRI;
					
					System.out.println("Combining GRI " + originalUnicastGRI + " and " + newResGRI + " into new MP group: " + thisGroup.substring(0, thisGroup.indexOf("_=_")));
				}
				else
				{
					System.out.println("Adding new reservation to group " + thisGroup);
				}
				
				// Update group MP-GRI to include the new reservation //
				int numMembers = Integer.parseInt(thisGroup.substring(thisGroup.indexOf(":_")+2, thisGroup.indexOf("_:"))) + 1;
				updatedGroup = thisGroup.substring(0, thisGroup.indexOf(":_")+2) + numMembers + "_:" + thisGroup.substring(thisGroup.indexOf("_:")+2) + ":" + newResGRI;
				updateGroupInLookupTable(updatedGroup);						 // Add new request to that group 
			}
			catch(OSCARSFaultMessage ofm){ ofm.printStackTrace(); }
			catch(OSCARSClientException oce){ oce.printStackTrace(); }
		}
		
		return updatedGroup;
	}
	
	
	/*********************************************************************************************************************************************************
	* Determines whether or not the given unicast GRI belongs to any MP-GRI.
	* 
	* @param unicastGRI, The unicast GRI to test for group membership.
	* @return MP-GRI to which the unicastGRI belongs, or an empty String if no such MP-GRI exists.
	*********************************************************************************************************************************************************/
	private String isUnicastPartOfGroup(String unicastGRI)
	{
		String strGriLine;
   		String longFormatGRI = "";
   		
		try
		{
			FileInputStream griStream = new FileInputStream(mpLookupGRI);
	   		DataInputStream griIn = new DataInputStream(griStream);
	   		BufferedReader griBr = new BufferedReader(new InputStreamReader(griIn));
	   			   		
	        while(true)
	        {
	        	strGriLine = griBr.readLine();
	            	
	            if(strGriLine == null)
	            	break;
	            else if(strGriLine.contains(unicastGRI))
	            {
	            	longFormatGRI = strGriLine;
	            	break;
	            }
	        }
		}
		catch(Exception e){ e.printStackTrace(); }
		
        return longFormatGRI;
	}
	
	/*********************************************************************************************************************************************************
	* Handles MP-Group ADD operations. Supports addition of one or more unicast GRIs to an existing link-disjoint set of paths.
	* Group GRIs MUST begin with string "MP" as they are treated as Multipath reservations by query/cancel/modify/etc methods.
	* - IF possible, a new link-disjoint path will be added to the group from the same source URN to the same destination URN.
	* *NOTE: Most of the functionality of this method is handled by the duplicateUnicast() method, which is passed the last-created member of the Multipath 
	*   group. This is possible since the last member will have OptionalConstraints detailing all previously-created link-disjoint paths in the group.
	*
	* @param groupGri, group MP-GRI to add a new link-disjoint path(s) to. 
	* @param mpClient, instance of the MultipathOSCARSClient that directly calls this method.
	* @param numPathsToAdd, number of link-disjoint "clones" to attempt to add to this group.
	* @return MP-GRI: Updated group GRI of input parameter groupGRI.
	*********************************************************************************************************************************************************/
	protected String addToGroup(String groupGRI, MultipathOSCARSClient mpClient, int numPathsToAdd)
	{
		// Ensure that groupGRI is a valid MP-GRI //
		ArrayList<String> groupList = new ArrayList<String>();
		ArrayList<SubrequestTuple> allSubrequests;
		groupList.add(groupGRI);
		allSubrequests = mpClient.listGroupMembers(groupList);
				
		if(allSubrequests.get(0).getAllDetails().get(0).getStatus().equals("GROUP DOES NOT EXIST!"))
		{
			System.err.println("The specified MP-GRI does not exist. Cannot creat a new path.");
			return groupGRI;
		}
		
		// Query the MP-GRI as a group //
		mpClient.silentQuery = true;
		ArrayList<SubrequestTuple> queryResults = mpClient.queryMPReservation(groupGRI);
		ArrayList<ResDetails> mpResDetails = queryResults.get(0).getAllDetails();
		mpClient.silentQuery = false;
		
		// Identify the last subrequest GRI in the group -- This subrequest necessarily contains OptionalConstraints detailing paths of all other members in the group. //
		String lastSubrequestGRI = mpResDetails.get(mpResDetails.size()-1).getGlobalReservationId();
		System.out.println("Last subrequest of Group " + groupGRI + " is " + lastSubrequestGRI + ". Cloning " + lastSubrequestGRI + ".");
		
		// Duplicate the last member of the group in order to create a new link-disjoint member //
		String returnGRI = duplicateUnicast(lastSubrequestGRI, mpClient, numPathsToAdd);
		System.out.println("[MPGroupAddition] to GRI " + miscHelper.getShortMPGri(returnGRI) + " complete.");
		
		return returnGRI;
	}
	
			
	/*********************************************************************************************************************************************************
	* Handles MP-Group SUB operations. Supports subtraction/removal of one or more source GRIs from specified destination group GRI.  
	* Group GRIs MUST begin with string "MP" as they are treated as Multipath reservations by query/cancel/modify/etc methods.
	* - IF destination GRI exists and contains the source GRIs, THEN source GRIs will be removed from the group.
	* 
	* @param gris, List of GRIs to group. Index 0 is the destination Group-GRI, all others are source GRIs.
	*********************************************************************************************************************************************************/
	protected String subFromGroup(ArrayList<String> gris)
	{		
		String groupGRI = gris.get(0);
		String longGRI;
		String newNum;
		int numSubGris;

		String updatedGRI;
		String updatedGRIArr[];
		ArrayList<String> updatedGRIList = new ArrayList<String>(); 	
    	    	
    	longGRI = miscHelper.getLongMPGri(groupGRI);	// Inspects MP-GRI lookup table for this group GRI
    	
    	if(longGRI == null)
    	{
    		System.err.println("\nError: All Group GRIs must begin with string \"MP\"");
    		return null;
    	}
    	else if(longGRI.contains(":_0_:"))
    	{
    		System.err.println("\nError: No such Group GRI");
    		return null;
    	}
    	
    	updatedGRI = longGRI;
    	updatedGRIArr = updatedGRI.split(":");	// Convert the new GRI to an Array, with ":" as token breaking up each index
    	
    	for(String oneToken : updatedGRIArr)
		{
    		if(!gris.contains(oneToken))
    			updatedGRIList.add(oneToken);		// Convert the Array representation to an ArrayList representation
		}
    	
    	numSubGris = updatedGRIList.size() - 2;	// only count members of the group
    	newNum = "_" + numSubGris + "_";
    	updatedGRIList.set(1, newNum);
    	
		// Build the new GRI for this group
		for(int subGri = 1; subGri < gris.size(); subGri++)
		{
			updatedGRI += ":" + gris.get(subGri);			
		}
						   	
    	updatedGRI = "";
    	
    	// Rebuild group MP-GRI string without removed subrequests
    	for(String oneToken : updatedGRIList)
    	{
    		updatedGRI += oneToken + ":";
    	}
    	
    	updatedGRI = updatedGRI.substring(0, updatedGRI.lastIndexOf(":"));	// Truncate trailing ":"
    		    	
    	String returnGRI = updateGroupInLookupTable(updatedGRI);
    	
    	System.out.println("[MPGroupSubtraction] from GRI " + miscHelper.getShortMPGri(updatedGRI) + " complete.");
    	return returnGRI;
	}
	
	private void copyFile(String originalFileName, String copyFileName)
	{
		try
        {
			File copyFile = new File(copyFileName);
        	copyFile.delete();
                
			FileInputStream inFStream = new FileInputStream(originalFileName);
	   		DataInputStream inDStream = new DataInputStream(inFStream);
	   		BufferedReader inBR = new BufferedReader(new InputStreamReader(inDStream));
	   		
	   		FileWriter outFStream = new FileWriter(copyFileName);
	   		BufferedWriter outBR = new BufferedWriter(outFStream);
	
	   		String oneLine;     					                   		
	   		       		
	        while(true)
	        {
	        	if((oneLine = inBR.readLine()) == null)
	        		break;
	                                    
	        	outBR.write(oneLine + "\n");
	        }
	        
	        inBR.close();
	        inDStream.close();
	        inFStream.close();
	        
	        outBR.close();
	        outFStream.close();

	        File originalFile = new File(originalFileName);
	        originalFile.delete();
        }
		catch(Exception e){ e.printStackTrace(); }
	}

}
