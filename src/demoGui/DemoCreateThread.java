package demoGui;

import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;

/*****************************************************************************************************************************************
* This class represents a parallel thread to run alongside the main MultipathUI GUI, to perform a long-running task in the background
* to eliminate noticeable lag and freezing.
* 
* This class performs a createReservation operation on the selected GRI/MP-GRI in the background and then resets the selections in
* the MP-GRI/GRI lists so that the newly created request can be queried.
*   
* @author Jeremy
/*****************************************************************************************************************************************/
public class DemoCreateThread extends SwingWorker<Void, Integer>
{
	DemoUI callingMPGui;			// The actual GUI object
	DemoGuiController multipathUIController;	// The GUI's behavior controller
	String source;						// Source node for new reservation
	String destination;				// Destination node for new reservation
	String startTime;					// Start time (YYYY-MM-DD HH:mm)
	String endTime;						// End time (YYYY-MM-DD HH:mm)
	int bandwidth;						// Bandwidth for new reservation (Mbps)
	int mpNumDisjoint;					// Number of link-disjoint paths to establish between source and destination
	JTree griTree;	
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
	public DemoCreateThread(DemoUI theGui, String srcURN, String dstURN, String startT, String endT, int band, int numDisjointPaths)
	{
		callingMPGui = theGui;
		multipathUIController = callingMPGui.mpUIController;
		source = srcURN;
		destination = dstURN;
		startTime = startT;
		endTime = endT;
		bandwidth = band;
		mpNumDisjoint = numDisjointPaths;
		griTree = callingMPGui.griTree;
	}
	
	/**
	* Thread's runner method. When the calling object invokes execute(), this is the method that runs. 
	*/
	protected Void doInBackground() 
	{ 
		// Invoke the actual createReservation call and get the corresponding GRI back from OSCARS //
	    String oscarsGRI = multipathUIController.createNewReservation(source, destination, startTime, endTime, bandwidth, mpNumDisjoint);	
	    // Refresh the MP-GRI/GRI lists to include the new reservation //
	    int countReserved = 0;
	    while(countReserved < mpNumDisjoint){
	    	/*Added for updating paths before request is finished*/
	    	DefaultMutableTreeNode root = (DefaultMutableTreeNode)(griTree.getModel().getRoot());
        	root.add(new DefaultMutableTreeNode(oscarsGRI));
        	callingMPGui.selectARequest(oscarsGRI);
        	/**/
	    	ArrayList<String> queryResults = multipathUIController.queryReservations(oscarsGRI);
	    	for(int i = 0; i < queryResults.size(); i++){
	    		callingMPGui.selectARequest(oscarsGRI);
		    	if(queryResults.get(i).contains("RESERVED") || queryResults.get(i).contains("FAILED")){
		    		countReserved++;
		    	}
		    }
	    	if(countReserved < mpNumDisjoint){
	    		countReserved = 0;
				try
				{
					Thread.sleep(5000);
				}
				catch(Exception e){}
	    	}
	    }
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
		callingMPGui.handleGriTree();
		callingMPGui.selectARequest(gri);
		callingMPGui.outputConsole.setText("Request " + gri + " CREATED!");
		callingMPGui.cancelResButton.setEnabled(true);
		callingMPGui.createButton.setEnabled(true);
		callingMPGui.closeDemoButton.setEnabled(true);
		callingMPGui.modifyResButton.setEnabled(true);
	}
}
