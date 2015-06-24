package gui;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.SwingWorker;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* This class performs a createReservation operation on the selected GRI/MP-GRI in the background and then resets the selections in
* the MP-GRI/GRI lists so that the newly created request can be queried.
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class CreateThread extends SwingWorker<Void, Integer>
{
	MultipathUI callingMPGui;			// The actual GUI object
	GuiController multipathUIController;	// The GUI's behavior controller
	String source;						// Source node for new reservation
	String destination;				// Destination node for new reservation
	String startTime;					// Start time (YYYY-MM-DD HH:mm)
	String endTime;						// End time (YYYY-MM-DD HH:mm)
	int bandwidth;						// Bandwidth for new reservation (Mbps)
	int mpNumDisjoint;					// Number of link-disjoint paths to establish between source and destination
		
	/**
	* Constructor
	*  
	* @param theGui, The actual GUI object
	* @param srcURN, Source node for new reservation
	* @param dstURN, Destination node for new reservation
	* @param startT, Start time (YYYY-MM-DD HH:mm)
	* @param endT, End time (YYYY-MM-DD HH:mm)
	* @param band, Bandwidth for new reservation (Mbps)
	* @param numDisjointPaths, Number of link-disjoint paths to establish between source and destination
	**/
	public CreateThread(MultipathUI theGui, String srcURN, String dstURN, String startT, String endT, int band, int numDisjointPaths)
	{
		callingMPGui = theGui;
		multipathUIController = callingMPGui.mpUIController;
		source = srcURN;
		destination = dstURN;
		startTime = startT;
		endTime = endT;
		bandwidth = band;
		mpNumDisjoint = numDisjointPaths;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	*/
	protected Void doInBackground() 
	{ 
		// Invoke the actual createReservation call and get the corresponding GRI back from OSCARS //
	    String oscarsGRI = multipathUIController.createNewReservation(source, destination, startTime, endTime, bandwidth, mpNumDisjoint);
	    		
	    // Refresh the MP-GRI/GRI lists to include the new reservation //
	    updateGriLists(oscarsGRI);
	    
	    return null; 
	}	
	
	/**
	* Update the GRI Lists to include the newly created reservation.
	* 
	* @param gri, GRI/MP-GRI to select once the lists have been refreshed
	**/
	private void updateGriLists(String gri)
	{
		JCheckBox showAllCheck = callingMPGui.showAllCheckBox;		// IF checked, display all unicast GRIs
		JList multipathGRIList = callingMPGui.mpGriList;		// List of all MP-GRIs
		JList unicastGRIList = callingMPGui.uniGriList;		// List of subrequest/Unicast GRIs
		
		// Multipath //
		if(gri.startsWith("MP"))		
		{
			multipathGRIList.setListData(multipathUIController.getMPGRIs());
			unicastGRIList.setListData(new Object[0]);
			unicastGRIList.setListData(multipathUIController.getGroupedGRIs(gri));
			multipathGRIList.setSelectedValue(gri, true);		// Make the new request MP-GRI the selected item
			unicastGRIList.setSelectedIndex(-1);				// De-select anything in the unicast GRI list
		}
		// Unicast //
		else								
		{
			// Add the new Unicast GRI to the lsit of ALL unicast GRIs and make it the selected item //
			if(!showAllCheck.isSelected())
			{
				showAllCheck.setSelected(true);		// Desired behavior already implemented in showAllCheckBox ActionListener :)
			}
			else
			{
				showAllCheck.setSelected(false);
				showAllCheck.setSelected(true);		// Desired behavior already implemented in showAllCheckBox ActionListener :P
			}
			
			while(!callingMPGui.listingThread.isDone());	// Wait until the Unicast GRI list has been populated before continuing
			
			unicastGRIList.setSelectedValue(gri, true);		// Make the new request GRI the selected item
			unicastGRIList.setSelectedValue(gri, true);		// Make the new request GRI the selected item
			callingMPGui.uniGriList.setSelectedValue(gri, true);
			
			System.out.println("SELECTED GRI = " + unicastGRIList.getSelectedValue());
		}
	}
}
