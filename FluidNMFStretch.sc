FluidNMFStretch {
	var <>server, <>fileIn, <>writeDir;
	var <>fileDataSets, <>frameDataSets, numChannels, <>nmfFolder, <>mfccFolder, <>stretchFolder;
	var sf, <>clusterData, <>kmeans, <>centroids, <>vbapPanPoints, <>vbapPlayback, <>vbapMaps, nrtMergeCounter;

	*new {arg server, fileIn, writeDir;
		^super.new.server_(server).fileIn_(fileIn).writeDir_(writeDir).init;
	}

	init {

		server.waitForBoot{
			var buf, bufI=0;
			"server booted".postln;
			sf = SoundFile.openRead(fileIn);
			numChannels = sf.numChannels.postln;

			fileDataSets = List.newClear(numChannels);
			frameDataSets = List.newClear(numChannels);
			clusterData = List.newClear(numChannels);
			centroids = List.newClear(numChannels);

		};
		if(writeDir==nil){
			writeDir = PathName(fileIn).pathOnly++"Main/";
			("mkdir "++writeDir).systemCmd;
		}{
			if(writeDir.isFolder.not){("mkdir "++writeDir).systemCmd};
		};
		nmfFolder = writeDir++"NMFChans/";
		mfccFolder = writeDir++"mfcc/";
	}

	nmf {|components = 50, action|
		var paths, counter = 0, numBootedServers;

		paths = List.newClear(0);

		if(nmfFolder.isFolder.not){("mkdir "++nmfFolder).postln.systemCmd};
		numChannels.do{|i|
				("mkdir "++nmfFolder++"/Chan"++i++"/").systemCmd;
			};

		numChannels.do{|bufI|
			var bufChan, resynth, bufChan2;
			"Chan ".post; bufI.postln;
			//this.makeFolders;
			resynth = Buffer.new(server);
			bufChan = Buffer.readChannel(server,fileIn, channels:[bufI],
				action:{|bufChan|
					[bufI,bufChan].postln;

					FluidBufNMF.process(server, bufChan, resynth:resynth, components: components, iterations:500, windowSize:2048,
						action:{|buf|
							buf.postln;
							//buf.write(writeDir++PathName(fileIn).fileNameWithoutExtension++"_Chan"++bufI++"_"++components++".caf", "caf", "int24");
							//extractChannels.value(buf, bufI);

							buf.numChannels.do{|i|
								var local;
								local = Buffer.new(server);
								FluidBufCompose.process(server, buf, 0, -1, i, 1, destination:local, action:{arg singleBuf;
									var path,labelNum, bufName, mfccBuf, folder;
									labelNum = i.asString;
									(4-labelNum.size.postln).do{labelNum=labelNum.insert(0, "0")};
									bufName = PathName(fileIn).fileNameWithoutExtension++"_Chan"++bufI++"_"++labelNum;
									path = nmfFolder++"Chan"++bufI++"/"++bufName++".wav";

									singleBuf.write(path);

									counter = counter+1;
									counter.postln;
									if(counter==(numChannels*components)){
										"all NMFed".postln;
										action.value;
									}
								})
							};
					})
			});
		};

		//}.fork
	}

	panNRT {|outFile, format = "w64", chans, everyNPoints=1|
		var lr =  ["_L", "_R"];
		var clusterDataRedux;

		if(everyNPoints<2){
			clusterDataRedux = clusterData;
		}{
			clusterDataRedux = List.newClear(clusterData.size);
			clusterDataRedux.size.do{|arrayNum|
				var temp;
				temp=clusterData[arrayNum].select{|item, i| ((i%everyNPoints)==0)};
				clusterDataRedux.put(arrayNum, temp);
			}
		};

		if(chans==nil){chans = [0,1]};

		if(clusterDataRedux==nil){"load cluster data and make panner first"}{
			chans.do{|chan| vbapPlayback[chan].panNRT(clusterDataRedux[chan], stretchFolder++"/"++outFile++lr[chan]++"."++format, format, "_"++outFile++lr[chan])}
		};
	}

	stretch {|durMult=12, stretchDestFolder="Stretch", fftMax=65536, overlaps=2, numSplits=9|
		var inFiles, x, chanFolders, folder;
		//[folderOrFile, durMult, stretchFolder].postln;
		{

			stretchFolder = writeDir++stretchDestFolder++"/";
			("mkdir "++stretchFolder).systemCmd;
			numChannels.do{|i|
				("mkdir "++stretchFolder++"Chan"++i++"/").systemCmd;
			};

			chanFolders = PathName(nmfFolder).folders;

			chanFolders.do{|folder, chanNum|
				var inFiles;

				inFiles = folder.files;
				inFiles.do{|inFile,i|
					var outFile;
					outFile = stretchFolder++folder.folderName++"/"++(inFile.fileName);
					TimeStretch.stretchNRT(inFile.fullPath, outFile, durMult, fftMax, overlaps, numSplits, 1);
				}
			}
	}.fork}

	makePanner {|vbapSpeakerArray, vbapPanPointsIn|
		var map, temp;

		//right now there are 10 destination points per side. should this be changeable?

		vbapPanPoints = vbapPanPointsIn;
		//vbapPanPoints = [VBAPPanPoints(-1), VBAPPanPoints(1)];
		vbapPlayback = List.fill(2, {|i|
			"panPoints".postln;
			VBAPPlayback(Group.tail(server), PathName(stretchFolder++"/""Chan"++i++"/"), vbapPanPoints[i].postln, vbapSpeakerArray)
		});
	}

	saveVBAPMaps {|fileName = "vbapMaps"|
		fileName = writeDir++fileName;
		vbapMaps.writeArchive(fileName);
	}

	loadVBAPMaps {|fileName = "vbapMaps"|
		fileName = writeDir++fileName;
		vbapMaps = Object.readArchive(fileName);
	}

	playAtFrame {|frameNum, chans, outBus = 0|
		var framesPerSlice = vbapPlayback[0].soundFile.numFrames/clusterData[0].size;
		var waitTime = vbapPlayback[0].soundFile.duration/clusterData[0].size;

		if(chans==nil) {chans = (0..(vbapPlayback.size-1))};
		chans.postln;
		"Frame Num: ".post; frameNum.postln;

		if(frameNum>(clusterData[0].size-1)){"there are not that many frames".postln}{
			chans.do{|i|
				var tempDict = ();
				var vbap = vbapPlayback[i];

				clusterData[i][frameNum].keys.do{|key| clusterData[i][frameNum][key].do{|item| tempDict.put(item.asInteger.asSymbol, key)}};

				vbap.quePlayback(frameNum*framesPerSlice, tempDict, outBus)
			};
			Routine({
				1.wait;
				chans.do{|i|
					"play ".post;Main.elapsedTime.postln;
					vbapPlayback[i].startPlayback;
				};
				((frameNum+1)..(clusterData[0].size-1)).do{|frameNum|
					server.sync;
					"Slice Num: ".post; frameNum.postln;
					chans.do{|i|
						var tempDict = ();
						var vbap = vbapPlayback[i];
						clusterData[i][frameNum].keys.do{|key|
							clusterData[i][frameNum][key].do{|item|
								tempDict.put(item.asInteger.asSymbol, key);
							}
						};
						tempDict.postln;
						vbap.setPanning(tempDict, waitTime.postln);
					};
					waitTime.wait;
					//10.wait;
				};
			}).play
		}
	}

/*	loadFileDataSets {|frameLength|
		fileDataSets.do{|item| item.do{|item2|item2.free}};
		numChannels.do{|chan|
			this.loadFileDataSetsChan(chan, frameLength);
		}
	}*/

	loadMFCCChannel {|chanNum, frameLength|
		var temp = List.newClear(0);
		fileDataSets[chanNum].do{|item|item.free};
		//fileDataSets = List.fill(numChannels, {List.newClear(0)});
		//numChannels.do{|i|
		PathName(mfccFolder++frameLength++"/"++"/Chan"++chanNum++"File/").files.do{|file, i|
			temp.add(FluidDataSet(server, file.fileNameWithoutExtension).read(file.fullPath))
			//}
		};
		fileDataSets.put(chanNum, temp);
	}

	loadFrameDataSets {|frameLength|
		frameDataSets.do{|item| item.do{|item2|item2.free}};
		numChannels.do{|chan|
			this.loadFrameDataSetsChan(chan, frameLength);
		}
	}

	loadFrameDataSetsChan {|chanNum, frameLength|
		var temp = List.newClear(0);

		frameDataSets[chanNum].do{|item|item.free};

		PathName(mfccFolder++frameLength++"/"++"/Chan"++chanNum++"Frame/").files.size.postln;

		{
		PathName(mfccFolder++frameLength++"/"++"/Chan"++chanNum++"Frame/").files.do{|file, i|
				var set = FluidDataSet(server, chanNum.asString++"-"++file.fileNameWithoutExtension);
				file.postln;
				i.postln;
				0.3.wait;
				temp.add(set.read(file.fullPath));
		};
		}.fork;
		frameDataSets.put(chanNum, temp);
	}

	saveClusterData {|fileName = "clusters"|
		numChannels.do{|chan|
			this.saveClusterChannel(chan, fileName)
		}
	}

	saveClusterChannel {|chan=0, fileName = "clusters"|
		fileName = writeDir++fileName;
		clusterData[0].writeArchive(fileName++"_"++chan);
	}

	loadClusterData {|fileName = "clusters"|
		fileName = writeDir++fileName;
		clusterData = List.newClear(2);
		clusterData.put(0, Object.readArchive(fileName++"_0"));
		clusterData.put(1, Object.readArchive(fileName++"_1"));
	}

	createClusters {
		numChannels.do{|chan|
			this.createClusterChan(chan, 10);
		}
	}

	setStretchFolder {|folder|
		stretchFolder = writeDir++folder;
	}

	createClusterChan {|chanNum, numClusters=10|
		var frameData, temp;

		//if(fileName==nil){fileName = writeDir++"clusters_"++chanNum};

		kmeans = FluidKMeans.new(server, numClusters);

		frameData = frameDataSets[chanNum];
		if(frameData==nil){"load frame data first!".postln;}
		{
			temp = List.newClear(frameData.size);
			frameData[0].size({|numAudioFiles|
				numAudioFiles = numAudioFiles.asInteger.postln;
				{
					frameData.do{|frame, frameNum|
						var frameClusters, groups;

						[frame, frameNum].postln;
						groups = Dictionary();
						numClusters.do{|i| groups.put(i.asSymbol, List[])};
						groups.postln;
						frameClusters = FluidLabelSet(server, ("kmeans"++chanNum++"-"++frameNum).asSymbol);

						server.sync;
						0.2.wait;
						kmeans.fitPredict(frame, frameClusters, numClusters, action: {|c|
							{
								numAudioFiles.do{|i|
									var labelNum;
									labelNum = i.asString;
									(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};

									//labelNum.postln;
									frameClusters.getLabel(labelNum, {|val|
										groups[val.asSymbol].add(labelNum)
									});
									server.sync;
									0.1.wait;
								};
								temp.put(frameNum, groups);

							}.fork
						});
						0.2.wait;
					};
					server.sync;
					0.1.wait;
					temp.postln;
					clusterData.put(chanNum, temp);
					server.sync;
					"done clustering".postln;
				}.fork
			})
		}
	}

	saveClusterChan {|chanNum, fileName="clusters_0"|
		clusterData[chanNum].writeArchive(writeDir++fileName);
	}

/*	getMFCC {|frameLength = 88200|
		var numChanFolders = PathName(nmfFolder).folders.size;
		numChanFolders.do{|chanNum|
			this.getMFCCChannel(chanNum, frameLength);
		}
	}*/

	getMFCCChannel {|chanNum, frameLength = 88200, counterStart = 0|
		var doit;
		var files, counter, oscy, folder, leBeuf, countTo;

		"getMFCCChannel ".post; chanNum.postln;

		if(mfccFolder.isFolder.not){("mkdir "++mfccFolder).systemCmd};

		if((mfccFolder++frameLength.asString++"/").isFolder!=true){
			("mkdir "++mfccFolder++frameLength.asString++"/").systemCmd;
			numChannels.do{|i|
				("mkdir "++mfccFolder++frameLength.asString++"/Chan"++i++"File/").systemCmd;
				("mkdir "++mfccFolder++frameLength.asString++"/Chan"++i++"Frame/").systemCmd;
			};
		};

		folder = nmfFolder++"Chan"++chanNum++"/";
		folder.postln;

		files = PathName(folder).files;
		files.size.postln;

		//sf = SoundFile(files[0].fullPath);
		fileDataSets.do{|item| item.do{item.free}};
		frameDataSets.do{|item| item.do{item.free}};

		fileDataSets.put(chanNum, List.fill(files.size, {|i| FluidDataSet(server, (chanNum.asString++"-"++i).asSymbol)}));

		countTo = sf.numFrames/frameLength-1;

		doit = {|file, i|
			var mfccBuf, statsBuf;

			file.postln;

			leBeuf = Buffer.read(server,file.fullPath.postln, action:{|audioBuf|

				var mfccBuf = Buffer.new(server);
				var statsBuf = Buffer.new(server);
				audioBuf.postln;
				{

					var trig, buf, count, mfcc, stats, rd, wr1, dsWr, endTrig;

					trig = LocalIn.kr(1, 1);
					buf =  LocalBuf(19, 1);
					count = (PulseCount.kr(trig, 0) - 1);
					mfcc = FluidBufMFCC.kr(audioBuf, count*frameLength, frameLength, features:mfccBuf, numCoeffs:20, trig: trig);
					stats = FluidBufStats.kr(mfccBuf, 0, -1, 1, 19, statsBuf, trig: Done.kr(mfcc));
					rd = BufRd.kr(19, statsBuf, DC.kr(0), 0, 1);// pick only mean pitch and confidence
					wr1 = Array.fill(19, {|i| BufWr.kr(rd[i], buf, DC.kr(i))});
					dsWr = FluidDataSetWr.kr(fileDataSets[chanNum][i], buf: buf, trig: Done.kr(stats));
					LocalOut.kr(Done.kr(dsWr));
					endTrig = count - countTo;
					SendTrig.kr(endTrig, chanNum);
					FreeSelf.kr(endTrig);
				}.play;
			})
		};

		counter = counterStart;
		doit.value(files[counter], counter);

		oscy = OSCFunc({|msg|
			msg.postln;
			if(msg[2]==chanNum){
				var labelNum;
				"Made It! Don't listen to those errors".postln;
				leBeuf.free;

				labelNum = counter.asString;
				(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
				fileDataSets[chanNum][counter].write(mfccFolder++frameLength.asString++"/"++"Chan"++chanNum++"File/"++labelNum++".json");

				counter = counter+1;
				counter.postln;
				if(files.size>counter) {
					"again, again!".postln;
					doit.value(files[counter], counter)
				}
				{
					//"shaping to time sets".postln;
					oscy.free;

					//save and set
					"save file data sets".postln;
					//this.saveFileDataSets(chanNum, frameLength);
				}
			}
		},'/tr')
	}

	saveFileDataSets {|chanNum, frameLength|
		fileDataSets[chanNum].do{|item, i|
			var labelNum;
			labelNum = i.asString;
			(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
			item.write(mfccFolder++frameLength.asString++"/"++"Chan"++chanNum++"File/"++labelNum++".json")
		};
	}

	saveFrameDataSetsFromMFCCChannel {|chanNum, frameLength|
		var setSize, name;
		Routine({
			fileDataSets[chanNum][0].size({|val|
				setSize = val.asInteger.postln;
			});
			2.wait;
			server.sync;
			setSize.postln;
/*			setSize.do{|val|
				val.postln;
				1.wait;*/

				frameDataSets.put(chanNum, Array.fill(setSize.asInteger, {|i|
					name = ("rotated"++chanNum++"_"++i).asSymbol.postln;
					FluidDataSet.new(server, name);
				//0.01.wait;
			})
				);
		// };

			fileDataSets[chanNum][0].size({|val|
				Routine({
				val.asInteger.do{|point|
					point = (point.asInteger);
					point.postln;
					0.01.wait;
					fileDataSets[chanNum].do{|dataSet, i|
						var labelNum, buf, label, localPoint;
						//[dataSet, i].postln;
						0.01.wait;≥
						labelNum = i.asString;
						(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
						label = (labelNum++point).asSymbol;
						localPoint = point.asSymbol;
						buf = Buffer.new(server);

						dataSet.getPoint(localPoint, buf, action:{
							var temp;
							//buf.postln;
							frameDataSets[chanNum][point].addPoint(labelNum, buf, {buf.free});
							//buf.free;
						});
					};

				};
				"write files".postln;
				{
					frameDataSets[chanNum].do{|item, i|
						var labelNum;
						0.05.wait;
						labelNum = i.asString;
						(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
						item.write(mfccFolder++frameLength++"/"++"Chan"++chanNum++"Frame/"++labelNum++".json")
					};
				}.fork
				}).play;
			})
		}).play;

	}
}
