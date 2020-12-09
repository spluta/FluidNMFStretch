s.reboot

FluidKMeans

//create an instance of FluidNMFStretch
//probably best to reboot the server before loading
f = FluidNMFStretch(s, "/Volumes/StretchFiles/Mahler5Chailly/Mahler.wav")
f = FluidNMFStretch(s, "/Volumes/StretchFiles/AdagiettoShort/AdagiettoShort.wav")
f = FluidNMFStretch(s, "/Volumes/StretchFiles/AdaggietoSweden/AdagiettoHardingSweden.wav")

f = FluidNMFStretch(s, "/Volumes/Samsung_T5/Mahler3OhMench/Mahler3.wav")

f = FluidNMFStretch(s, "/Volumes/T2/NeverReallyOver/NeverReallyOver.wav")

f = FluidNMFStretch(s, "/Volumes/T2/Shorty/Shorty.wav", "/Volumes/T2/FluidNMFOutDir/")

//does the nmf component analysis, saves the N channel files in the Main folder and places each component of each channel of the NMF analysis in its own folder for inside the Chans folder. So, if the analyzed file is stereo and we analyze for 50 components, there will be 2 folders in the Chans folder, each with 50 files in them
f.nmf(50);  //this may take a while!!!

//time stretches each of the N separate NMF channels and places them in the user specified folder inside the Main folder. This folder will have as many folders as there are channels in the original file
//the stretch algorithms are run in non-real-time, so it will run all of the stretches at once (so, this may take over your computer for a while)

//the default is to have a maximum FFT size of 65536, 9 different separations of
//see the TimeStretch help file for more informatio
f.stretch(100, "Stretch100", 65536, 9, 2);

//the user can also supply an array of overlap values, one per frequency band, so that each band of the frequency spectrum has its own overlap setting
//see the TimeStretch help file for more information
f.stretch(100, "Stretch100_24", 65536, [2,2,2,2,2,2,2,4,4], 9)

Panning

//the VBAP panning algorithm of FluidNMFStretch relies upon an MFCC analysis of the NMF channels, grouping the channels into clumps, which will change over time

//get MFCC does the MFCC analysis on each NMF channel, saving the analysis to the ChanNFile folders inside the mfcc folder
//at this point, these need to happen one channel at a time
//it will say "Buffer UGen: no buffer data". THIS IS A LIE!
//it will say "Processing Cancelled" but that just means it is done

f.getMFCCChannel(0, (44100/5).asInteger, 0)
//now do channel 1
f.getMFCCChannel(1, (44100/5).asInteger, 0)

//f.getMFCCChannelParallel(1, 88200, 5)

//the mfcc analysis only needs to happen once. after, the files can be loaded with loadFileDataSets
f.loadFileDataSets((44100/5).asInteger)

f.fileDataSets[1].do{|item| item.size({|size| size.postln})}

//the MFCC analysis is in a format of corresponding to one per NMF file, but we need the format to be in frames (1 file per 2 second chunk of data), so that channels can be panned in similar groups. Thus, the data needs to be put into frame format
f.saveFrameDataSetsFromFile

//you may need to do these one channel at a time
f.saveFrameDataSetsFromFileChannel(0, (44100/5).asInteger)
f.saveFrameDataSetsFromFileChannel(1, (44100/5).asInteger)

//on each frame, the algorithm clusters the N time-stretched NMF channels into timbrel similarity
to do this, we must create clusters
f.loadFrameDataSets((44100/5).asInteger);

f.loadFrameDataSetsChan(0, (44100/5).asInteger);
f.createClusterChan(0, 10)
f.saveClusterChan(0, "clusters8820_0");


f.loadFrameDataSetsChan(1, (44100/5).asInteger);
f.createClusterChan(1, 10)
f.saveClusterChan(1, "clusters8820_1");

f.saveClusterData("clusters8820") //save these for future use - probably should add the mfcc chunk size to the file

//PLAYBACK
//now that the data has all been saved, we can load an run this at any point
//run each line independently

s.options.numOutputBusChannels_(32);

s.reboot
f = FluidNMFStretch(s, "/Volumes/T2/NeverReallyOver/NeverReallyOver.wav");
(f.loadClusterData("clusters8820");
f.setStretchFolder("Stretch100"); //choose the correct Stretch folder inside the Main folder (there can be multiple)

//give
~vbapArray = VBAPSpeakerArray.new(3, [[-30, 0], [30, 0], [-90, 0], [90, 0], [-150, 0], [150, 0], [-45, 45], [45, 45], [-135, 45], [135, 45]]);

~panPoints = List.newClear(0);
5.do{|i| ~panPoints.add([18+(36*i), 0])};
3.do{|i| ~panPoints.add([45+(30*i), 35])};
2.do{|i| ~panPoints.add([45+(90*i), 75])};

f.makePanner(~vbapArray, [VBAPPanPoints(~panPoints*[[-1,1]]), VBAPPanPoints(~panPoints)])
)

f.clusterData[1]

f.playAtSlice(0, [1], 0);

f.panNRT("version1")
