package demoGui;

import gui.CancelThread;
import gui.UnicastListThread;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeSelectionModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.PropertyConfigurator;

public class DemoUI
{

	private JFrame frame;
		
	protected ArrayList<String> mpGriList = new ArrayList<String>();				// List for displaying all existing MP group GRIs
	protected ArrayList<String> griList = new ArrayList<String>();					//List for displaying all existing non-MP GRIs
	protected JTree griTree = new JTree();
	
	protected UnicastListThread listingThread;	// Parallel thread used to populate list of Unicast requests. Made global so it can be accessed in CreateThread.
	
	protected DemoGuiController mpUIController;	// Controller for this GUI. List models are modified here.
											//   Also responsible for submitting requests to MultipathOSCARSClient.java
	protected JTextPane outputConsole;
	
	protected BackgroundPanel topology;
	protected ArrayList<Node> nodes = new ArrayList<Node>();
	protected ArrayList<String> nodeNames = new ArrayList<String>();
	
	protected boolean creatingRequest = false;
	protected boolean sourceSelection = false;
	protected boolean destinationSelection = false;
	protected boolean pathSelection = false;
	protected String currentSource = "";
	protected String currentDestination = "";
	protected int numDisjointPaths = 0;
	protected String srcUrn = "";
	protected String dstUrn = "";
	protected static DemoUI window;
	
	protected Node currentSrcNode = null;
	protected Node previousSrcNode = null;
	protected Node currentDstNode = null;
	protected Node previousDstNode = null;
	
	JButton createButton;
	JButton modifyResButton;
	JButton cancelResButton;
	JButton closeDemoButton;
	JButton confirm;
	JComboBox numPaths;
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) 
	{
		PropertyConfigurator.configure("lib/log4j.properties");	// Eliminate Logger warnings.
		
		try {
			UIManager.setLookAndFeel  ("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InstantiationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedLookAndFeelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					window = new DemoUI();
					window.frame.setExtendedState(JFrame.MAXIMIZED_BOTH); // Should maximize the window but not cover the taskbar
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public DemoUI() 
	{
		mpUIController = new DemoGuiController();
		mpUIController.getAllUnicastGRIs();		// Invoke a request to List all unicast GRIs
		mpUIController.refreshMPGriLists();		// Invoke a request to obtain all Multipath group GRIs
		
		initialize();
		
		// Constantly auto-refresh query output every 7 seconds in parallel (to eliminate lag/freezing) //
		DemoRefreshThread refresher = new DemoRefreshThread(this);  
		refresher.execute();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() 
	{
		frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(1500, 790);
		frame.setResizable(true);
		frame.setTitle("Multipath OSCARS Demo");
				
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(150, 480));
		frame.getContentPane().add(panel, BorderLayout.WEST);
		
		createButton = new JButton("Create Request");
		createButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			if(creatingRequest){
				cancelCreation();
			}
			else{
				modifyResButton.setEnabled(false);
				cancelResButton.setEnabled(false);
				closeDemoButton.setEnabled(false);
				createReservation();
			}
		}});
		createButton.setMinimumSize(new Dimension(110, 80));
		createButton.setMaximumSize(new Dimension(110, 80));
		
		modifyResButton = new JButton("Modify Request");
		modifyResButton.setEnabled(false);
		modifyResButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			createButton.setEnabled(false);
			modifyResButton.setEnabled(false);
			cancelResButton.setEnabled(false);
			closeDemoButton.setEnabled(false);
			modifyReservation();
		}});
		modifyResButton.setMinimumSize(new Dimension(110, 80));
		modifyResButton.setMaximumSize(new Dimension(110, 80));
		
		cancelResButton = new JButton("Cancel Request");
		cancelResButton.setEnabled(false);
		cancelResButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			createButton.setEnabled(false);
			modifyResButton.setEnabled(false);
			cancelResButton.setEnabled(false);
			closeDemoButton.setEnabled(false);
			cancelReservation();
		}});
		cancelResButton.setMinimumSize(new Dimension(110, 80));
		cancelResButton.setMaximumSize(new Dimension(110, 80));
				
		closeDemoButton = new JButton("Close Demo");
		closeDemoButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			createButton.setEnabled(false);
			modifyResButton.setEnabled(false);
			cancelResButton.setEnabled(false);
			closeDemoButton.setEnabled(false);
			System.exit(0);
		}});
		closeDemoButton.setMinimumSize(new Dimension(110, 40));
		closeDemoButton.setMaximumSize(new Dimension(110, 40));
		
		
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(10,15,10,10));
		panel.add(Box.createRigidArea(new Dimension(0,50)));
		panel.add(createButton);
		panel.add(Box.createRigidArea(new Dimension(0,75)));
		panel.add(modifyResButton);
		panel.add(Box.createRigidArea(new Dimension(0,75)));
		panel.add(cancelResButton);
		panel.add(Box.createRigidArea(new Dimension(0,75)));
		panel.add(closeDemoButton);
		
		JSplitPane splitPane = new JSplitPane();
		frame.getContentPane().add(splitPane, BorderLayout.CENTER);
		splitPane.setEnabled( false );
				
		topology = new BackgroundPanel();
		splitPane.setRightComponent(topology);
		topology.setLayout(null);
		placeImages(topology);
		drawLinks(topology);
		
		handleGriTree();
		
		JScrollPane scrollPane = new JScrollPane(griTree);
		scrollPane.setMinimumSize(new Dimension(150, 500));
		splitPane.setLeftComponent(scrollPane);
		
		JPanel panel2 = new JPanel();
		frame.getContentPane().add(panel2, BorderLayout.SOUTH);
		panel2.setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		
		outputConsole = new JTextPane();
		outputConsole.setText("Welcome to the Multipath Client User Interface!");
		outputConsole.setEditable(false);
		outputConsole.setPreferredSize(new Dimension(1210, 100));
		outputConsole.setMaximumSize(new Dimension(1210, 100));
		JScrollPane outputScrollPane = new JScrollPane(outputConsole);
		
		panel2.add(outputScrollPane);
	}
	
	protected void cancelCreation() {
		creatingRequest = false;
		createButton.setText("Create Request");
		createButton.setBorder(null);
		currentSource = "";
		currentDestination = "";
		numDisjointPaths = 0;
		sourceSelection = false;
		destinationSelection = false;
		pathSelection = false;
		topology.resetNodes();
		topology.remove(numPaths);
		topology.remove(confirm);
		topology.validate();
		topology.repaint();
		outputConsole.setText("Request Creation Process Cancelled");
	}

	/** MODIFY RESERVATION **/
	protected void modifyReservation() 
	{
		Object griSelected = griTree.getLastSelectedPathComponent();
	
		if(griSelected == null)
		{
			return;
		}
		
		createButton.setEnabled(false);
		closeDemoButton.setEnabled(false);
		cancelResButton.setEnabled(false);
		
		String griSelectedString = griSelected.toString();
		
		if(!outputConsole.getText().contains("ACTIVE") && !outputConsole.getText().contains("RESERVED"))
		{
				JOptionPane.showMessageDialog(frame,"Inactive reservations cannot be modified!");
				handleGriTree();
				selectARequest(griSelectedString);
				cancelResButton.setEnabled(true);
				modifyResButton.setEnabled(true);
				createButton.setEnabled(true);
				closeDemoButton.setEnabled(true);
				return;
		}
		
		Object options[];
		// Pop-up dialog //
		if(griSelectedString.contains("MP-"))
			options = new Object[]{"Add New Path to Group", "Never Mind"};
		else
			options = new Object[]{"Add New Path to Group", "Remove Path from Group"};
		
		int choice = JOptionPane.showOptionDialog(frame, "How would you like to modify \'" + griSelectedString + "\'?", "Modify Reservation?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		
		if(choice == 0)
		{
			outputConsole.setText("Attempting to create new reservation...");
			String groupGRI = mpUIController.addToGroup(griSelectedString);
			
			handleGriTree();
			
			if(groupGRI.equals("IMPOSSIBLE"))
			{
				JOptionPane.showMessageDialog(frame,"Current network state does not support adding an additional disjoint reservation to " + griSelectedString + "!");
				groupGRI = griSelectedString;
				selectARequest(griSelectedString);
			}
			else if(!griSelectedString.contains("MP-"))
			{
				outputConsole.setText("Reservation \'" + griSelectedString + "\' and new path added to new group \'" + groupGRI + "\'.");
				selectARequest(groupGRI);
			}
			else
			{
				outputConsole.setText("New path added to group \'" + groupGRI + "\'.");
				selectARequest(groupGRI);
			}
		}
		else if((choice == 1) && (griSelectedString.contains("MP-")))
		{
			handleGriTree();
			selectARequest(griSelectedString);
			cancelResButton.setEnabled(true);
			modifyResButton.setEnabled(true);
			createButton.setEnabled(true);
			closeDemoButton.setEnabled(true);
			return;
		}
		else if(choice == 1)
		{
			BufferedReader br = null;
			boolean isPartOfMPGroup = false;
			String whichMPGroup = null;
			String line = null;
			
			try 
			{
				br = new BufferedReader(new FileReader("mp_gri_lookup.txt"));
				while((line = br.readLine()) != null)
				{
					if(line.contains(griSelectedString))
					{
						isPartOfMPGroup = true;
						String[] groupDecomp = line.split("_=_"); 
						whichMPGroup = groupDecomp[0];
						System.out.println(whichMPGroup);
						break;
					}
				}
				
		        br.close();
			} 
			catch (FileNotFoundException e) { e.printStackTrace(); } 
			catch (IOException e) {	e.printStackTrace(); }
			
			if(!isPartOfMPGroup)
			{
				JOptionPane.showMessageDialog(frame, griSelectedString + " does not belong to a Multipath group, and cannot be removed!");
				
				handleGriTree();
				selectARequest(griSelectedString);
				cancelResButton.setEnabled(true);
				modifyResButton.setEnabled(true);
				createButton.setEnabled(true);
				closeDemoButton.setEnabled(true);
				return;
			}
			else
			{
				Object options2[] = {"Remove", "Cancel"};	
			
				int choice2 = JOptionPane.showOptionDialog(frame, "WARNING! Removing \'" + griSelectedString + "\' from group \'" + whichMPGroup + "\' will not cancel the reservation in OSCARS. Would you like to proceed with removal?", "WARNING!", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options2, options2[1]);
			
				if(choice2 == 1)
				{
					handleGriTree();
					selectARequest(whichMPGroup);
					cancelResButton.setEnabled(true);
					modifyResButton.setEnabled(true);
					createButton.setEnabled(true);
					closeDemoButton.setEnabled(true);
					return;	
				}
				else
				{
					outputConsole.setText("Attempting to remove reservation from group...");
					
					String groupGRI = mpUIController.subFromGroup(whichMPGroup, griSelectedString);
			
					outputConsole.setText("Removal operation complete.");
		
					handleGriTree();
					
					TreePath tp = null;
				    DefaultMutableTreeNode root = (DefaultMutableTreeNode)griTree.getModel().getRoot();
				    Enumeration<DefaultMutableTreeNode> e = root.depthFirstEnumeration();
				    while (e.hasMoreElements()) 
				    {
				        DefaultMutableTreeNode node = e.nextElement();
				        if (node.toString().equalsIgnoreCase(groupGRI)) 
				        {
				           tp = new TreePath(node.getPath());
				        }
				    }
				    if(tp!= null)
				    {
				    	selectARequest(groupGRI);				    					    	
				    }
				    else
				    {
				    	topology.resetLinks();
				    	topology.resetNodes();
				    }
					
				}
			}
		}
		
		handleGriTree();
		cancelResButton.setEnabled(true);
		modifyResButton.setEnabled(true);
		createButton.setEnabled(true);
		closeDemoButton.setEnabled(true);
	}

	/*****************************************************************************
	* This is what happens when you click the "Cancel Reservation" button.
	* - Submits the request to OSCARS.
	*****************************************************************************/
	protected void cancelReservation() {
		Object griSelected = griTree.getLastSelectedPathComponent();
		if(griSelected == null){
			return;
		}
		createButton.setEnabled(false);
		closeDemoButton.setEnabled(false);
		String griSelectedString = griSelected.toString();
		
		if(!outputConsole.getText().contains("ACTIVE") && !outputConsole.getText().contains("RESERVED"))
		{
				JOptionPane.showMessageDialog(frame,"Inactive reservations cannot be cancelled!");
				handleGriTree();
				selectARequest(griSelectedString);
				cancelResButton.setEnabled(true);
				modifyResButton.setEnabled(true);
				createButton.setEnabled(true);
				closeDemoButton.setEnabled(true);
				return;
		}
		
		DemoCancelThread cancellationThread;	// Parallel thread in which to perform the cancellation to prevent lag/freezing
				
		// Cancel Unicast request 
		if(!griSelectedString.contains("MP"))	
		{
			// Confirm cancellation with pop-up warning dialog //
			Object options[] = {"Cancel Request", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frame,"Are you sure you want to cancel the unicast request \'" + griSelectedString + "\'?", "Confirm Cancel?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0)		// User confirmed cancellation
			{
				outputConsole.setText("Cancelling reservation...");
				//mpUIController.cancelExistingReservation(uniGriSelected);
				cancellationThread = new DemoCancelThread(this, griSelectedString, false, true, false, 1);
				cancellationThread.execute();
			}
		}
		// Cancel Group Request
		else if(griSelectedString.contains("MP"))	
		{
			
			// Confirm cancellation with pop-up warning dialog //
			Object options[] = {"Cancel Group", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frame,"Are you sure you want to cancel Group \'" + griSelectedString + "\'?", "Confirm Cancel?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0)		// User confirmed cancellation
			{
				int numRequests = ((DefaultMutableTreeNode)griSelected).getChildCount();
				outputConsole.setText("Cancelling reservation...");
				cancellationThread = new DemoCancelThread(this, griSelectedString, true, false, true, numRequests);
				cancellationThread.execute();
			}
		}
		
		handleGriTree();
		selectARequest(griSelectedString);
		cancelResButton.setEnabled(false);
		modifyResButton.setEnabled(false);
	}

	/*****************************************************************************
	* This is what happens when you click the "Create Reservation" button.
	* - Submits the request to OSCARS.
	*****************************************************************************/
	protected void createReservation()
	{
		currentSource = "";
		currentDestination = "";

		outputConsole.setForeground(Color.BLACK);
		outputConsole.setText("");
		outputConsole.setText("Creating reservation...");
		
		topology.resetLinks();
		topology.resetNodes();
		
		griTree.setSelectionPath(null);
		creatingRequest = true;
		createButton.setText("Cancel Creation");
		Border roundedBorder = new LineBorder(Color.RED, 4, true);
		createButton.setBorder(roundedBorder);
		// First get the appropriate URNs for the selected src/destination //
		sourceSelection = true;
		outputConsole.setText("Select a Source Node for this Request");
		Object[] numPathsArray = {"1 Path", "2 Paths", "3 Paths", "4 Paths", "5 Paths"};
		numPaths = new JComboBox(numPathsArray);
		confirm = new JButton("Confirm Selection");
		numPaths.setSelectedIndex(0);
		numPaths.setEditable(false);
		topology.add(numPaths);
		numPaths.setBounds(300, 530, 75, 40);
		numPaths.setVisible(false);
		numPaths.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent arg0)
			{
				String selectedValue = ((JComboBox)arg0.getSource()).getSelectedItem().toString();
				selectedValue = selectedValue.substring(0, selectedValue.indexOf("Path")-1);
				System.out.println("Value = [" + selectedValue + "]");
				numDisjointPaths = Integer.parseInt(selectedValue);
				outputConsole.setText(numDisjointPaths + " Paths Selected");
				pathSelection = true;
			}
		});
		confirm.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			String startTime = "2016-01-01 00:00";
			String endTime = "2016-01-01 01:00";
			int bandwidth = 10;
			DemoCreateThread creationThread;
			if(!confirmSelection())
				return;
			else
			{
				if(sourceSelection)
				{
					sourceSelection = false;
					destinationSelection = true;
					outputConsole.setText("Select a Destination node for this Request");
					return;
				}
				if(destinationSelection)
				{
					if(currentDestination.equals(currentSource))
					{
						outputConsole.setText("Cannot have the same node as both Source and Destination for one Request. Reselect a new Destination");
						return;
					}
					destinationSelection = false;
					//Source and Destination Chosen, now create URNs
					srcUrn = currentSource + " : " + "port-1" + " : " + "link1";
					dstUrn = currentDestination + " : " + "port-1" + " : " + "link1";
					//Select Number of Paths
					outputConsole.setText("Select the number of Paths for this Request");
					numPaths.setVisible(true);
					numPaths.setSelectedIndex(0);
					return;
				}
				
				if(pathSelection)
				{
					pathSelection = false;
					outputConsole.setText("Creating Request...");
					topology.remove(numPaths);
					topology.remove(confirm);
					topology.validate();
					topology.repaint();
					creationThread = new DemoCreateThread(window, srcUrn, dstUrn, startTime, endTime, bandwidth, numDisjointPaths);
					creationThread.execute();
					createButton.setText("Create Request");
					createButton.setEnabled(false);
					creatingRequest = false;
					return;
				}
			}
		}});
	}
	
	protected boolean confirmSelection() 
	{
		if(sourceSelection){
			if(currentSource != ""){
				return true;
			}
			else{
				return false;
			}
		}
		if(destinationSelection){
			if(currentDestination != ""){
				return true;
			}
			else{
				return false;
			}
		}
		if(pathSelection){
			return true;
		}
		return false;
	}

	/** Node click action handler **/
	protected void handleNodeSelection(Node node)
	{
		topology.add(confirm);
		confirm.setBounds(675, 530, 130, 40);
		
		if(sourceSelection)
		{
			if(node.isClickedAsSource())	// Don't do any source management if this node is already selected as source
				return;
			
			currentSource = node.getName();
			outputConsole.setText(node.getName() + " selected as Source");
						
			this.placeSourceNode(topology, node, previousSrcNode);
			
			previousSrcNode = node;
			
			return;
		}
		
		if(destinationSelection)
		{
			if(node.isClickedAsDestination())	// Don't do any destination management if this node is already selected as destination
				return;
			if(node.isClickedAsSource())		// Don't do any destination management if this node is already selected as source
				return;
			
			currentDestination = node.getName();
			outputConsole.setText(node.getName() + " selected as Destination");
						
			this.placeDestinationNode(topology, node, previousDstNode);
			
			previousDstNode = node;
			
			return;
		}
	}

	protected void handleGriTree() 
	{
		// TODO Auto-generated method stub
		mpUIController.refreshMPGriLists();
		mpUIController.getAllUnicastGRIs();
		mpGriList = mpUIController.getMPGRIsAsStrings();
		griList = mpUIController.getUngroupedGRIs(mpGriList);
		
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Requests");
		for(int i = 0; i < mpGriList.size(); i++){
			DefaultMutableTreeNode mpNode = new DefaultMutableTreeNode(mpGriList.get(i));
			Object[] uniGriObjects = mpUIController.getGroupedGRIs(mpGriList.get(i));
			for(int j = 0; j < uniGriObjects.length; j++){
				mpNode.add(new DefaultMutableTreeNode(uniGriObjects[j].toString()));
			}
			root.add(mpNode);
		}
		for(int i = 0; i < griList.size(); i++){
			DefaultMutableTreeNode ungroupedNode = new DefaultMutableTreeNode(griList.get(i));
			root.add(ungroupedNode);
		}
		
		griTree.setModel(new DefaultTreeModel(root));
		griTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		((DefaultTreeModel)griTree.getModel()).reload();
				
		griTree.addTreeSelectionListener(new TreeSelectionListener() 
		{
			@Override
			public void valueChanged(TreeSelectionEvent e) 
			{
				//Returns the last path element of the selection.
				//This method is useful only when the selection model allows a single selection.
								
				JTree tree = (JTree)e.getSource();
			    DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
			    			    
			    if (node == null)
			    {
			    	cancelResButton.setEnabled(false);
			    	modifyResButton.setEnabled(false);
			    	return;
			    }
			    			    
			    //if(((String)node.getUserObject()).equals("Requests") || creatingRequest){
			    //	tree.setSelectionPath(null);
			    //	cancelResButton.setEnabled(false);
			    //	modifyResButton.setEnabled(false);
			    //	return;
			    //}
			    
			    Object nodeInfo = node.getUserObject();
			    
			    ArrayList<String> queryOutput = queryGRI((String)nodeInfo);
			    			    			      
			    ArrayList<String> requestStrings = new ArrayList<String>();
			    ArrayList<String> requestStatuses = new ArrayList<String>();
			    			    			    
			    for(int i = 0; i < queryOutput.size(); i++)
			    {
			    	if (queryOutput.get(i).contains("Hops")) 
			    	{
			    		requestStrings.add(queryOutput.get(i));
			    	
			    		if(queryOutput.get(i).contains("CANCELLED"))
			    		{
			    			requestStatuses.add("CANCELLED");
			    		}
			    		else if(queryOutput.get(i).contains("RESERVED"))
			    		{
			    			requestStatuses.add("RESERVED");
			    		}
			    		else if(queryOutput.get(i).contains("ACTIVE"))
			    		{
			    			requestStatuses.add("ACTIVE");
			    		}
			    		else if(queryOutput.get(i).contains("FAILED"))
			    		{
			    			requestStatuses.add("FAILED");
			    		}
			    		else
			    		{
			    			requestStatuses.add("OTHER");
			    		}
			    	}
			    	else if(queryOutput.get(i).contains("FAILED"))
			    	{
			    		topology.resetLinks();
			    		requestStrings.add("FAILED");
		    			requestStatuses.add("FAILED");
		    		}
			    }
			    
			    ArrayList<ArrayList<String>> pathStrings = new ArrayList<ArrayList<String>>();
			    for(int i = 0; i < requestStrings.size(); i++)
			    {
			    	if(requestStrings.equals("FAILED"))
			    	{
			    		ArrayList<String> linkStrings = new ArrayList<String>();
			    		linkStrings.add("FAILED");
			    		pathStrings.add(linkStrings);
			    	}
			    	
			    	String[] splitRequest = requestStrings.get(i).split("Hops in Path:");
				    if(splitRequest.length > 1)
				    {
				       String linksInPath = splitRequest[1];
				       linksInPath = linksInPath.trim();
				       String[] links = linksInPath.split(" --> ");
				       ArrayList<String> linkStrings = new ArrayList<String>(Arrays.asList(links));
				       pathStrings.add(linkStrings);
				    }
			    }
								    
			    for(int xx = 0; xx < pathStrings.size(); xx++)
			    {
			    	if(pathStrings.get(xx).equals("FAILED")){
			    		continue;
			    	}
			    	String[] partsOfFirstHop = pathStrings.get(xx).get(0).split("_");
		    		String[] partsOfLastHop = pathStrings.get(xx).get(pathStrings.get(xx).size()-1).split("_");
		    		String reservationSource = partsOfFirstHop[0];
		    		String reservationDestination = partsOfLastHop[1];
		    		
		    		ArrayList<Node> allTopoNodes = topology.getNodes();
		    		
		    		Node reservationSrcNode = null;
		    		Node reservationDstNode = null;
		    		
		    		for(Node oneNode : allTopoNodes)
		    		{
		    			if(oneNode.getName().equals(reservationSource))
		    				reservationSrcNode = oneNode;
		    			if(oneNode.getName().equals(reservationDestination))
		    				reservationDstNode = oneNode;
		    		}
		    		
		    		currentSrcNode = reservationSrcNode;
		    		currentDstNode = reservationDstNode;
		    		
		    		placeSourceNode(topology,reservationSrcNode,previousSrcNode);
		    		placeDestinationNode(topology,reservationDstNode,previousDstNode);
		    		
		    		previousSrcNode = reservationSrcNode;
		    		previousDstNode = reservationDstNode;
		    	}

			    			    
			    if(pathStrings.size() > 0)
			    {
			    	if(pathStrings.size() > 1)	//MP
			    	{ 
			    		colorLinksMP(pathStrings, requestStatuses);
			    	}
			    	else	//One path
			    	{ 
			    		colorLinks(pathStrings.get(0), requestStatuses);
			    	}
			    }
			    			    
			    cancelResButton.setEnabled(true);
			    modifyResButton.setEnabled(true);
			    closeDemoButton.setEnabled(true);
			}
		});
	}
	
	/*****************************************************************************
	* This is what happens when user or another method selects an item in the 
	* Multipath/Subrequest/Unicast GRI lists.
	* - Submit the selected item for immediate Querying in OSCARS.
	* - Print query results to output console.
	* 
	* @param isGroup, TRUE if an item in the MP-GRI list is selected,
	* 				  FALSE if an item in the Subrequest GRI list is selected.
	*****************************************************************************/
	protected ArrayList<String> queryGRI(String gri)
	{
		DemoQueryThread queryThread;	// Query the GRI in a parallel thread to eliminate lag/freezing
		
		outputConsole.setForeground(Color.BLACK);
		outputConsole.setText("");
		outputConsole.setText("Querying reservation...");
		
		queryThread = new DemoQueryThread(gri, this);
	
		queryThread.execute();	// Perform the actual query
		
		try 
		{
			queryThread.get();
		} 
		catch (InterruptedException e) { e.printStackTrace(); }
		catch (ExecutionException e) { e.printStackTrace(); }
		
		return queryThread.getAllQueryOutput();
	}
	

	void colorLinks(ArrayList<String> links, ArrayList<String> requestStatuses){
		ArrayList<Link> allLinks = topology.getLinks();
		for(int i = 0; i < allLinks.size(); i++){
			topology.changeColor(Color.black,  allLinks.get(i).getID1());
		}
		if(requestStatuses.get(0).equals("FAILED") && links.get(0).equals("FAILED")){
			topology.resetLinks();
			return;
		}
		for(int i = 0; i < allLinks.size(); i++){
			if(links.contains(allLinks.get(i).getID1()) || links.contains(allLinks.get(i).getID2())){
				if(requestStatuses.get(0).equals("RESERVED") || requestStatuses.get(0).equals("ACTIVE") || requestStatuses.get(0).equals("OTHER"))
					topology.changeColor(Color.red, allLinks.get(i).getID1());
				else if(requestStatuses.get(0).equals("CANCELLED") || requestStatuses.get(0).equals("FAILED"))
					topology.changeColor(Color.gray, allLinks.get(i).getID1());	
			}
		}
		return;
	}
	
	private void colorLinksMP(ArrayList<ArrayList<String>> pathStrings, ArrayList<String> requestStatuses) {
		ArrayList<Link> allLinks = topology.getLinks();
		for(int i = 0; i < allLinks.size(); i++){
			topology.changeColor(Color.black,  allLinks.get(i).getID1());
		}
		for(int i = 0; i < pathStrings.size(); i++){
			ArrayList<String> links = pathStrings.get(i);
			for(int j = 0; j < allLinks.size(); j++){
				if(links.contains(allLinks.get(j).getID1()) || links.contains(allLinks.get(j).getID2())){
					if(requestStatuses.get(i).equals("RESERVED") || requestStatuses.get(i).equals("ACTIVE") || requestStatuses.get(i).equals("OTHER")){
						switch(i){
							case 0:
								topology.changeColor(Color.red, allLinks.get(j).getID1());
								break;
							case 1:
								topology.changeColor(Color.blue, allLinks.get(j).getID1());
								break;
							case 2:
								topology.changeColor(Color.green, allLinks.get(j).getID1());
								break;
							case 3:
								topology.changeColor(Color.MAGENTA, allLinks.get(j).getID1());
								break;
						}						
					}
					else if(requestStatuses.get(i).equals("CANCELLED") || requestStatuses.get(i).equals("FAILED")){
						topology.changeColor(Color.gray, allLinks.get(j).getID1());
					}
					else{ //FAILED
						
					}
				}
			}
		}
	
	}
	
	void placeImages(BackgroundPanel panel){
		Object[] topologyNodes = mpUIController.getTopologyNodes();
		BufferedImage[] nodeImages;

		for(int i = 0; i < topologyNodes.length; i++)
		{
			String nodeName = topologyNodes[i].toString().split(" ")[0];
			if(!nodeNames.contains(nodeName)){
				nodeNames.add(nodeName);
			}
		}
		nodeImages = new BufferedImage[nodeNames.size()];
		for(int i = 0; i < nodeNames.size(); i++)
		{
			nodeImages[i] = null;
			try {
				nodeImages[i] = ImageIO.read(new File("images/UnselectedNode.fw.png"));
				nodeImages[i] = resize(nodeImages[i],60,60);
			} catch (IOException e) {
				System.out.println("Image File not found");
			}
		}
		
		for(int i =0; i < nodeNames.size(); i++){
			Node n = new Node(nodeNames.get(i), assignCoords(i), nodeImages[i]);
			n.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					Node node = (Node)e.getSource();
					handleNodeSelection(node);
					return;
				}
			});
			nodes.add(n);
			placeNodes(n, panel);
		}
		return;
	}
	
	/** Draw source node -- JJ Added **/
	void placeSourceNode(BackgroundPanel topoMap, Node srcNode, Node previousSelection)
	{			
		if(previousSelection != null)
		{
			if(previousSelection.equals(srcNode))
				return;
								
			try
			{
				BufferedImage deselectedNodeImage;
				deselectedNodeImage = ImageIO.read(new File("images/UnselectedNode.fw.png"));
				deselectedNodeImage = resize(deselectedNodeImage, 60, 60);
				previousSelection.setImageIcon(deselectedNodeImage);
			}
			catch (IOException e) { System.out.println("Image File not found"); }
						
			previousSelection.unclickAsSource();			
		}
		
		try
		{
			BufferedImage srcNodeImage;
			srcNodeImage = ImageIO.read(new File("images/SourceNode.fw.png"));
			srcNodeImage = resize(srcNodeImage, 60, 60);
			srcNode.setImageIcon(srcNodeImage);
		} 
		catch (IOException e) { System.out.println("Image File not found"); }
								
		srcNode.clickAsSource();
		
		topoMap.repaint();
		
		return;
	}

	/** Draw destination node -- JJ Added **/
	void placeDestinationNode(BackgroundPanel topoMap, Node dstNode, Node previousSelection)
	{			
		if(previousSelection != null)
		{
			if(previousSelection.equals(dstNode))
				return;
								
			try
			{
				BufferedImage deselectedNodeImage;
				deselectedNodeImage = ImageIO.read(new File("images/UnselectedNode.fw.png"));
				deselectedNodeImage = resize(deselectedNodeImage, 60, 60);
				previousSelection.setImageIcon(deselectedNodeImage);
			}
			catch (IOException e) { System.out.println("Image File not found"); }
						
			previousSelection.unclickAsDestination();			
		}
		
		try
		{
			BufferedImage dstNodeImage;
			dstNodeImage = ImageIO.read(new File("images/DestinationNode.fw.png"));
			dstNodeImage = resize(dstNodeImage, 60, 60);
			dstNode.setImageIcon(dstNodeImage);
		} 
		catch (IOException e) { System.out.println("Image File not found"); }
								
		dstNode.clickAsDestination();
		
		topoMap.repaint();
		
		return;
	}
	
	/** ReDraw node in its default state -- JJ Added **/
	void clearNode(Node n)
	{
		try
		{
			BufferedImage nodeImage;
			nodeImage = ImageIO.read(new File("images/UnselectedNode.fw.png"));
			nodeImage = resize(nodeImage, 60, 60);
			n.setImageIcon(nodeImage);
		} 
		catch (IOException e) { System.out.println("Image File not found"); }
						
		topology.repaint();
	}
	
	
	protected Coord assignCoords(int position){
		Coord c = null;
		switch(position){
			case 0: //ALBU
				c = new Coord(350, 420);
				break;
			case 1: //AOFA
				c = new Coord(960, 160);
				break;
			case 2: //ATLA
				c = new Coord(800, 450);
				break;
			case 3: //CHIC
				c = new Coord(700, 300);
				break;
			case 4: //CLEV
				c = new Coord(820, 230);
				break;
			case 5: //DENV
				c = new Coord(375, 300);
				break;
			case 6: //ELPA
				c = new Coord(390, 500);
				break;
			case 7: //HOUS
				c = new Coord(550, 525);
				break;
			case 8: //KANS
				c = new Coord(520, 335);
				break;
			case 9: //NASH
				c = new Coord(740, 380);
				break;
			case 10: //NEWY
				c = new Coord(975, 250);
				break;
			case 11: //PNWG
				c = new Coord(124, 50);
				break;
			case 12: //SDSC
				c = new Coord(100, 400);
				break;
			case 13: //STAR
				c = new Coord(680, 220);
				break;
			case 14: //SUNN
				c = new Coord(65, 275);
				break;
			case 15: //WASH
				c = new Coord(910, 300);
				break;
		}
		return c;
	}
	
	void placeNodes(Node node, BackgroundPanel panel){
		node.setBounds(node.getCoords().getX(), node.getCoords().getY(), 60, 60);
		panel.add(node);
		panel.addNode(node);
		return;
	}
	
	void drawLinks(BackgroundPanel topology){
		// 0 - ALBU, 1 - AOFA, 2 - ATLA, 3 - CHIC, 4 - CLEV, 5 - DENV, 6 - ELPA, 7 - HOUS, 8 - KANS, 9 - NASH, 10 - NEWY, 11 - PNWG, 12 - SDSC, 13 - STAR, 14 - SUNN, 15 - WASH
		int PNWG_X = nodes.get(nodeNames.indexOf("PNWG")).getCenterCoords().getX();
		int PNWG_Y = nodes.get(nodeNames.indexOf("PNWG")).getCenterCoords().getY();
		int SUNN_X = nodes.get(nodeNames.indexOf("SUNN")).getCenterCoords().getX();
		int SUNN_Y = nodes.get(nodeNames.indexOf("SUNN")).getCenterCoords().getY();
		int ALBU_X = nodes.get(nodeNames.indexOf("ALBU")).getCenterCoords().getX();
		int ALBU_Y = nodes.get(nodeNames.indexOf("ALBU")).getCenterCoords().getY();
		int AOFA_X = nodes.get(nodeNames.indexOf("AOFA")).getCenterCoords().getX();
		int AOFA_Y = nodes.get(nodeNames.indexOf("AOFA")).getCenterCoords().getY();
		int ATLA_X = nodes.get(nodeNames.indexOf("ATLA")).getCenterCoords().getX();
		int ATLA_Y = nodes.get(nodeNames.indexOf("ATLA")).getCenterCoords().getY();
		int CHIC_X = nodes.get(nodeNames.indexOf("CHIC")).getCenterCoords().getX();
		int CHIC_Y = nodes.get(nodeNames.indexOf("CHIC")).getCenterCoords().getY();
		int CLEV_X = nodes.get(nodeNames.indexOf("CLEV")).getCenterCoords().getX();
		int CLEV_Y = nodes.get(nodeNames.indexOf("CLEV")).getCenterCoords().getY();
		int DENV_X = nodes.get(nodeNames.indexOf("DENV")).getCenterCoords().getX();
		int DENV_Y = nodes.get(nodeNames.indexOf("DENV")).getCenterCoords().getY();
		int ELPA_X = nodes.get(nodeNames.indexOf("ELPA")).getCenterCoords().getX();
		int ELPA_Y = nodes.get(nodeNames.indexOf("ELPA")).getCenterCoords().getY();
		int HOUS_X = nodes.get(nodeNames.indexOf("HOUS")).getCenterCoords().getX();
		int HOUS_Y = nodes.get(nodeNames.indexOf("HOUS")).getCenterCoords().getY();
		int KANS_X = nodes.get(nodeNames.indexOf("KANS")).getCenterCoords().getX();
		int KANS_Y = nodes.get(nodeNames.indexOf("KANS")).getCenterCoords().getY();
		int NASH_X = nodes.get(nodeNames.indexOf("NASH")).getCenterCoords().getX();
		int NASH_Y = nodes.get(nodeNames.indexOf("NASH")).getCenterCoords().getY();
		int NEWY_X = nodes.get(nodeNames.indexOf("NEWY")).getCenterCoords().getX();
		int NEWY_Y = nodes.get(nodeNames.indexOf("NEWY")).getCenterCoords().getY();
		int SDSC_X = nodes.get(nodeNames.indexOf("SDSC")).getCenterCoords().getX();
		int SDSC_Y = nodes.get(nodeNames.indexOf("SDSC")).getCenterCoords().getY();
		int STAR_X = nodes.get(nodeNames.indexOf("STAR")).getCenterCoords().getX();
		int STAR_Y = nodes.get(nodeNames.indexOf("STAR")).getCenterCoords().getY();
		int WASH_X = nodes.get(nodeNames.indexOf("WASH")).getCenterCoords().getX();
		int WASH_Y = nodes.get(nodeNames.indexOf("WASH")).getCenterCoords().getY();
		
		//(PNWG, SUNN)
		topology.addLink(PNWG_X-7, PNWG_Y-7, SUNN_X-7, SUNN_Y-7, Color.black, "PNWG_SUNN_port-1_port-1", "SUNN_PNWG_port-1_port-1");
		topology.addLink(PNWG_X+7, PNWG_Y+7, SUNN_X+7, SUNN_Y+7, Color.black, "PNWG_SUNN_port-2_port-2", "SUNN_PNWG_port-2_port-2");
		//(PNWG, STAR)
		topology.addLink(PNWG_X, PNWG_Y, STAR_X, STAR_Y, Color.black, "PNWG_STAR_port-4_port-1", "STAR_PNWG_port-1_port-4");
		//(PNWG, DENV)
		topology.addLink(PNWG_X, PNWG_Y, DENV_X, DENV_Y, Color.black, "PNWG_DENV_port-3_port-1", "DENV_PNWG_port-1_port-3");
		//(SUNN, SDSC)
		topology.addLink(SUNN_X, SUNN_Y, SDSC_X, SDSC_Y, Color.black, "SDSC_SUNN_port-1_port-3", "SUNN_SDSC_port-3_port-1");
		//(SUNN, DENV)
		topology.addLink(SUNN_X, SUNN_Y, DENV_X, DENV_Y, Color.black, "DENV_SUNN_port-2_port-4", "SUNN_DENV_port-4_port-2");
		//(SUNN, ELPA)
		topology.addLink(SUNN_X, SUNN_Y, ELPA_X, ELPA_Y, Color.black, "ELPA_SUNN_port-1_port-5", "SUNN_ELPA_port-5_port-1");
		//(DENV, KANS)
		topology.addLink(DENV_X-10, DENV_Y-10, KANS_X-10, KANS_Y-10, Color.black, "DENV_KANS_port-4_port-1", "KANS_DENV_port-1_port-4");
		topology.addLink(DENV_X+10, DENV_Y+10, KANS_X+10, KANS_Y+10, Color.black, "DENV_KANS_port-5_port-2", "KANS_DENV_port-2_port-5");
		//(DENV, ALBU)
		topology.addLink(DENV_X, DENV_Y, ALBU_X, ALBU_Y, Color.black, "DENV_ALBU_port-3_port-1", "ALBU_DENV_port-1_port-3");
		//(ALBU, ELPA)
		topology.addLink(ALBU_X, ALBU_Y, ELPA_X, ELPA_Y, Color.black, "ALBU_ELPA_port-2_port-2", "ELPA_ALBU_port-2_port-2");
		//(ELPA, HOUS)
		topology.addLink(HOUS_X, HOUS_Y, ELPA_X, ELPA_Y, Color.black, "ELPA_HOUS_port-3_port-1", "HOUS_ELPA_port-1_port-3");
		//(HOUS, KANS)
		topology.addLink(HOUS_X, HOUS_Y, KANS_X, KANS_Y, Color.black, "HOUS_KANS_port-2_port-3", "KANS_HOUS_port-3_port-2");
		//(HOUS, ATLA)
		topology.addLink(HOUS_X, HOUS_Y, ATLA_X, ATLA_Y, Color.black, "HOUS_ATLA_port-3_port-3", "ATLA_HOUS_port-3_port-3");
		//(ATLA, NASH)
		topology.addLink(NASH_X, NASH_Y-12, ATLA_X, ATLA_Y-12, Color.black, "ATLA_NASH_port-1_port-3", "NASH_ATLA_port-3_port-1");
		topology.addLink(NASH_X, NASH_Y+12, ATLA_X, ATLA_Y+12, Color.black, "ATLA_NASH_port-2_port-4", "NASH_ATLA_port-4_port-2");
		//(NASH, CHIC)
		topology.addLink(NASH_X-17, NASH_Y-17, CHIC_X-17, CHIC_Y-17, Color.black, "NASH_CHIC_port-1_port-5", "CHIC_NASH_port-5_port-1");
		topology.addLink(NASH_X+17, NASH_Y+17, CHIC_X+17, CHIC_Y+17, Color.black, "NASH_CHIC_port-2_port-6", "CHIC_NASH_port-6_port-2");
		//(CHIC, KANS)
		topology.addLink(KANS_X-7, KANS_Y-7, CHIC_X-7, CHIC_Y-7, Color.black, "CHIC_KANS_port-3_port-4", "KANS_CHIC_port-4_port-3");
		topology.addLink(KANS_X+7, KANS_Y+7, CHIC_X+7, CHIC_Y+7, Color.black, "CHIC_KANS_port-4_port-5", "KANS_CHIC_port-5_port-4");
		//(CHIC, CLEV)
		topology.addLink(CLEV_X-7, CLEV_Y-7, CHIC_X-7, CHIC_Y-7, Color.black, "CHIC_CLEV_port-7_port-1", "CLEV_CHIC_port-1_port-7");
		topology.addLink(CLEV_X+7, CLEV_Y+7, CHIC_X+7, CHIC_Y+7, Color.black, "CHIC_CLEV_port-8_port-2", "CLEV_CHIC_port-2_port-8");		
		//(CHIC, STAR)
		topology.addLink(STAR_X-12, STAR_Y-12, CHIC_X-12, CHIC_Y-12, Color.black, "CHIC_STAR_port-1_port-2", "STAR_CHIC_port-2_port-1");
		topology.addLink(STAR_X+12, STAR_Y+12, CHIC_X+12, CHIC_Y+12, Color.black, "CHIC_STAR_port-2_port-3", "STAR_CHIC_port-3_port-2");				
		//(CLEV, AOFA)
		topology.addLink(CLEV_X-7, CLEV_Y-7, AOFA_X-7, AOFA_Y-7, Color.black, "CLEV_AOFA_port-4_port-1", "AOFA_CLEV_port-1_port-4");
		topology.addLink(CLEV_X+7, CLEV_Y+7, AOFA_X+7, AOFA_Y+7, Color.black, "CLEV_AOFA_port-5_port-2", "AOFA_CLEV_port-2_port-5");		
		//(NEWY, AOFA)
		topology.addLink(AOFA_X-10, AOFA_Y-10, NEWY_X-10, NEWY_Y-10, Color.black, "NEWY_AOFA_port-3_port-5", "AOFA_NEWY_port-5_port-3");
		topology.addLink(AOFA_X+10, AOFA_Y+10, NEWY_X+10, NEWY_Y+10, Color.black, "NEWY_AOFA_port-2_port-4", "AOFA_NEWY_port-4_port-2");	
		//(NEWY, WASH)
		topology.addLink(NEWY_X, NEWY_Y, WASH_X, WASH_Y, Color.black, "NEWY_WASH_port-1_port-6", "WASH_NEWY_port-6_port-1");
		//(WASH, ATLA)
		topology.addLink(ATLA_X-7, ATLA_Y-7, WASH_X-7, WASH_Y-7, Color.black, "WASH_ATLA_port-4_port-4", "ATLA_WASH_port-4_port-4");
		topology.addLink(ATLA_X+7, ATLA_Y+7, WASH_X+7, WASH_Y+7, Color.black, "WASH_ATLA_port-5_port-5", "ATLA_WASH_port-5_port-5");
		//(WASH, CLEV)
		topology.addLink(CLEV_X, CLEV_Y, WASH_X, WASH_Y, Color.black, "WASH_CLEV_port-1_port-3", "CLEV_WASH_port-3_port-1");
		//(STAR, WASH)
		topology.addLink(STAR_X, STAR_Y, WASH_X, WASH_Y, Color.black, "STAR_WASH_port-4_port-2", "WASH_STAR_port-2_port-4");
		//(WASH, AOFA)
		topology.addLink(AOFA_X, AOFA_Y, WASH_X, WASH_Y, Color.black, "AOFA_WASH_port-3_port-3", "WASH_AOFA_port-3_port-3");
	}
	
	void updateRequestTree(JTree tree){
		mpGriList = mpUIController.getMPGRIsAsStrings();
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Requests");
		for(int i = 0; i < mpGriList.size(); i++){
			DefaultMutableTreeNode mpNode = new DefaultMutableTreeNode(mpGriList.get(i));
			Object[] uniGriObjects = mpUIController.getGroupedGRIs(mpGriList.get(i));
			for(int j = 0; j < uniGriObjects.length; j++){
				mpNode.add(new DefaultMutableTreeNode(uniGriObjects[j].toString()));
			}
			root.add(mpNode);
		}
		tree.setModel(new DefaultTreeModel(root));
		return;
	}

	public static BufferedImage resize(BufferedImage image, int width, int height) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TRANSLUCENT);
		Graphics2D g2d = (Graphics2D) bi.createGraphics();
		g2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY));
		g2d.drawImage(image, 0, 0, width, height, null);
		g2d.dispose();
		return bi;
	}
	
	protected class Coord{
		int x;
		int y;
		public Coord(int x, int y){
			this.x = x;
			this.y = y;
		}
		
		public int getX(){
			return this.x;
		}
		
		public int getY(){
			return this.y;
		}

	}
	
	protected class Node extends JLabel{
		private String name;
		private Coord coordinates;
		private ImageIcon imageIcon;
		private Coord centerCoordinates;
		private boolean srcClick = false;
		private boolean dstClick = false;
		
		public Node(String name, Coord coordinates, BufferedImage image){
			this.name = name;
			this.coordinates = coordinates;
			this.imageIcon = new ImageIcon(image);
			this.setSize(new Dimension(this.imageIcon.getIconWidth(), this.imageIcon.getIconWidth()));
			this.setText(name);
			this.setIcon(imageIcon);
			this.setIconTextGap(-45);
			this.centerCoordinates = new Coord(coordinates.getX()+this.getIcon().getIconWidth()/2, coordinates.getY()+this.getIcon().getIconHeight()/2);
		}
		
		public Coord getCoords(){
			return this.coordinates;
		}
		
		public String getName(){
			return this.name;
		}
		
		public ImageIcon getImageIcon(){
			return this.imageIcon;
		}
		
		public Coord getCenterCoords(){
			return this.centerCoordinates;
		}
		
		public boolean isClickedAsSource()
		{
			return srcClick;
		}
		
		public void clickAsSource()
		{
			srcClick = true;
		}
		
		public void unclickAsSource()
		{
			srcClick = false;
		}

		public void clickAsDestination()
		{
			dstClick = true;
		}
		
		public void unclickAsDestination()
		{
			dstClick = false;
		}
		
		public boolean isClickedAsDestination()
		{
			return dstClick;
		}
				
		public void setImageIcon(BufferedImage newImage)
		{
			this.imageIcon = new ImageIcon(newImage);
			this.setIcon(imageIcon);
		}
	}
	
	protected class BackgroundPanel extends JPanel{

		private ArrayList<Link> links = new ArrayList<Link>();
		private ArrayList<Node> nodes = new ArrayList<Node>();

		public void addLink(int x1, int x2, int x3, int x4, Color color, String id1, String id2) {
			links.add(new Link(x1,x2,x3,x4, color, id1, id2));        
		    repaint();
		}
		
		public void addNode(Node newNode)
		{
			nodes.add(newNode);
		}

		public void clearLinks() {
		    links.clear();
		    repaint();
		}
		
		public void resetLinks()
		{
			for(Link eachLink : this.links)
			{
				changeColor(Color.black, eachLink.getID1());
			}
		}
		
		public void resetNodes()
		{
			previousSrcNode = null;
			previousDstNode = null;
			
			if(currentSrcNode != null)
				currentSrcNode.unclickAsSource();
			
			if(currentDstNode != null)
				currentDstNode.unclickAsDestination();
						
			for(Node eachNode : this.nodes)
			{
				clearNode(eachNode);
			}
		}

		public ArrayList<Link> getLinks(){
			return this.links;
		}
		
		public ArrayList<Node> getNodes()
		{
			return this.nodes;
		}
		
		public void changeColor(Color color, String id){
			for(int i = 0; i < links.size(); i++){
				if(links.get(i).getID1().equals(id) || links.get(i).getID2().equals(id)){
					links.get(i).setColor(color);
				}
			}
			repaint();
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g;
		    super.paintComponent(g2);
		    try {
		    	g2.drawImage(ImageIO.read(new File("images/US.png")), 0, 0, null);
		    	g2.drawImage(ImageIO.read(new File("images/UML.png")), 50, 465, Color.WHITE, null);
		    	g2.drawImage(ImageIO.read(new File("images/Esnet_Logo.png")), 830, 520, Color.WHITE, null);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    for (Link link : links) {
		    	g2.setColor(link.color);
		    	g2.setStroke(new BasicStroke(5));
		    	g2.drawLine(link.x1, link.y1, link.x2, link.y2);
		    }
		}
	}
	
	protected class Link{
		private int x1; 
	    private int y1;
	    private int x2;
	    private int y2;   
	    private Color color;
	    private String id1;
	    private String id2;

	    public Link(int x1, int y1, int x2, int y2, Color color, String id1, String id2) {
	        this.x1 = x1;
	        this.y1 = y1;
	        this.x2 = x2;
	        this.y2 = y2;
	        this.color = color;
	        this.id1 = id1;
	        this.id2 = id2;
	    }        
	    
	    public void setColor(Color color){
	    	this.color = color;
	    }
	    
	    public String getID1(){
	    	return this.id1;
	    }
	    
	    public String getID2(){
	    	return this.id2;
	    }
	}

	public void selectARequest(String gri) {
		// TODO Auto-generated method stub
	    @SuppressWarnings("unchecked")
	    TreePath tp = null;
	    DefaultMutableTreeNode root = (DefaultMutableTreeNode)griTree.getModel().getRoot();
	    Enumeration<DefaultMutableTreeNode> e = root.depthFirstEnumeration();
	    while (e.hasMoreElements()) {
	        DefaultMutableTreeNode node = e.nextElement();
	        if (node.toString().equalsIgnoreCase(gri)) {
	           tp =  new TreePath(node.getPath());
	        }
	    }
	    if(tp!= null){
	    	griTree.setSelectionPath(tp);
	    	System.out.println(griTree.getSelectionPath());
	    }
	}
	
}
