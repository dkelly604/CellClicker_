import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

import javax.swing.JOptionPane;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.WaitForUserDialog;
import ij.io.FileInfo;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;



public class Flo_Counter implements PlugIn, MouseListener{
	String filename;
	String theparentDirectory;
	ImagePlus RefImage;
	int RefImageID;
	ImagePlus GFPImage;
	int GFPImageID;
	ImagePlus RFPImage;
	int RFPImageID;
	ImagePlus DICImage;
	int DICImageID;
	ImagePlus imp1;
	int imp1ID;
	int MergeImageID;
	ImagePlus MergeImage;
	int x[] = new int[500];
	int y[] = new int[500];
	int count;
	
	/*
	 * ImageJ Plugin to identify and measure fluorescent dots in individual
	 * cells. Plugin measures dot area and intensity + the distance between
	 * the 2 dots. Output is in the form of a text file for later analysis
	 * in Excel or R type software.
	 * 
	 * This plugin requires very specific image file structures to work, this 
	 * was a consequence of how the images were acquired. The images look like 
	 * 2D timelapse images but are in fact small Z stacks. The first 3 images are 
	 * a 3 step Z stack in the green channel, the fourth image is a DIC reference
	 * image and the final 3 images are a small Z stack in the red channel.
	 * 
	 * Author David Kelly
	 * 03/01/2018
	 * 
	 */
	
	
	public void run(String arg) {
		//Set up the measurements required by the plugin
		IJ.run("Set Measurements...", "area mean min max centroid redirect=None decimal=2");
		
		//Instruct user to open an OME tiff
		new WaitForUserDialog("Open Image", "Open OME.tiff Image").show();
				
		//Check for Open Image Open
		ImagePlus timp =  WindowManager.getCurrentImage();
		if (timp == null){
			String response = JOptionPane.showInputDialog("Do you want to quit y/n");
			if (response.equalsIgnoreCase("y")){
				return;
			}
			if (response.equalsIgnoreCase("n")){
				new WaitForUserDialog("Open Image", "Please open an image first.").show();
			}
		}
		
		//GET THE FILE NAME AND FILEPATH
		ImagePlus imp =  WindowManager.getCurrentImage();
		filename = imp.getTitle(); 	//Get file name
		int dotindex = filename.indexOf('.');		
		filename = filename.substring(0, dotindex + 4);
		FileInfo filedata = imp.getOriginalFileInfo();
		theparentDirectory = filedata.directory; //Get File Path for saving image of marked cells
				
		/*
		 * OME tiff image is assigned to a variable
		 * and autoscaled for processing into the single
		 * channels needed for analysis. Note, this is a
		 * very unusual order for a 3D image and requires
		 * some unusual channel sorting.
		 */
		RefImage = WindowManager.getCurrentImage();
		RefImageID = RefImage.getID();
		IJ.run(RefImage, "Set Scale...", "distance=1 known=1 pixel=1 unit=micron");
		IJ.run(RefImage, "Enhance Contrast", "saturated=0.35"); //Autoscale image
				
		SortChannels(); //Method to unpick the images from the unusual order.
		MergeColour(); //Method to make a merged colour image of the Z projected channels 
		GetPoints(); //Method gets the mouse click points used to identify interesting cells.
		
		SaveMerge();
		
		new WaitForUserDialog("Finished", "Plugin Finished").show();
		IJ.run(imp, "Close All", "");
	}
	
	public void SortChannels(){
		
		/*
		 * This method extracts the 3 channels from their
		 * strange format and Z projects the 2 colours
		 * ready for merging and analysis
		 */
		IJ.selectWindow(RefImageID);
		RefImage.setC(1);
		ImagePlus tempGFPimp = new Duplicator().run(RefImage, 1, 1, 1, 3, 1, 1);
		tempGFPimp.show();
		
		//Extract GFP channel and Z project 
		IJ.run(tempGFPimp, "Z Project...", "projection=[Max Intensity]");
		GFPImage = WindowManager.getCurrentImage();
		GFPImageID = GFPImage.getID();
		GFPImage.setTitle("GFP");
		IJ.run(GFPImage, "Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
		tempGFPimp.changes = false;
		tempGFPimp.close();
		
		//Extract DIC
		IJ.selectWindow(RefImageID);
		RefImage.setC(2);
		ImagePlus tempDICimp2 = new Duplicator().run(RefImage, 2, 2, 4, 4, 1, 1);
		tempDICimp2.show();
		DICImage = WindowManager.getCurrentImage();
		DICImageID = DICImage.getID();
		IJ.run(DICImage, "Enhance Contrast", "saturated=0.35"); //Autoscale image
		tempDICimp2.changes = false;
		tempDICimp2.close();
		
		
		//Re-Assign refimage as RFPImage and Z project
		IJ.selectWindow(RefImageID);
		RefImage.setC(3);
		ImagePlus tempRFPimp2 = new Duplicator().run(RefImage, 3, 3, 5, 7, 1, 1);
		tempRFPimp2.show();
		IJ.run(tempRFPimp2, "Z Project...", "projection=[Max Intensity]");
		RFPImage = WindowManager.getCurrentImage();
		RFPImage.setTitle("RFP");
		RFPImageID = RFPImage.getID();
		IJ.run(RFPImage, "Enhance Contrast", "saturated=0.35"); //Autoscale image
		IJ.run(RFPImage, "Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
		RefImage.changes = false;
		RefImage.close();
		tempRFPimp2.changes = false;
		tempRFPimp2.close();
	}
	
	public void MergeColour(){
		/*
		 * Method to merge the projected green and red channels
		 * so that the user can use it to select which cells are
		 * interesting
		 */
		GFPImage.setTitle("Green");
		String Image1 = GFPImage.getTitle();
		String Image2 = RFPImage.getTitle();
		String thedetails = "c1=" + Image2 + " c2=" + Image1 + " create keep";
		
		IJ.run(imp1, "Merge Channels...", thedetails);
		MergeImage = WindowManager.getCurrentImage();
		MergeImageID = MergeImage.getID();
	}
	
	public void GetPoints(){
		
		/*
		 * Method registers the mouseclicks on the cells
		 * of interest and gets the XY coordinate which 
		 * is passed onto the getGreen and getRed Methods
		 * to outline the cell and make the measurements.
		 */
		IJ.selectWindow(MergeImageID);
		ImageCanvas canvas;
		count = 0;
		canvas = MergeImage.getWindow().getCanvas(); 
		canvas.addMouseListener(this);
		new WaitForUserDialog("Click", "Click Points, Click OK to stop").show();
		getGreen();
		getRed();
		
		
	}
	
	public void getGreen(){
		
		/*
		 * Method to take XY coordinates from mouse
		 * click and place a 20 pixel wide circle around
		 * the cell of interest. Threshold the contents of
		 * the ROI, localise the dots and measure the 
		 * intensity and distance between them.
		 */
		IJ.selectWindow(GFPImageID);
		GFPImage.unlock();
		double GreenArea[] = new double [2];
		double GreenMean[] = new double [2];
		double GreenXcoor[] = new double [2];
		double GreenYcoor[] = new double [2];
		double GreenDistance;
		
		for (int z=0;z<count;z++){
			int adjX = x[z]-10;
			int adjY = y[z]-10;
			GFPImage.setRoi(new OvalRoi(adjX,adjY,20,20));
			
			//Get Max/Min threshold value from ROI
			IJ.setAutoThreshold(GFPImage, "Default dark");
			ImageProcessor ip = GFPImage.getProcessor();
			double theMax = 0;
			IJ.run(GFPImage, "Analyze Particles...", "size=4-400 pixel display clear");
			ResultsTable rtMax = new ResultsTable();
			rtMax = Analyzer.getResultsTable();
			int nummaxvals = rtMax.getCounter();
			for(int a= 0;a<nummaxvals;a++){
				double thetempMax = rtMax.getValueAsDouble(5, 0);
				if(thetempMax>theMax){
					theMax = thetempMax;
				}
			}
			double theMin = 65536;
			for(int a= 0;a<nummaxvals;a++){
				double thetempMin = rtMax.getValueAsDouble(4, 0);
				if(thetempMin<theMin){
					theMin = thetempMin;
				}
			}
			
			double stopper = theMax*0.8; //Calculte 80% of maximum intensity value
			
			//Get initial ROI measurements
			IJ.run(GFPImage, "Analyze Particles...", "size=4-400 pixel display clear");	
			ResultsTable rt = new ResultsTable();
			rt = Analyzer.getResultsTable();
			int numvals = rt.getCounter();
			
			//Check dot parameters in ROI		
			boolean tooBig = true;
			boolean tooFlat = false;
			
			/*
			 * Do/While loop to adjust threshold value to search for
			 * 2 dots in the event that the auto threshold doesn't 
			 * pick them out. Its based on the dots being in the top
			 * 20% of the pixel intensity.
			 */
			do{
				theMin = theMin+5;
				IJ.setThreshold(GFPImage, theMin, theMax);
				IJ.run("Threshold...");
				IJ.run(GFPImage, "Analyze Particles...", "size=4-400 pixel display clear");
				rt = Analyzer.getResultsTable();
				numvals = rt.getCounter();
				double [] theArea = new double [numvals];
				for (int a=0;a<numvals;a++){
					theArea[a] = rt.getValueAsDouble(0, a);
				}	
				
				//Check to see if 1 dot or both dots are bigger than 10 pixels 
				OptionalDouble contains = DoubleStream.of(theArea).max();
				double doubleval = contains.getAsDouble();
				OptionalDouble containsmin = DoubleStream.of(theArea).min();				
				double doublemin = containsmin.getAsDouble();				
				
				if(doubleval < 25 & doublemin <=10){	
					tooBig = false;
					ResultsTable GreenRTMax = new ResultsTable();
					GreenRTMax = Analyzer.getResultsTable();
					int greennummaxvals = GreenRTMax.getCounter();
					
					//2 dots present
					if (greennummaxvals == 2){
						for(int a=0;a<2;a++){
							GreenArea[a] = rtMax.getValueAsDouble(0, a);
							GreenMean[a] = rtMax.getValueAsDouble(1, a);
							GreenXcoor[a] = rtMax.getValueAsDouble(6, a);
							GreenYcoor[a] = rtMax.getValueAsDouble(7, a);
						}
						//Calculate Distance between green dots (Pythagoras theory)
						double D1 = Math.abs(GreenXcoor[0] - GreenXcoor[1]);
						double D2 = Math.abs(GreenYcoor[0] - GreenYcoor[1]);
						GreenDistance = Math.sqrt((Math.pow(D1, 2) + Math.pow(D2, 2)));	 
					
						//Output the green channel results to text file
						OutputGreenText(GreenDistance,GreenArea,GreenMean,z,greennummaxvals);
					}
					
					//Only 1 dot
					if (greennummaxvals == 1){
						GreenArea[0] = rtMax.getValueAsDouble(0, 0);
						GreenMean[0] = rtMax.getValueAsDouble(1, 0);
						GreenArea[1] = 0;
						GreenMean[1] = 0;
						
						//Set Distance between green dots to zero
						GreenDistance = 0;	
						
						OutputGreenText(GreenDistance,GreenArea,GreenMean,z,greennummaxvals);
					}
					
					
				}
	
				/*Check to see if a dot actually exists
				 * by calculating 80% of ROI max pixel
				 * value. If dot is within 80% then its 
				 * unlikely to be a real dot
				 */
				if(theMin > stopper){
					tooFlat = true;
					GreenArea[0] = 0;
					GreenMean[0] = 0;
					GreenArea[1] = 0;
					GreenMean[1] = 0;
					
					//Set Distance between green dots to zero
					GreenDistance = 0;	
					int greennummaxvals = 0;
					OutputGreenText(GreenDistance,GreenArea,GreenMean,z,greennummaxvals);
				}
				
			}while(tooBig==true & tooFlat== false);
			
		}
		
	
	}
	
	public void getRed(){
		
		/*
		 * Method to take XY coordinates from mouse
		 * click and place a 20 pixel wide circle around
		 * the cell of interestint the red channel. Threshold
		 * the contents of the ROI, localise the dots and 
		 * measure the intensity and distance between them.
		 */
		double [] RedArea = new double [2];
		double [] RedMean = new double[2];
		double [] RedXcoor = new double[2];
		double [] RedYcoor = new double[2];
	
		IJ.selectWindow(RFPImageID);
		RFPImage.unlock();
		
		for(int z=0;z<count;z++){
			int adjX = x[z]-10;
			int adjY = y[z]-10;
			RFPImage.setRoi(new OvalRoi(adjX,adjY,20,20));
			IJ.setAutoThreshold(RFPImage, "Default dark");
			IJ.run(RFPImage, "Analyze Particles...", "size=4-400 pixel display clear");
			ResultsTable rtMax = new ResultsTable();
			rtMax = Analyzer.getResultsTable();
			int nummaxvals = rtMax.getCounter();
			
			//Check there are the correct number of red cells
			if(nummaxvals > 2){
				//Overlapped with next cell
			}
			if(nummaxvals < 2){
				//Missed the cell
			}
			if(nummaxvals == 2){
				for(int a=0;a<2;a++){
					RedArea[a] = rtMax.getValueAsDouble(0, a);
					RedMean[a] = rtMax.getValueAsDouble(1, a);
					RedXcoor[a] = rtMax.getValueAsDouble(6, a);
					RedYcoor[a] = rtMax.getValueAsDouble(7, a);
				}
			}
			//Calculate Distance between red dots
			double D1 = Math.abs(RedXcoor[0] - RedXcoor[1]);
			double D2 = Math.abs(RedYcoor[0] - RedYcoor[1]);
			double RedDistance = Math.sqrt((Math.pow(D1, 2) + Math.pow(D2, 2)));
			
			OutputRedText(RedDistance,RedArea,RedMean,z); //Output text file for red channel
		}
		
		
	}
	
	public void MarkMerge(int xx, int yy){
		/*
		 * Method marks the position of every cell
		 * counted in the plugin with its corresponding 
		 * cell number so that all results can be checked 
		 * back to the cell from which they were made.
		 */
		IJ.selectWindow(MergeImageID);
		ImageProcessor ip = MergeImage.getProcessor();
		Font font = new Font("SansSerif", Font.PLAIN, 18);
		ip.setFont(font);
		ip.setColor(new Color(255, 255, 255));
		String cellnumber = String.valueOf(count + 1);
		int xpos = (int) xx;
		int ypos = (int) yy;
		ip.drawString(cellnumber, xpos, ypos);
		MergeImage.updateAndDraw();
		
	}
	
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}		
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {
		
		//Add position to merge image
		if (e.getModifiers() == MouseEvent.BUTTON1_MASK){
			int xx = e.getX();
			int yy = e.getY();
			MarkMerge(xx,yy);
		}
		
		//Get XY coordinates for mouse click
		if (e.getModifiers() == MouseEvent.BUTTON1_MASK){
			x[count] = e.getX();
			y[count] = e.getY();
			count = count + 1;	
		}
	}
		
	public void mouseEntered(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {}
	
	public void OutputRedText(double RedDistance,double [] RedArea, double [] RedMean, int z){
		
		/*
		 * Method takes the measurments made in the red channel
		 * and formats them into a text file for output to an
		 * external analysis program. The red and green channel
		 * measurments are put into the same text file for ease 
		 * of use.
		 */
		
		
		//Put Text file back into original directory
		String theColour = "Red";
		String CreateName =  theparentDirectory + filename + theColour + ".xls";
		String FILE_NAME = CreateName;
		
			try{
				FileWriter fileWriter = new FileWriter(FILE_NAME,true);
				BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);	
				bufferedWriter.newLine();	
				if(z==0){
					bufferedWriter.write(filename);
					bufferedWriter.newLine();
				}
				bufferedWriter.write("Cell " + (z+1) + " First Red Dot Area " + RedArea[0] + " First Red Dot Intensity " + RedMean[0] + " Second Red Dot Area " + RedArea[1] + " Second Red Dot Intensity " + RedMean[1] + " Red Distance = " + RedDistance);
				bufferedWriter.newLine();
				bufferedWriter.close();
			}
			catch(IOException ex) {
		    System.out.println(
		    "Error writing to file '"
		    + FILE_NAME + "'");
		    }		
		
	}
	
	public void OutputGreenText(double GreenDistance, double[] GreenArea, double[] GreenMean, int z, int greennummaxvals){
		/*
		 * Method takes the measurments made in the green channel
		 * and formats them into a text file for output to an
		 * external analysis program. The red and green channel
		 * measurments are put into the same text file for ease 
		 * of use.
		 */
		
		
		//Put Text file back into original directory
		String theColour = "Green";
		String CreateName =  theparentDirectory + filename + theColour + ".xls";
		String FILE_NAME = CreateName;
		
		try{
			FileWriter fileWriter = new FileWriter(FILE_NAME,true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);	
			bufferedWriter.newLine();	
			if(z==0){
				bufferedWriter.write(filename);
				bufferedWriter.newLine();
			}
			bufferedWriter.write("Cell " + (z+1) + " First Green Dot Area " + GreenArea[0] + " First Green Dot Intensity " + GreenMean[0] + " Second Green Dot Area " + GreenArea[1] + " Second Green Dot Intensity " + GreenMean[1] + " Green Distance = " + GreenDistance + " Dot Count = " + greennummaxvals);
			bufferedWriter.newLine();
			bufferedWriter.close();
		}
		catch(IOException ex) {
	    System.out.println(
	    "Error writing to file '"
	    + FILE_NAME + "'");
	    }	
	}
		
	public void SaveMerge(){
		/*
		 * Method saves the 2 colour merged image 
		 * complete with the numbered positions of
		 * all the cells used in the measurements
		 */
		IJ.selectWindow(MergeImageID);
		for (int z=0;z<count;z++){
			int adjX = x[z]-10;
			int adjY = y[z]-10;
			MergeImage.setRoi(new OvalRoi(adjX,adjY,20,20));
			IJ.setForegroundColor(255, 255, 0);
			IJ.run(MergeImage, "Draw", "slice");
		}
		String MergeName =  theparentDirectory + filename + ".jpg";
		IJ.saveAs(MergeImage, "Jpeg",MergeName);
	}
}


