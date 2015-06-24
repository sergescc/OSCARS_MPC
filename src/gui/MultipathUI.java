package gui;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JList;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.JTextPane;

import org.apache.log4j.PropertyConfigurator;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.EventQueue;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.Date;

/***********************************************************************************************
* Front-end user interface for MultipathOSCARSClient.java
* This is an unofficial GUI intended for demonstration and presentation purposes.
* - Supports Unicast/Multipath createReservation, queryReservation, cancelReservation
* - Supports custom grouping operations
* - Invokes listing calls from MultipathOSCARSClient.java through the GuiController class
* 
* @author Jeremy
***********************************************************************************************/
public class MultipathUI
{
	private JButton addGroupButton;			// Completes the group ADD operation
	private JButton subGroupButton;			// Preforms group SUB operation
	private JButton groupCancelButton;		// Allows user to cancel the grouping action after clicking addGroupButton
	private JButton cancelResButton;		// Cancels the selected reservation
	private JButton createButton;			// Create a new reservation with parameters entered in varioud fields
	private JButton resetButton;			// Clear all new reservation parameter fields
		
	protected JCheckBox showAllCheckBox;		// Toggles between showing ALL Unicast GRIs and just those in the selected group
	
	private JComboBox sourceList;			// List of source nodes for new reservations
	
	private JFrame frmMultipathui;
		
	private JLabel newResPanelTitle;
	private JLabel lblSourceNode;
	private JLabel lblDestinationNodes;
	private JLabel lblReservationStartTime;
	private JLabel lblReservationEndTime;
	private JLabel lblPathCount;
	private JLabel bandwidthLabel;
	private JLabel mpGriPanelTitle;
	private JLabel uniGriPanelTitle;
	private JLabel label;
	private JLabel label_1;
	private JLabel label_2;
	private JLabel label_3;
	private JLabel label_4;
	private JLabel label_5;
	private JLabel lblSurviveCheck;
	private JLabel lblService;						// Displays the service in use
	
	private JList destinationList;			// List of destination nodes for new reservations (multiple-selections allowed)
	protected JList mpGriList;				// List for displaying all existing MP group GRIs
	protected JList uniGriList;				// List for displaying all unicast GRIs or grouped GRIs
	
	private JPanel panel;
	private JPanel panel_1;
	private JPanel panel_2;
	private JPanel panel_3;
	private JPanel panel_5;
			
	private JSeparator separator;
	private JSeparator separator_1;
	private JSeparator separator_2;
	private JSeparator separator_3;
	private JSeparator separator_4;
	private JSeparator separator_5;
	
	private JScrollPane destScroller;
	private JScrollPane mpScroller;
	private JScrollPane uniScroller;
	private JScrollPane consoleScroller;
	private JTextField numDisjointField;	// Lower bound - minimum number of destinations to reach for Multipath
	private JTextField bandwidthField;		// New reservation bandwidth parameter
	private JTextField startYear;			// New reservation start-time parameters
	private JTextField startMonth;
	private JTextField startDay;
	private JTextField startHour;
	private JTextField startMinute;
	private JTextField endYear;				// New reservation end-time paramters
	private JTextField endMonth;
	private JTextField endDay;
	private JTextField endHour;
	private JTextField endMinute;
	private JCheckBox surviveCheckbox = new JCheckBox("");	// Trigger Survivability/Multipath functionality
		
	protected JTextPane outputConsole;		// Query results will be displayed here
	
	protected int lastMPGri;					// Tracks index of the last MP-GRI selected before the "Show All" checkbox is clicked
	protected UnicastListThread listingThread;	// Parallel thread used to populate list of Unicast requests. Made global so it can be accessed in CreateThread.
	
	protected GuiController mpUIController;	// Controller for this GUI. List models are modified here.
											//   Also responsible for submitting requests to MultipathOSCARSClient.java
			
	/*****************************************************************************
	* Launch the application.
	*****************************************************************************/
	public static void main(String[] args) 
	{
		PropertyConfigurator.configure("lib/log4j.properties");	// Eliminate Logger warnings.
		
		EventQueue.invokeLater(new Runnable()
		{
			public void run() 
			{				
				try 
				{
					MultipathUI window = new MultipathUI();
					window.frmMultipathui.setVisible(true);
					
					window.resetFields();
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
			}
		});
	}

	/*****************************************************************************
	* Constructor - Launch the application.
	*****************************************************************************/
	public MultipathUI() 
	{
		mpUIController = new GuiController();
		mpUIController.getAllUnicastGRIs();		// Invoke a request to List all unicast GRIs
		mpUIController.refreshMPGriLists();		// Invoke a request to obtain all Multipath group GRIs
		
		initialize();
		
		// Constantly auto-refresh query output every 7 seconds in parallel (to eliminate lag/freezing) //
		RefreshThread refresher = new RefreshThread(this);  
		refresher.execute();
	}

	/*****************************************************************************
	* Give all widgets their initial values.
	*****************************************************************************/
	private void initialize() 
	{
		frmMultipathui = new JFrame();
		frmMultipathui.setTitle("Multipath OSCARS Client");
		frmMultipathui.setBounds(100, 100, 758, 630);
		frmMultipathui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmMultipathui.getContentPane().setLayout(null);
		
		panel = new JPanel();
		panel.setBounds(379, 0, 167, 253);
		panel.setLayout(null);
		
		panel_1 = new JPanel();
		panel_1.setBounds(544, 0, 167, 253);
		panel_1.setLayout(null);
		
		panel_2 = new JPanel();
		panel_2.setBounds(0, 0, 336, 412);
		panel_2.setLayout(null);
		
		panel_3 = new JPanel();
		panel_3.setBounds(350, 264, 395, 33);
		
		panel_5 = new JPanel();
		panel_5.setBounds(350, 264, 395, 33);
		panel_5.setVisible(false);
		
						
		// All the Label Widgets //
		newResPanelTitle = new JLabel("New Reservation");
		newResPanelTitle.setFont(new Font("Tahoma", Font.PLAIN, 14));
		newResPanelTitle.setBounds(132, 11, 109, 20);
		
		mpGriPanelTitle = new JLabel("Multipath GRI");
		mpGriPanelTitle.setFont(new Font("Tahoma", Font.PLAIN, 14));
		mpGriPanelTitle.setBounds(40, 11, 117, 17);
		
		uniGriPanelTitle = new JLabel("Grouped GRIs");
		uniGriPanelTitle.setFont(new Font("Tahoma", Font.PLAIN, 14));
		uniGriPanelTitle.setBounds(41, 11, 97, 15);
				
		lblSourceNode = new JLabel("Source Node:");
		lblSourceNode.setBounds(10, 46, 127, 14);
		
		lblDestinationNodes = new JLabel("Destination Node:");
		lblDestinationNodes.setBounds(193, 42, 139, 14);
		
		bandwidthLabel = new JLabel("Bandwidth (Mbps):");
		bandwidthLabel.setBounds(9, 103, 128, 14);
		
		lblPathCount = new JLabel("Number of Paths:");
		lblPathCount.setEnabled(false);
		lblPathCount.setBounds(10, 173, 128, 14);
		
		lblReservationStartTime = new JLabel("Reservation Start Time:");
		lblReservationStartTime.setBounds(10, 211, 177, 14);
		
		lblReservationEndTime = new JLabel("Reservation End Time:");
		lblReservationEndTime.setBounds(10, 262, 177, 14);
		
		label = new JLabel(":");
		label.setBounds(203, 234, 22, 14);
		
		label_1 = new JLabel("/");
		label_1.setBounds(93, 234, 22, 14);
		
		label_2 = new JLabel("/");
		label_2.setBounds(45, 234, 22, 14);
		
		label_3 = new JLabel("/");
		label_3.setBounds(93, 285, 22, 14);
		
		label_4 = new JLabel(":");
		label_4.setBounds(203, 285, 22, 14);
		
		label_5 = new JLabel("/");
		label_5.setBounds(45, 285, 22, 14);
		
		lblSurviveCheck = new JLabel("Survivable Reservation:");
		lblSurviveCheck.setBounds(10, 140, 157, 14);
		
		lblService = new JLabel("(Unicast)");
		lblService.setFont(new Font("Tahoma", Font.ITALIC, 14));
		lblService.setForeground(Color.LIGHT_GRAY);
		lblService.setBounds(21, 392, 94, 20);
		
		
		// All the Separator widgets //
		separator = new JSeparator();
		separator.setBounds(700, 11, 1, 566);
		
		separator_1 = new JSeparator();
		separator_1.setBounds(125, 32, 116, 1);
		
		separator_2 = new JSeparator();
		separator_2.setBounds(33, 32, 97, 1);
				
		separator_3 = new JSeparator();
		separator_3.setBounds(38, 32, 92, 1);
			
		separator_4 = new JSeparator();
		separator_4.setOrientation(SwingConstants.VERTICAL);
		separator_4.setBounds(346, 11, 1, 393);
		
		separator_5 = new JSeparator();
		separator_5.setBounds(15, 415, 702, 1);
		
		
		// All TextBox widgets //
		bandwidthField = new JTextField();
		bandwidthField.setHorizontalAlignment(SwingConstants.RIGHT);
		bandwidthField.setText("0");
		bandwidthField.setColumns(10);
		bandwidthField.setBounds(116, 100, 51, 20);
		
		numDisjointField = new JTextField();
		numDisjointField.setEnabled(false);
		numDisjointField.setEditable(false);
		numDisjointField.setHorizontalAlignment(SwingConstants.RIGHT);
		numDisjointField.setText("1");
		numDisjointField.setBounds(145, 170, 22, 20);
		numDisjointField.setColumns(10);
		
		startMonth = new JTextField();
		startMonth.setHorizontalAlignment(SwingConstants.CENTER);
		startMonth.setText("MM");
		startMonth.setColumns(2);
		startMonth.setBounds(10, 231, 28, 20);
		
		startDay = new JTextField();
		startDay.setHorizontalAlignment(SwingConstants.CENTER);
		startDay.setText("DD");
		startDay.setColumns(2);
		startDay.setBounds(55, 231, 28, 20);
		
		startYear = new JTextField();
		startYear.setHorizontalAlignment(SwingConstants.CENTER);
		startYear.setText("YYYY");
		startYear.setBounds(103, 231, 51, 20);
		startYear.setColumns(4);
			
		startHour = new JTextField();
		startHour.setHorizontalAlignment(SwingConstants.CENTER);
		startHour.setText("HH");
		startHour.setColumns(2);
		startHour.setBounds(164, 231, 28, 20);
		
		startMinute = new JTextField();
		startMinute.setHorizontalAlignment(SwingConstants.CENTER);
		startMinute.setText("mm");
		startMinute.setColumns(2);
		startMinute.setBounds(212, 231, 28, 20);
						
		endMonth = new JTextField();
		endMonth.setHorizontalAlignment(SwingConstants.CENTER);
		endMonth.setText("MM");
		endMonth.setColumns(2);
		endMonth.setBounds(10, 282, 28, 20);
				
		endDay = new JTextField();
		endDay.setHorizontalAlignment(SwingConstants.CENTER);
		endDay.setText("DD");
		endDay.setColumns(2);
		endDay.setBounds(55, 282, 28, 20);
		
		endYear = new JTextField();
		endYear.setHorizontalAlignment(SwingConstants.CENTER);
		endYear.setText("YYYY");
		endYear.setColumns(4);
		endYear.setBounds(103, 282, 51, 20);
		
		endHour = new JTextField();
		endHour.setHorizontalAlignment(SwingConstants.CENTER);
		endHour.setText("HH");
		endHour.setColumns(2);
		endHour.setBounds(164, 282, 28, 20);
		
		endMinute = new JTextField();
		endMinute.setHorizontalAlignment(SwingConstants.CENTER);
		endMinute.setText("mm");
		endMinute.setColumns(2);
		endMinute.setBounds(213, 282, 28, 20);
		
		// Output Console widgets //
		outputConsole = new JTextPane();
		outputConsole.setBounds(10, 425, 710, 155);
		outputConsole.setText("Welcome to the Multipath Client User Interface!");
		outputConsole.setEditable(false);
		
		consoleScroller = new JScrollPane();
		consoleScroller.setBounds(10, 425, 710, 155);
		consoleScroller.setViewportView(outputConsole);
		
		// Source Node List widgets //
		sourceList = new JComboBox(mpUIController.getTopologyNodes());
		sourceList.setBounds(10, 69, 157, 20);
		sourceList.addItemListener(new ItemListener(){	public void itemStateChanged(ItemEvent arg0)	// State change listener for source list 
		{			
			changeSourceNodeSelection();
		}});
		for (int i=0; i<sourceList.getComponentCount(); i++) 
		{
			//~ Mouse Listener Events for all parts of the source node list 
			sourceList.getComponent(i).addMouseListener(new MouseAdapter(){@Override public void mouseEntered(MouseEvent arg0) 
			{
					refreshSourceNodeList();
			}});
		}
				
		// Destination Node List widgets //		
		destinationList = new JList(mpUIController.getTopologyNodes());	
		destinationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		destinationList.setBounds(177, 71, 137, 192);
		
		destScroller = new JScrollPane();
		destScroller.setBounds(177, 71, 149, 129);
		destScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		destScroller.setViewportView(destinationList);
		
		
		// Multipath group GRI List widgets //
		mpGriList = new JList(mpUIController.getMPGRIs());
		mpGriList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		mpGriList.setBounds(10, 63, 147, 179);
		mpGriList.addListSelectionListener(new ListSelectionListener(){public void valueChanged(ListSelectionEvent arg0) 
		{
			changeMPGriSelection();
		}});
		
		mpScroller = new JScrollPane();
		mpScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		mpScroller.setBounds(10, 63, 147, 179);		
		mpScroller.setViewportView(mpGriList);
		
		
		// Grouped/Unicast GRI List widgets //
		uniGriList = new JList();
		uniGriList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		uniGriList.setBounds(16, 63, 141, 179);
		uniGriList.addListSelectionListener(new ListSelectionListener(){public void valueChanged(ListSelectionEvent arg0) 
		{
			changeUniGriSelection();
		}});

		uniScroller = new JScrollPane();
		uniScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		uniScroller.setBounds(16, 63, 141, 179);
		panel_1.add(uniScroller);
		uniScroller.setViewportView(uniGriList);
		
		showAllCheckBox = new JCheckBox("Show all");
		showAllCheckBox.setBounds(23, 37, 97, 23);
		showAllCheckBox.addItemListener(new ItemListener(){public void itemStateChanged(ItemEvent e) 
		{		        
			clickCheckBox();
		}});

		
		// Button widgets //
		createButton = new JButton("Create Reservation");
		createButton.setBounds(10, 351, 144, 30);
		createButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			createReservation();
		}});
		
		resetButton = new JButton("Reset Fields");
		resetButton.setBounds(181, 351, 139, 30);
		resetButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			resetFields();
		}});
		
		cancelResButton = new JButton("Cancel Reservation");
		cancelResButton.setEnabled(false);
		cancelResButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			cancelReservation();
		}});
		
		addGroupButton = new JButton("Add to Group");
		addGroupButton.setEnabled(false);
		addGroupButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0)
		{
			addToGroup();			
		}});
				
		groupCancelButton = new JButton("Cancel Grouping");
		groupCancelButton.setEnabled(true);
		groupCancelButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			endAddToGroupWidgets();
		}});
				
		subGroupButton = new JButton("Sub from Group");
		subGroupButton.setEnabled(false);
		subGroupButton.addActionListener(new ActionListener(){public void actionPerformed(ActionEvent arg0) 
		{
			subFromGroup();
		}});
						
		
		// Checkbox Widget //
		surviveCheckbox.setHorizontalAlignment(SwingConstants.RIGHT);
		surviveCheckbox.setBounds(145, 136, 22, 20);
		surviveCheckbox.addItemListener(new ItemListener(){public void itemStateChanged(ItemEvent arg0)
		{
			triggerSurvivability();
		}});
		
		
		// Build the Panel widgets //
		panel.add(mpGriPanelTitle);
		panel.add(separator_2);
		panel.add(mpScroller);
		
		panel_1.add(separator_3);
		panel_1.add(uniGriPanelTitle);
		panel_1.add(showAllCheckBox);
		
		panel_2.add(separator_1);
		panel_2.add(newResPanelTitle);
		panel_2.add(lblSourceNode);		
		panel_2.add(lblDestinationNodes);
		panel_2.add(bandwidthLabel);
		panel_2.add(lblPathCount);
		panel_2.add(lblReservationStartTime);
		panel_2.add(lblReservationEndTime);
		panel_2.add(label);
		panel_2.add(label_1);
		panel_2.add(label_2);
		panel_2.add(label_3);
		panel_2.add(label_4);
		panel_2.add(label_5);
		panel_2.add(lblSurviveCheck);
		panel_2.add(lblService);
		panel_2.add(bandwidthField);
		panel_2.add(numDisjointField);
		panel_2.add(startMonth);
		panel_2.add(startDay);
		panel_2.add(startYear);
		panel_2.add(startHour);
		panel_2.add(endMonth);
		panel_2.add(endDay);
		panel_2.add(endYear);
		panel_2.add(endHour);
		panel_2.add(endMinute);
		panel_2.add(startMinute);
		panel_2.add(sourceList);
		panel_2.add(destScroller);
		panel_2.add(createButton);
		panel_2.add(resetButton);
		panel_2.add(surviveCheckbox);
				
		panel_3.add(cancelResButton);
		panel_3.add(addGroupButton);
		panel_3.add(subGroupButton);
		
		panel_5.add(groupCancelButton);
		
		// Finalize the Form //
		frmMultipathui.getContentPane().add(panel);
		frmMultipathui.getContentPane().add(panel_1);
		frmMultipathui.getContentPane().add(panel_2);				
		
		frmMultipathui.getContentPane().add(panel_3);
		frmMultipathui.getContentPane().add(panel_5);
		frmMultipathui.getContentPane().add(separator);
		frmMultipathui.getContentPane().add(separator_4);
		frmMultipathui.getContentPane().add(separator_5);		
		frmMultipathui.getContentPane().add(consoleScroller);		
	}
	
		
	/*****************************************************************************
	* This is what happens when you click the "Reset Fields" button.
	* - All new parameter reservation fields will be reset to their default values.
	*****************************************************************************/
	private void resetFields()
	{
		startYear.setText("2016");
		startMonth.setText("01");
		startDay.setText("01");
		startHour.setText("00");
		startMinute.setText("00");
		
		endYear.setText("2016");
		endMonth.setText("01");
		endDay.setText("01");
		endHour.setText("01");
		endMinute.setText("00");
		
		numDisjointField.setText("1");
		
		sourceList.setSelectedIndex(-1);
		destinationList.clearSelection();
		
		bandwidthField.setText("0");
		
		surviveCheckbox.setSelected(false);
		
		numDisjointField.setText("1");		
	}
	
	/*****************************************************************************
	* This is what happens when you click the "Create Reservation" button.
	* - Submits the request to OSCARS.
	*****************************************************************************/
	private void createReservation()
	{
		String srcURN = "";				// Selected source node
		String dstURN = "";				// Selected destination node
		String startTime = "";
		String endTime = "";
		int bandwidth = 0;
		int numDisjointPaths = 0;
		CreateThread creationThread;
				
		if(!reservationParametersAreValid())	// Throw any necessary pop-up errors for bad input
			return;
		
		outputConsole.setForeground(Color.BLACK);
		outputConsole.setText("");
		outputConsole.setText("Creating reservation...");
		
		// First get the appropriate URNs for the selected src/destination //
		srcURN = sourceList.getSelectedItem().toString();
		dstURN = destinationList.getSelectedValue().toString();
				
		// Time format = YYYY-MM-DD HH:mm //
		startTime = startYear.getText() + "-" + startMonth.getText() + "-" + startDay.getText() + " " + startHour.getText() + ":" + startMinute.getText();
		endTime = endYear.getText() + "-" + endMonth.getText() + "-" + endDay.getText() + " " + endHour.getText() + ":" + endMinute.getText();
				
		bandwidth = new Integer(bandwidthField.getText()).intValue();
		numDisjointPaths = new Integer(numDisjointField.getText()).intValue();
		
		// Submit request to GuiController in a parallel thread to eliminate lag/freezing //
		creationThread = new CreateThread(this, srcURN, dstURN, startTime, endTime, bandwidth, numDisjointPaths);
		creationThread.execute();
	}
	

	/*****************************************************************************
	* Verify that reservation parameters do not violate any logical rules.
	* - Note: Most of these invalidations can be caught by OSCARS, but since the
	*   MultipathOSCARSClient doesn't directly catch, handle, and forward them all 
	*   to the end-user, it's just safer (and quicker) to handle them at the 
	*   front-end.
	* Pop-up warning dialogs are displayed indicating invalid parameters.
	*****************************************************************************/
	@SuppressWarnings("deprecation")
	public boolean reservationParametersAreValid()
	{
		String start_year = startYear.getText();
		String start_month = startMonth.getText();
		String start_day = startDay.getText();
		String start_hour = startHour.getText();
		String start_min = startMinute.getText();
		
		String end_year = endYear.getText();
		String end_month = endMonth.getText();
		String end_day = endDay.getText();
		String end_hour = endHour.getText();
		String end_min = endMinute.getText();
		
		int startYearInt;
		int startMonthInt;
		int startDayInt;
		int startHourInt;
		int startMinInt;
		int validStartDays = 31;	// Number of days in the given month, subject to change
		
		int endYearInt;
		int endMonthInt;
		int endDayInt;
		int endHourInt;
		int endMinInt;
		int validEndDays = 31;		// Number of days in the given month, subject to change

		int bandwidth;
		int numDisjointPaths;
		
		boolean shouldFail = false;	// Some of the invalid scenarios get messy. Setting this variable helps keep things cleaner.
				
		try
		{
			startYearInt = new Integer(start_year).intValue();
			startMonthInt = new Integer(start_month).intValue();
			startDayInt = new Integer(start_day).intValue();
			startHourInt = new Integer(start_hour).intValue();
			startMinInt = new Integer(start_min).intValue();
			
			endYearInt = new Integer(end_year).intValue();
			endMonthInt = new Integer(end_month).intValue();
			endDayInt = new Integer(end_day).intValue();
			endHourInt = new Integer(end_hour).intValue();
			endMinInt = new Integer(end_min).intValue();
			
			bandwidth = new Integer(bandwidthField.getText()).intValue();
			numDisjointPaths = new Integer(numDisjointField.getText()).intValue();
		}
		catch(NumberFormatException notNum)		// User is entering letters, when numbers are expected!
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Why are you entering letters!?!?? STOP TRYING TO BREAK ME!");
			return false;
		}
				
		Date currentDate = new Date(System.currentTimeMillis()/1000);	// Get the current date/time. Will be used later.
		int currYear = currentDate.getYear();
		int currMonth = currentDate.getMonth();
		int currDay = currentDate.getDay();
		int currHour = currentDate.getHours();
		int currMinute = currentDate.getMinutes();
		
		// Set the number of days in the given month. May not be exceeded or the user has entered an imaginary date. //
		if(startMonthInt == 4 || startMonthInt == 6 || startMonthInt == 9 || startMonthInt == 11)
			validStartDays = 30;
		if(startMonthInt == 2)		// Account for leap-years. If it is a leap-year, February will have 29 days
		{
			if(startYearInt % 400 == 0)
				validStartDays = 29;
			else if(startYearInt % 100 == 0)
				validStartDays = 28;
			else if(startYearInt % 4 == 0)
				validStartDays = 29;
			else
				validStartDays = 28;
		}

		// Set the number of days in the given month. May not be exceeded or the user has entered an imaginary date. //
		if(endMonthInt == 4 || endMonthInt == 6 || endMonthInt == 9 || endMonthInt == 11)
			validEndDays = 30;
		if(endMonthInt == 2)	// Account for leap-years. If it is a leap-year, February will have 29 days
		{
			if(endYearInt % 400 == 0)
				validEndDays = 29;
			else if(endYearInt % 100 == 0)
				validEndDays = 28;
			else if(endYearInt % 4 == 0)
				validEndDays = 29;
			else
				validEndDays = 28;
		}
			
		// No source node specified // 
		if(sourceList.getSelectedIndex() == -1)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Please specify a Source node and port.");
			return false;
		}
		// No destination node(s) specified //
		if(destinationList.getSelectedIndices().length == 0)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Please specify at least one Destination node and port.");
			return false;
		}
		// This would result in a FAILED reservation anyways. Handle it here to avoid that. //
		if(bandwidth <= 0)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Bandwidth, must be greater than 0 Mbps.");
			return false;
		}
		// Number of requested disjoint paths must be at least 1 //
		if(numDisjointPaths <= 1 && surviveCheckbox.isSelected())
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Number of Disjoint Paths: Multipath service requires at least 2 paths");
			numDisjointField.setText("2");
			return false;
		}
		// Number of requested disjoint paths must be at least 1 //
		else if(numDisjointPaths < 1)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Number of Disjoint Paths: Must be a positive value");
			numDisjointField.setText("1");
			return false;
		}
		// Prevent user from entering imaginary times //
		if(startMinInt > 59 || startMinInt < 0 || startHourInt < 0 || startHourInt > 23) 
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Start Time set, please update and try again");
			return false;
		}
		// Prevent user from entering imaginary dates //
		if(startMonthInt > 12 || startMonthInt < 1 || startDayInt > 31 || startDayInt < 1)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Start Date set, please update and try again");
			return false;
		}
		else if(validStartDays < startDayInt)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Start Date set, please update and try again.");
			return false;
		}
		
		// Start date/time cannot be in the past! // 
		if(currYear > startYearInt)	
		{
			shouldFail = true;
		}
		else if(currYear == startYearInt)
		{
			if(currMonth > startMonthInt)
			{
				shouldFail = true;
			}
			else if(currMonth == startMonthInt)
			{
				if(currDay > startDayInt)
				{
					shouldFail = true;
				}
				else if(currDay == startDayInt)
				{
					if(currHour > startHourInt)
					{
						shouldFail = true;
					}
					else if(currHour == startHourInt)
					{
						if(currMinute > startMinInt)
						{
							shouldFail = true;
						}
					}
					
				}
			}
		}
			
		if(shouldFail)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid Start Time set: Start time must be some time in the future.");
			return false;
		}
		
		// Prevent user from entering imaginary times //			
		if(endMinInt > 59 || endMinInt < 0 || endHourInt < 0 || endHourInt > 23) 
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid End Time set, please update and try again.");
			return false;
		}
		// Prevent user from entering imaginary dates //
		if(endMonthInt > 12 || endMonthInt < 1 || endDayInt > 31 || endDayInt < 1)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid End Date set, please update and try again.");
			return false;
		}
		else if(validEndDays < endDayInt)
		{
			JOptionPane.showMessageDialog(frmMultipathui,"Invalid End Date set, please update and try again.");
			return false;
		}
		
		shouldFail = false;
		
		// End date/time cannot be earlier than start time! Start time was already checked, so this ensures that End Time is also in the future. //  
		if(startYearInt > endYearInt)
		{
			shouldFail = true;
		}
		else if(startYearInt == endYearInt)
		{
			if(startMonthInt > endMonthInt)
			{
				shouldFail = true;
			}
			else if(startMonthInt == endMonthInt)
			{
				if(startDayInt > endDayInt)
				{
					shouldFail = true;
				}
				else if(startDayInt == endDayInt)
				{
					if(startHourInt > endHourInt)
					{
						shouldFail = true;
					}
					else if(startHourInt == endHourInt)
					{
						if(startMonthInt > endMinInt)
						{
							shouldFail = true;
						}
					}
					
				}
			}
		}
			
		if(shouldFail)
		{
			JOptionPane.showMessageDialog(frmMultipathui, "Invalid End Time set: End Time must be after Start Time.");
			return false;
		}	
		
		
		return true;	// User's parameters don't break any rules, ready to reserve the request.
	}
	
	
	/*****************************************************************************
	* This is what happens when you click the "Cancel Reservation" button.
	* - Simply submits the group or subrequest to MultipathOSCARSClient to be 
	*   cancelled.
	* - Confirmation messages pop-up for the user to confirm cancellation.
	*****************************************************************************/
	private void cancelReservation()
	{
		Object mpItemSelected = mpGriList.getSelectedValue();	
		Object uniItemSelected = uniGriList.getSelectedValue();
		
		String mpGriSelected = null;		// Selected MP-GRI
		String uniGriSelected = null;		// Selected subrequest GRI (sub-group or unicast)
				
		CancelThread cancellationThread;	// Parallel thread in which to perform the cancellation to prevent lag/freezing
				
		// Both a group and a subrequest GRI are selected, which should be cancelled? //
		if(mpItemSelected != null && uniItemSelected != null)	
		{
			mpGriSelected = mpItemSelected.toString();
			uniGriSelected = uniItemSelected.toString();
						
			// Issue a pop-up for user to select cancellation action //
			Object options[] = {"Cancel Group", "Cancel Subrequest", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frmMultipathui,"Do you want to cancel Group \'" + mpGriSelected + "\' or just the Subrequest \'" + uniGriSelected + "\'?", "What Should I Cancel?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
			
			switch(choice)
			{
				case 0:		// User clicked "Cancel Group"
					outputConsole.setText("Cancelling reservation...");
					cancellationThread = new CancelThread(this, mpGriSelected, true, true, true);
					cancellationThread.execute();

					break;
				case 1:		// User clicked "Cancel Subrequest"
					outputConsole.setText("Cancelling reservation...");
					//mpUIController.cancelExistingReservation(uniGriSelected);
					cancellationThread = new CancelThread(this, uniGriSelected, true, true, false);
					cancellationThread.execute();
					
					break;
			}
		}
		// Cancel Unicast request 
		else if(mpItemSelected == null)	
		{
			uniGriSelected = uniItemSelected.toString();
			
			// Confirm cancellation with pop-up warning dialog //
			Object options[] = {"Cancel Request", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frmMultipathui,"Are you sure you want to cancel the unicast request \'" + uniGriSelected + "\'?", "Confirm Cancel?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0)		// User confirmed cancellation
			{
				outputConsole.setText("Cancelling reservation...");
				//mpUIController.cancelExistingReservation(uniGriSelected);
				cancellationThread = new CancelThread(this, uniGriSelected, false, true, false);
				cancellationThread.execute();
			}
		}
		// Cancel Group Request
		else if(uniItemSelected == null)	
		{
			mpGriSelected = mpItemSelected.toString();
			
			// Confirm cancellation with pop-up warning dialog //
			Object options[] = {"Cancel Group", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frmMultipathui,"Are you sure you want to cancel Group \'" + mpGriSelected + "\'?", "Confirm Cancel?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0)		// User confirmed cancellation
			{
				outputConsole.setText("Cancelling reservation...");
				cancellationThread = new CancelThread(this, mpGriSelected, true, false, true);
				cancellationThread.execute();
			}
		}
	}
	
	
	/*****************************************************************************
	* This is what happens when you click the "Cancel Grouping" button.
	* - Does the opposite of the above method. Re-enables all disabled widgets
	*   and hides the group selection radio buttons. 
	*****************************************************************************/
	private void endAddToGroupWidgets()
	{
		mpGriList.setEnabled(true);
		uniGriList.setEnabled(true);
		showAllCheckBox.setEnabled(true);
		sourceList.setEnabled(true);
		destinationList.setEnabled(true);
		bandwidthField.setEnabled(true);
		numDisjointField.setEnabled(true);
		startMonth.setEnabled(true);
		startDay.setEnabled(true);
		startYear.setEnabled(true);
		startHour.setEnabled(true);
		startMinute.setEnabled(true);
		endMonth.setEnabled(true);
		endDay.setEnabled(true);
		endYear.setEnabled(true);
		endHour.setEnabled(true);
		endMinute.setEnabled(true);
		createButton.setEnabled(true);
		resetButton.setEnabled(true);
		
		panel_3.setVisible(true);
		panel_5.setVisible(false);
	}
	
	
	/*****************************************************************************
	* This is what happens when you click the "Add to Group" button (2nd time).
	* Will attempt to add the selected MP-GRI or subrequest GRI to the group
	* specified below the button (near the radio buttons).
	* - If the specified destination group does not already exist, it will
	*   be created with the selected GRI added to it. If the group DOES already 
	*   exist, it will simply be appended to.
	* - If the user has selected both an MP-GRI and subrequest GRI, a pop-up
	*   will prompt user to clarify which component to add.
	*****************************************************************************/
	private void addToGroup()
	{
		Object mpItemSelected = mpGriList.getSelectedValue();
		Object uniItemSelected = uniGriList.getSelectedValue();
		
		String mpGriSelected;
		String uniGriSelected;
		String multipathClientMPGri = "";
				
		if(mpItemSelected != null)		// Add to the MP-GRI
		{
			mpGriSelected = mpItemSelected.toString();		// Selected MP-GRI
			
			Object options[] = {"Add Reservation", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frmMultipathui,"Add link-disjoint Unicast reservation to group \'" + mpGriSelected + "\'?", "Confirm Grouping?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0) // Clicked Add Reservation
			{
				outputConsole.setText("Performing group action...");
				multipathClientMPGri = mpUIController.addToGroup(mpGriSelected);
				
				if(multipathClientMPGri.equals("IMPOSSIBLE"))
				{
					JOptionPane.showMessageDialog(frmMultipathui,"Current network state does not support adding an additional disjoint reservation to " + mpGriSelected + "!");
					multipathClientMPGri = mpGriSelected;
				}
			}
		}
		else							// Clone Unicast GRI and add it to the original's group (or new group)
		{
			uniGriSelected = uniItemSelected.toString();	// Selected Unicast GRI
			
			Object options[] = {"Add Reservation", "Never Mind"};
			int choice = JOptionPane.showOptionDialog(frmMultipathui,"Add link-disjoint Unicast reservation to \'" + uniGriSelected + "\'?", "Confirm Grouping?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			
			if(choice == 0) // Clicked Add Reservation
			{
				outputConsole.setText("Performing group action...");
				multipathClientMPGri = mpUIController.addToGroup(uniGriSelected);
				
				if(multipathClientMPGri.equals("IMPOSSIBLE"))
				{
					JOptionPane.showMessageDialog(frmMultipathui,"Current network state does not support pairing " + uniGriSelected + " with an additional disjoint reservation!");
					multipathClientMPGri = uniGriSelected;
					return;
				}
			}
		}
		
		endAddToGroupWidgets();
		
		mpGriList.setListData(mpUIController.getMPGRIs());
				
		mpGriList.setSelectedValue(multipathClientMPGri, true);	// Select the destination group in MP-GRI list
		//uniGriList.setSelectedValue(newMember, true);	// Select the just-added request in the Subrequest GRI list
	}
	
	
	/*****************************************************************************
	* This is what happens when you click the "Sub from Group" button.
	* Will remove the selected Subrequest (sub-group/Unicast) GRI from the group
	*  selected in the MP-GRI list.
	*****************************************************************************/
	private void subFromGroup()
	{
		String mpGriSelected = mpGriList.getSelectedValue().toString();		// Selected MP-GRI
		String uniGriSelected = uniGriList.getSelectedValue().toString();	// Selected Subrequest GRI
				
		Object options[] = {"Remove Reservation", "Never Mind"};
		int choice = JOptionPane.showOptionDialog(frmMultipathui,"Remove reservation \'" + uniGriSelected + "\' from group \'" + mpGriSelected + "\'?", "Confirm De-Grouping?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
		
		// Remove from group //
		if(choice == 0)
		{
			outputConsole.setText("Performing group action...");
			mpUIController.subFromGroup(mpGriSelected, uniGriSelected);
		}
		
		// Update the MP-GRI in list //
		ArrayList<String> groups = mpUIController.getMPGRIsAsStrings();
		if(!groups.contains(mpGriSelected))
			outputConsole.setText("Empty group: " + mpGriSelected + " deleted.");
		
		mpGriList.setListData(mpUIController.getMPGRIs());		
				
		mpGriList.setSelectedValue(mpGriSelected, true);
	}
	
	
	/*****************************************************************************
	* This is what happens when you move the mouse over the source node list 
	* dropdown box.
	* - This behavior important ONLY if one or more destinations is selected in 
	*   the destination node list!
	* - Updates the Source list before it becomes visible to the user, and removes
	*   the selected destinations so that source != destination for new requests. 
	*****************************************************************************/
	@SuppressWarnings("deprecation")
	private void refreshSourceNodeList()
	{
		Object[] updatedSourceList;
		Object[] selectedDestItems = destinationList.getSelectedValues();
		String[] selectedDestNodes = new String[selectedDestItems.length];	// The destination nodes currently selected
		
		if(selectedDestItems.length > 0)
		{
			for(int d = 0; d < selectedDestNodes.length; d++)
				selectedDestNodes[d] = selectedDestItems[d].toString();
		
			// Track the current source selection, remove the selected destinations from source list, then reset to the current source selected. //
			Object currentSelectedSource = sourceList.getSelectedItem();
			updatedSourceList = mpUIController.updateSourceNodeList(selectedDestNodes);	// Remove selected destinations from Source list
			sourceList.setModel(new DefaultComboBoxModel(updatedSourceList));
			sourceList.setSelectedItem(currentSelectedSource);
		}
	}
	
	/*****************************************************************************
	* This is what happens when you make a selection in the source node list 
	* dropdown box.
	* - Removes selected source from set of available destination nodes and
	*   replaces it with the previously selected source.
	* - This is so that source != destination in new requests.
	*****************************************************************************/
	private void changeSourceNodeSelection()
	{
		Object sourceItem = sourceList.getSelectedItem();
		String sourceNode;
		Object[] updatedDestinationList;
		
		// Get currently selected source node //
		if(sourceItem == null)
			sourceNode = "";
		else
			sourceNode = sourceItem.toString();
						
		int[] selectedIndeces = destinationList.getSelectedIndices();
		Object[] selectedNodes = new Object[selectedIndeces.length];
		
		// Get the currently selected destination nodes //
		for(int i = 0; i < selectedIndeces.length; i++)
		{
			selectedNodes[i] = destinationList.getModel().getElementAt(selectedIndeces[i]); 
		}
		
		// Remove selected source from destination node list, and reset the destination lsit selections to what they were before the update //
		updatedDestinationList = mpUIController.updateDestinationNodeList(sourceNode);
		destinationList.setListData(updatedDestinationList);
		destinationList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		selectedIndeces = new int[selectedNodes.length];
		for(int i = 0; i < destinationList.getModel().getSize(); i++)
		{
			Object oneListMember = destinationList.getModel().getElementAt(i);
			
			for(int j = 0; j < selectedNodes.length; j++)
			{
				if(oneListMember.equals(selectedNodes[j]))
					selectedIndeces[j] = i;
			}	
		}
		
		destinationList.setSelectedIndices(selectedIndeces);
	}


	/*****************************************************************************
	* This is what happens when you make a selection in the Multipath GRI list.
	* - Deselect showAllCheckbox.
	* - Show all the subrequest GRIs in this group.
	* - Query the group and print query output to display console. 
	*****************************************************************************/
	protected void changeMPGriSelection()
	{
		Object selectedItem = mpGriList.getSelectedValue();
		String selectedMPGri;
		
		if(uniGriList.getValueIsAdjusting())
			return;
		
		if(selectedItem != null)
		{
			selectedMPGri = selectedItem.toString();	// The selected MP-GRI
			showAllCheckBox.setSelected(false);			// Hide all Unicast request GRIs
			
			// Update subrequest GRI list - for some reason, oncee just isn't enough. List is blank until the second call? //
			uniGriList.setListData(mpUIController.getGroupedGRIs(selectedMPGri));
			uniGriList.setListData(mpUIController.getGroupedGRIs(selectedMPGri));	
			
			cancelResButton.setEnabled(true);
			addGroupButton.setEnabled(true);
			
			queryGRI(true);		// Query the entire group and display output in the console.
		}
		else	// No MP-GRI is selected
		{
			uniGriList.setListData(new Object[0]);	// Empty subrequest list
			cancelResButton.setEnabled(false);
			addGroupButton.setEnabled(false);
			subGroupButton.setEnabled(false);
		}
	}

	/*****************************************************************************
	* This is what happens when you make a selection in the subrequest/unicast 
	* GRI list.
	* - Query the subrequest and print query output to display console. 
	*****************************************************************************/
	protected void changeUniGriSelection()
	{
		Object selectedItem = uniGriList.getSelectedValue();
		
		if(uniGriList.getValueIsAdjusting())
			return;
		
		if(selectedItem != null)
		{
			cancelResButton.setEnabled(true);
			addGroupButton.setEnabled(true);
			
			if(mpGriList.getSelectedValue() != null)
				subGroupButton.setEnabled(true);
			
			queryGRI(false);	// Perform query and display output to console
		}
		else // Should not occur, not possible to deselect item from the list.
		{
			uniGriList.setListData(new Object[0]);	
			cancelResButton.setEnabled(false);
			addGroupButton.setEnabled(false);
			subGroupButton.setEnabled(false);
		}
	}

	/*****************************************************************************
	* This is what happens when user or another method checks/unchecks the 
	* "Show All" checkbox.
	* 
	* IF checked:
	* - Deselect currently selected group.
	* - Empty console of output.
	* - Poplulate subrequest list with ALL unicast GRIs.
	* ELSE:
	* - Remove all unicast GRIs from subrequest list.
	* - Reselect the previously selected group and display its query output.
	*****************************************************************************/
	private void clickCheckBox()
	{
		listingThread = new UnicastListThread(this);
		listingThread.execute();	// Generate unicast GRI list in parallel to eliminate lag/freezing
		
		outputConsole.setForeground(Color.BLACK);
		outputConsole.setText("");
		
		if(showAllCheckBox.isSelected())
		{
			uniGriPanelTitle.setText("Unicast GRIs");
			outputConsole.setText("Generating list of Unicast GRIs...");
		}
		else
		{
			uniGriPanelTitle.setText("Grouped GRIs");
			outputConsole.setText("Generating list of grouped GRIs...");
		}		
	}
	
	
	/*****************************************************************************
	* This is what happens when you check/uncheck the "Survivabile Reservation" Checkbox.
	* - If the box is checked, Multipath functionality is enabled.
	* - If the box is unchecked, only Unicast functionality is supported.
	*****************************************************************************/
	protected void triggerSurvivability()
	{
		// Check the box //
		if(surviveCheckbox.isSelected())
		{
			numDisjointField.setEnabled(true);
			numDisjointField.setEditable(true);
			numDisjointField.setText("2");	
			
			lblPathCount.setEnabled(true);	
			lblPathCount.setText("Number of Paths (>2):");
			
			lblService.setText("(Multipath)");
		}
		// Uncheck the box //
		else
		{
			numDisjointField.setText("1");	
			numDisjointField.setEnabled(false);
			numDisjointField.setEditable(false);
			
			lblPathCount.setEnabled(false);	
			lblPathCount.setText("Number of Paths:");
			
			lblService.setText("(Unicast)");
		}	
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
	private void queryGRI(boolean isGroup)
	{
		QueryThread queryThread;	// Query the GRI in a parallel thread to eliminate lag/freezing
		
		outputConsole.setForeground(Color.BLACK);
		outputConsole.setText("");
		outputConsole.setText("Querying reservation...");
					    
		// Multipath vs. Subrequest //
		if(isGroup)
			queryThread = new QueryThread(mpGriList.getSelectedValue().toString(), this);
		else
			queryThread = new QueryThread(uniGriList.getSelectedValue().toString(), this);
		
		queryThread.execute();	// Perform the actual query
	}
}
