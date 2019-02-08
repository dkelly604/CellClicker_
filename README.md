# CellClicker_

ImageJ plugin to quantify cells selected by mouse click from user. A 20 pixel circular ROI is drawn around the cell and the 2 dots within are quantified. A text file with the dot intensities and the distance between the 2 dots is calculated for the red and green channels. The image opening section of the plugin is very specific to the images used in the study. The images it is designed to open appear as a 7 image timelapse when in fact they are 3 channel small Z stacks. The first 3 are the green channel, the 4th is DIC and the last 3 are the red channel. The image opening part of the plugin would require considerable changing to make the rest of the plugin work with more normal images.

INSTALLATION

1. Ensure that the ImageJ version is at least 1.5 and the installation has Java 1.8.0_60 (64bit) or higher installed. If not download the latest version of ImageJ bundled with Java and install it.

2. The versions can be checked by opening ImageJ and clicking Help then About ImageJ.

3. Place CellClicker_.jar into the plugins directory of your ImageJ installation, a plugin called Flo Counter should appear in the Plugins drop down menu on ImageJ.

4. Flo_Counter.java is the editable code for the plugin should improvements or changes be required.

USAGE

1. Drag the image file from its directory and drop it onto the menu bar of ImageJ

2. Click on Flo Counter in the Plugins drop down menu

3. A merged image will be created and you will be asked to click on the cells of interest

4. A circular ROI is drawn around the cell and the 2 dots within are analysed but only if they are within the top 20% of the intensity values in the region, in both channels.

5. Intensity values and distance between the 2 points are then output to a text file in the directory from which the images were opened from.

6. A .jpg image of the merged image with each cell used for the quantification numbered for cross checking any problems.

7. After each measurement you will be asked if you would like to measure another the y/n is case insensitive. 
