package demoGui;

import java.util.ArrayList;

import javax.swing.JList;
import javax.swing.SwingWorker;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* This class performs a cancelReservation operation on the selected GRI/MP-GRI in the background and then resets the selections in
* the MP-GRI/GRI lists so that the appropriate request can be re-queried (also in the background).
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class DemoCancelThread extends SwingWorker<Void, Integer>
{
	DemoUI callingMPGui;	// The actual GUI object
	String griToCancel;			// The GRI that will be cancelled
	boolean isMPGriSelected;	// TRUE if user has selected a group GRI on the GUI
	boolean isUniGriSelected;	// TRUE if user has selected a subrequest GRI on the GUI
	boolean cancellingMP;		// TRUE if we are canceling an group, FALSE if it is a subrequest
	int numCancelled;
	/**
	* Constructor
	* 
	* @param theGui, The calling GUI object
	* @param gri,	GRI to cancel
	* @param mpIsSelected, TRUE if user has selected a group GRI on the GUI
	* @param uniIsSelected, TRUE if user has selected a subrequest GRI on the GUI
	* @param groupCancellation, TRUE if we are canceling an group, FALSe if it is a subrequest
	**/
	public DemoCancelThread(DemoUI theGui, String gri, boolean mpIsSelected, boolean uniIsSelected, boolean groupCancellation, int numCancelled)
	{
		callingMPGui = theGui;
		
		griToCancel = gri;
		isMPGriSelected = mpIsSelected;
		isUniGriSelected = uniIsSelected;
		cancellingMP = groupCancellation;
		this.numCancelled = numCancelled;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	*/
	protected Void doInBackground() 
	{ 
		DemoGuiController multipathUIController = callingMPGui.mpUIController;	// GUI's behavior controller 
	    multipathUIController.cancelExistingReservation(griToCancel);		// Perform the actual reservation cancel operation
	    int countCancelled = 0;
	    while(countCancelled < numCancelled){
	    	ArrayList<String> queryResults = multipathUIController.queryReservations(griToCancel);
	    	for(int i = 0; i < queryResults.size(); i++){
		    	if(queryResults.get(i).contains("CANCELLED") || queryResults.get(i).contains("FAILED")){
		    		countCancelled++;
		    	}
		    }
	    	if(countCancelled < numCancelled){
	    		countCancelled = 0;
				try
				{
					Thread.sleep(5000);
				}
				catch(Exception e){}
	    	}
	    }		
	    refreshListsAfterCancel();		// Update the GRI Lists so that the INCANCEL status may be reflected
	    
	    return null; 
	}	
	
	/**
	* Have the GUI refresh the GRI lists so that the most recent query results can be displayed
	**/
	private void refreshListsAfterCancel()
	{
		callingMPGui.handleGriTree();
		callingMPGui.selectARequest(griToCancel);
		callingMPGui.outputConsole.setText("Request " + griToCancel + " CANCELLED!");
		callingMPGui.cancelResButton.setEnabled(true);
		callingMPGui.createButton.setEnabled(true);
		callingMPGui.closeDemoButton.setEnabled(true);
		callingMPGui.modifyResButton.setEnabled(true);
	}
}
