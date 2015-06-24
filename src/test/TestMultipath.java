package test;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.PropertyConfigurator;

import net.es.oscars.api.soap.gen.v06.ResDetails;

import multipath.AnycastHandler;
import multipath.MultipathOSCARSClient;
import multipath.SubrequestTuple;

import config.Configuration;

/*********************************************************************************************************
* This class serves to give examples of how a a user-written program would interact with the 
* MultipathOSCARSClient interface and show what sorts of parameters are expected. All scenarios in this
* class pursue Multipath/Unicast reservations.
* - Comment out various blocks of code to test separate pieces.
* 
* @author Jeremy
*********************************************************************************************************/
@SuppressWarnings("unused")		// Suppresses unused library warnings when various code-portions are commented
public class TestMultipath 
{
	public static void main(String[] args) throws InterruptedException
	{     
		PropertyConfigurator.configure("lib/log4j.properties");	// Eliminate Logger warnings.
	try{
			
		String oscarsURL = Configuration.oscarsURL;
		long myTime = (long)(System.currentTimeMillis() / 1000L);		// Current time in seconds, represented as a long value
		
		System.out.println("Connecting to OSCARS");
		MultipathOSCARSClient multipathClient = new MultipathOSCARSClient(oscarsURL);
		System.out.println("==============\n");	
		
		/*
		// TEST createMPReservation() - Unicast //
        System.out.println("Creating new Reservation");
		String gri_uni = multipathClient.createMPReservation("Unicast from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 1);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		*/
		/*
		// TEST createMPReservation() - Multipath-2 Best-effort //
        System.out.println("Creating new Reservation");
		String gri_mp_2 = multipathClient.createMPReservation("Best-Effort Reservation from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		*/
		///*
		// TEST createMPReservation() - Multipath-3 Best-effort //
        /*System.out.println("Creating new Reservation");
        String gri_mp_3 = multipathClient.createMPReservation("Best-Effort Reservation from TestMultipath", "urn:ogf:network:domain=es.net:node=SUNN:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=CHIC:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 4);
		System.out.println("Creation done!");
		System.out.println("==============\n");*/
		
		// TEST createMPReservation() - Anycast //
        /*System.out.println("Creating new Reservation");
		String gri_any = multipathClient.createMPReservation("Anycast from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=anycast(DENV-4,SUNN-5,PNWG-4,ELPA-3):port=anycast:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 1);
		System.out.println("Creation done!");
		System.out.println("==============\n");*/
		
		// TEST createMPReservation() - Anypath //
        System.out.println("Creating new Reservation");
        AnycastHandler handler = new AnycastHandler(multipathClient);
        String gri_anypath = handler.handleAnycastRequest("Anypath from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=anycast(DENV-4,SUNN-5,PNWG-4,ELPA-3):port=anycast:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 4);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		// TEST queryMPReservation() //
		System.out.println("Querying Reservation");
		multipathClient.queryMPReservation(gri_anypath);
		System.out.println("Query finished!");
		System.out.println("==============\n");
		     
		
		// TEST cancelMPReservation() //
		System.out.println("Cancelling Reservation");
		multipathClient.cancelMPReservation(gri_anypath);
		System.out.println("Cancellation complete!");
		System.out.println("==============\n");
		//*/
		/*		
		// TEST modifyMPReservation() //
		System.out.println("Creating Preliminary Reservation");
		String gri_mod_mp_2 = multipathClient.createMPReservation("Best-Effort Reservation from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		System.out.println("Querying preliminary request");
		multipathClient.queryMPReservation(gri_mod_mp_2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Modifying preliminary request");
		multipathClient.modifyMPReservation(gri_mod_mp_2, "New Description", 25, myTime+3700, myTime+3820);
		System.out.println("Modification done!");
		System.out.println("==============\n");
		
		System.out.println("Querying modified request");
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri_mod_mp_2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		*/
		/*		
		// TEST setupMPPath() & teardown<CPath() //
		System.out.println("Creating new Reservation");
		String gri_xml_mp_2 = multipathClient.createMPReservation("Best-Effort Reservation from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "signal-xml", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		System.out.println("Querying preliminary request");		
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri_xml_mp_2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Setting up path for reservation"); 
		multipathClient.setupMPPath(gri_xml_mp_2);	
		System.out.println("Path Setup done!");
		System.out.println("==============\n");
		
		System.out.println("Querying reservation"); 		
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri_xml_mp_2);
		System.out.println("Query done!");
		System.out.println("==============\n");
		
		System.out.println("Tearing Down path for reservation"); 
		multipathClient.teardownMPPath(gri_xml_mp_2);
		System.out.println("Path Teardown done!");
		System.out.println("==============");
		
		System.out.println("Querying reservation"); 		
		Thread.sleep(10000);
		multipathClient.queryMPReservation(gri_xml_mp_2);	
		System.out.println("Query done!");
		System.out.println("==============\n");
		*/	
		/*
		// Test groupReservations() - ADD Unicast (original is NOT in a group) //
		System.out.println("Creating new Reservation");
		String gri_uni_grp1 = multipathClient.createMPReservation("Unicast from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 1);
		System.out.println("Creation done!");
		System.out.println("==============\n");
		
		Thread.sleep(10000);
		System.out.println("Disjoint-Cloning " + gri_uni_grp1);
		ArrayList<String> groupMembers = new ArrayList<String>();
		groupMembers.add(gri_uni_grp1);
		String newGroupGRI = multipathClient.groupReservations(groupMembers, true, 1);
		System.out.println("New group created with MP-GRI: " + newGroupGRI);
		System.out.println("==============\n");
		*/
		/*
		// Test groupReservations() - ADD Unicast (original is already in a group) //
		System.out.println("Creating new Multipath Reservation");
		String gri_mp_grp1 = multipathClient.createMPReservation("Mulitpath from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
			
		ArrayList<String> groupGRIs = new ArrayList<String>();
		groupGRIs.add(gri_mp_grp1);	
		ArrayList<SubrequestTuple> allGroups = multipathClient.listGroupMembers(groupGRIs);
        String oneMemberGRI = allGroups.get(0).getAllDetails().get(0).getGlobalReservationId();
				
		System.out.println("Adding disjoint path to MP-GRI containing: " + oneMemberGRI);
		ArrayList<String> groupMembers = new ArrayList<String>();
		groupMembers.add(oneMemberGRI);
		String updatedGroupGRI = multipathClient.groupReservations(groupMembers, true, 1);
		System.out.println("Update group with MP-GRI: " + updatedGroupGRI);
		System.out.println("==============\n");
		*/
		/*
		// Test groupReservations() - ADD to group //
		System.out.println("Creating new Multipath Reservation");
		String gri_mp_grp2 = multipathClient.createMPReservation("Mulitpath from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
							
		System.out.println("Adding disjoint path to  group: " + gri_mp_grp2);
		ArrayList<String> groupMembers = new ArrayList<String>();
		groupMembers.add(gri_mp_grp2);
		String groupGRI = multipathClient.groupReservations(groupMembers, true, 1);
		System.out.println("Updated group with MP-GRI: " + groupGRI);
		System.out.println("==============\n");
		*/
		/*
		// Test groupReservations() - REMOVE //
		System.out.println("Creating new Multipath Reservation");
		String gri_mp_grp3 = multipathClient.createMPReservation("Mulitpath from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 3);
		System.out.println("Creation done!");
		System.out.println("==============\n");
			
		ArrayList<String> groupGRIs2 = new ArrayList<String>();
		groupGRIs2.add(gri_mp_grp3);	
		ArrayList<SubrequestTuple> allGroups2 = multipathClient.listGroupMembers(groupGRIs2);
        String oneMemberGRI = allGroups2.get(0).getAllDetails().get(0).getGlobalReservationId();
        String twoMemberGRI = allGroups2.get(0).getAllDetails().get(1).getGlobalReservationId();
				
		System.out.println("Removing members " + oneMemberGRI + " and " + twoMemberGRI + " from MP-GRI " + gri_mp_grp3);
		ArrayList<String> groupMembers = new ArrayList<String>();
		groupMembers.add(gri_mp_grp3);
		groupMembers.add(oneMemberGRI);
		groupMembers.add(twoMemberGRI);
		String updatedGroupGRI = multipathClient.groupReservations(groupMembers, false, 1);
		System.out.println("Update group with MP-GRI: " + updatedGroupGRI);
		System.out.println("==============\n");
		*/
		/*
		// Test groupReservations() - REMOVE until EMPTY//
		System.out.println("Creating new Multipath Reservation");
		String gri_mp_grp4 = multipathClient.createMPReservation("Mulitpath from TestMultipath", "urn:ogf:network:domain=es.net:node=ALBU:port=port-1:link=link1", true, "any", "urn:ogf:network:domain=es.net:node=WASH:port=port-1:link=link1", true, "any", 25, "timer-automatic", myTime+3600, myTime+3840, 2);
		System.out.println("Creation done!");
		System.out.println("==============\n");
			
		ArrayList<String> groupGRIs3 = new ArrayList<String>();
		groupGRIs3.add(gri_mp_grp4);	
		ArrayList<SubrequestTuple> allGroups3 = multipathClient.listGroupMembers(groupGRIs3);
        String oneMemberGRI = allGroups3.get(0).getAllDetails().get(0).getGlobalReservationId();
        String twoMemberGRI = allGroups3.get(0).getAllDetails().get(1).getGlobalReservationId();
				
		System.out.println("Removing members " + oneMemberGRI + " and " + twoMemberGRI + " from MP-GRI " + gri_mp_grp4);
		ArrayList<String> groupMembers = new ArrayList<String>();
		groupMembers.add(gri_mp_grp4);
		groupMembers.add(oneMemberGRI);
		groupMembers.add(twoMemberGRI);
		String updatedGroupGRI = multipathClient.groupReservations(groupMembers, false, 1);
		System.out.println("Update group with MP-GRI: " + updatedGroupGRI);
		System.out.println("==============\n");
		*/
		/*
		// TEST listGroupMembers() //
		System.out.println("Listing all Members of Groups = MP-1, MP-2, MP-3");
		ArrayList<String> groupGRIs = new ArrayList<String>();
		groupGRIs.add("MP-1");
		groupGRIs.add("MP-2");
		groupGRIs.add("MP-3");
		ArrayList<SubrequestTuple> allGroups = multipathClient.listGroupMembers(groupGRIs);
        for(SubrequestTuple oneGroup : allGroups)
        {
        	System.out.println("Group GRI: " + oneGroup.getGroupGRI() + "\n=================");
        	ArrayList<ResDetails> allMemberDetails = oneGroup.getAllDetails();
        	for(ResDetails oneMemberDetails : allMemberDetails)
        	{
        		String gri = oneMemberDetails.getGlobalReservationId();
        		String status = oneMemberDetails.getStatus();
        		if(status.equals("GROUP DOES NOT EXIST!"))
        		{
        			System.out.println("- " + status);
        			continue;
        		}
        		System.out.println("- GRI: " + gri);
        		if(status.equals("MP-QUERIED"))
        			System.out.println("Status: MP-GROUP");
        		else
        			System.out.println("Status: " + status);
        	}
        	System.out.println("=================\n");
        }
        System.out.println("Listing complete!");
		System.out.println("================\n");
		*/
		/*
		System.out.println("Now Listing ALL Members of ALL Groups");
		ArrayList<String> groupGRIs = new ArrayList<String>();
		groupGRIs.add("ALL");
		ArrayList<SubrequestTuple> allGroups = multipathClient.listGroupMembers(groupGRIs);
        for(SubrequestTuple oneGroup : allGroups)
        {
        	System.out.println("Group GRI: " + oneGroup.getGroupGRI() + "\n=================");
        	ArrayList<ResDetails> allMemberDetails = oneGroup.getAllDetails();
        	for(ResDetails oneMemberDetails : allMemberDetails)
        	{
        		String gri = oneMemberDetails.getGlobalReservationId();
        		String status = oneMemberDetails.getStatus();
        		if(status.equals("GROUP DOES NOT EXIST!"))
        		{
        			System.out.println("- " + status);
        			continue;
        		}
        		System.out.println("- GRI: " + gri);
        		if(status.equals("MP-QUERIED"))
        			System.out.println("Status: MP-GROUP");
        		else
        			System.out.println("Status: " + status);
        	}
        	System.out.println("=================\n");
        }
        System.out.println("Listing complete!");
		System.out.println("================\n");
		*/
		/*
		// TEST listUnicastByStatus() //
		System.out.println("Listing all Unicast with status = RESERVED, CANCELLED, ACTIVE");
		ArrayList<String> status = new ArrayList<String>();
		status.add("RESERVED");
		status.add("CANCELLED");
		status.add("ACTIVE");
		List<ResDetails> listResults = multipathClient.listUnicastByStatus(status);
		for(ResDetails oneListItem : listResults)
		{
			System.out.println("\nGRI: " + oneListItem.getGlobalReservationId());
			System.out.println("Status: " + oneListItem.getStatus());
		}
		System.out.println("Listing complete");
		System.out.println("================\n");
		//
		System.out.println("Now listing ALL Unicast!");
		status = new ArrayList<String>();
		status.add("ALL");
		listResults = multipathClient.listUnicastByStatus(status);
		for(ResDetails oneListItem : listResults)
		{
			System.out.println("\nGRI: " + oneListItem.getGlobalReservationId());
			System.out.println("Status: " + oneListItem.getStatus());
		}
		System.out.println("\nListing complete");
		System.out.println("================\n");
	*/
	}
	catch(Exception e){ e.printStackTrace(); }
	
	}
}