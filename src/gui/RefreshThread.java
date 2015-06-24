package gui;

import javax.swing.JList;
import javax.swing.SwingWorker;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* If the user has selected a GRI in the GUI, the GRI is queries and results are displayed in the console. However, it's possible that
* those query results are not up to date.  For example, when the user cancels a request, the output might have status "INCANCEL".
* This class allows for repeated querying to give the user the most up-to-date information about the GRI every 7 seconds.
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class RefreshThread extends SwingWorker<Void, Integer>
{
	MultipathUI callingMPGui;				// The actual GUI object
	
	/**
	* Constructor
	* 
	* @param theGui, The actual GUI object
	**/
	public RefreshThread(MultipathUI theGui)
	{
		callingMPGui = theGui;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	**/
	protected Void doInBackground() 
	{ 					
		boolean refreshMultipathQuery = false;								// Is the refreshed GRI Multipath or Unicast?
		JList mpGriList = callingMPGui.mpGriList;					// List of group/Multipath GRIs
		JList uniGriList = callingMPGui.uniGriList;					// List of subrequest/Unicast GRIs
		
		QueryThread refreshingQuery;			// Querying thread to run in parallel
		
		// Refresh query results every 7 seconds //
		while(true)
		{
			Object selectedMPItem = mpGriList.getSelectedValue();
			Object selectedUniItem = uniGriList.getSelectedValue();
			String selectedMPGRI = "";		// MP-GRI to query
			String selectedUniGRI = "";		// GRI to query
			
			// Don't perform query unless it is necessary //
			if(callingMPGui.outputConsole.getText().equals("Welcome to the Multipath Client User Interface!"))
				continue;
			if(callingMPGui.outputConsole.getText().equals("Creating reservation..."))
				continue;
			if(callingMPGui.outputConsole.getText().equals("Querying reservation..."))
				continue;
			if(callingMPGui.outputConsole.getText().equals("Cancelling reservation..."))
				continue;
			if(callingMPGui.outputConsole.getText().equals(""))
				continue;
			
			// Prepare to refresh MP-GRI query results //
			if(selectedMPItem != null)
			{
				System.out.println("HERE IN REFRESH!");
				selectedMPGRI = selectedMPItem.toString();
				refreshMultipathQuery = true;
			}

			// Prepare to refresh subrequest/Unicast GRI query results //
			if(selectedUniItem != null)
			{
				System.out.println("HERE IN REFRESH!");
				selectedUniGRI = selectedUniItem.toString();
				refreshMultipathQuery = false;
			}
			
			if(refreshMultipathQuery)
				refreshingQuery = new QueryThread(selectedMPGRI, callingMPGui); 	// Refresh group
			else
				refreshingQuery = new QueryThread(selectedUniGRI, callingMPGui);	// Refresh subrequest 
			
			// Perform the actual refreshing query //
			refreshingQuery.execute();
			
			
			System.out.println("REFRESHING ...");
			
			
			// Wait 7 seconds before refreshing -- Arbitrarily chosen time-interval//
			try
			{
				Thread.sleep(7000);
			}
			catch(Exception e){}
		}
	}	
}
