package gui;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* This class populates the list of ALL Unicast GRIs in the background, which takes some time because it involves submitting more than one 
* listReservations() request to OSCARS because trying to list FAILED reservations alongside CANCELLED reservations results in some 
* reservations being omitted from the returned list.
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class UnicastListThread extends SwingWorker<Void, Integer>
{
	MultipathUI callingMPGui;	// The actual GUI object
	
	/**
	* Constructor
	* 
	* @param theGui, The actual GUI object
	**/
	public UnicastListThread(MultipathUI theGui)
	{
		callingMPGui = theGui;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	*/
	protected Void doInBackground() 
	{ 		
		GuiController multipathUIController = callingMPGui.mpUIController;	// The GUI's behavior controller
		JCheckBox showAllCheck = callingMPGui.showAllCheckBox;				// IF checked, display all unicast GRIs
		JList mpGriList = callingMPGui.mpGriList;					// List of all MP-GRIs
		JList uniGriList = callingMPGui.uniGriList;					// List of subrequest/Unicast GRIs
		JTextPane console = callingMPGui.outputConsole;						// GUI console for displaying messages/query output

		int lastMPGri = callingMPGui.lastMPGri;			// The last MP-GRI selected BEFORE the "Show All" checkbox was checked. 
					
		// Checked //
		if(showAllCheck.isSelected())
        {		        	
			// Track currently selected MP-GRI, and clear the MP-GRI list //
        	lastMPGri = mpGriList.getSelectedIndex();		
        	mpGriList.clearSelection();						

        	// Populate unicast list with all unicast GRIs //
        	uniGriList.setListData(multipathUIController.getAllUnicastGRIs());
        	
        	console.setText("");	// Clear console output
        }
		// Unchecked //
        else
        {
        	// Reset MP-GRI selection to what it was before "Show All" was checked //
        	if(lastMPGri != -1)
        	{
        		if(mpGriList.getSelectedIndex() == lastMPGri || mpGriList.getSelectedIndex() == -1)
        			mpGriList.setSelectedIndex(lastMPGri);
        	}
        	// There was no MP-GRI selected before "Show All" was checked //
        	else
        	{
        		uniGriList.setSelectedIndex(-1);
        		uniGriList.setListData(new Object[0]);
        		console.setText("");
        	}
        }
        
		// Update the global version of this variable for the next UnicastListThread object to access //
		callingMPGui.lastMPGri = lastMPGri; 
				
        return null;
	}	
}
