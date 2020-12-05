VBAPPlayback {
	var <>group, <>folder, <>vbapPoints, <>vbapSpeakerArray, <>panBus, <>location, <>azi, <>ele, files, synths, vbapBuf, bufs, <>soundFile, <>sliceFrames, vbapFile;

	*new {arg group, folder, vbapPoints, vbapSpeakerArray;
		^super.new.group_(group).folder_(folder).vbapPoints_(vbapPoints).vbapSpeakerArray_(vbapSpeakerArray).init;
	}

	init {
		var temp;
		folder.postln;
		files = folder.files.select{|file| file.postln;(file.extension=="wav")};

		files.addAll(folder.files.select{|file| file.postln;(file.extension=="aif")});

		files.postln;

		soundFile = SoundFile.new;
		soundFile.openRead(files[0].fullPath);

		azi = List.fill(files.size, {0});
		ele = List.fill(files.size, {0});

		if(vbapSpeakerArray!=nil){
			vbapBuf = vbapSpeakerArray.loadToBuffer(group.server);
		}{
			"no speaker array provided. setting to Quad".postln;
			this.setQuad;
		};
		SynthDef("vbapSoundObject"++vbapSpeakerArray.numSpeakers, {
			var aziNoise, eleNoise, out, env, azi, ele;

			eleNoise = LFNoise1.kr(1/\noiseLag.kr(24)).range(0,9);
			aziNoise = LFNoise1.kr(1/\noiseLag.kr).range(-9,9);
			out = DiskIn.ar(1, \buf.kr, 0);

			out = VBAP.ar(vbapSpeakerArray.numSpeakers, out, \vbapBuf.kr, (\azi.kr(0, \lag.kr)+aziNoise), (\ele.kr(0, \lag.kr)+eleNoise));

			env = EnvGen.kr(Env.asr(0.01, 1, 0.01), \gate.kr(1), doneAction:2);

			Out.ar(\outBus.kr(0), out*env*\mul.kr(0));
		}).send(group.server);
	}

	setQuad {
		vbapSpeakerArray = VBAPSpeakerArray.new(2, [-45, 45, -135, 135]);
		vbapBuf = vbapSpeakerArray.loadToBuffer(group.server);
	}

	setVBAPSpeakerArray {|sa|
		vbapSpeakerArray = sa;
		vbapBuf = vbapSpeakerArray.loadToBuffer(group.server);
	}

	quePlayback {|startFrame, panLocations, outBus=0|
		var mul;
		{
			panLocations.postln;
			group.set(\gate, 0);
			bufs.do{|item| item.free};
			bufs = List.newClear(0);
			files.do{|file, i|
				bufs.add(Buffer.cueSoundFile(group.server, file.fullPath, startFrame.asInteger, 1, 262144));
			};
			group.server.sync;
			synths = Array.fill(bufs.size, {|i|
				var panLoc;

				panLoc = vbapPoints.panPoints[panLocations[i.asSymbol].asInteger];

				Main.elapsedTime.postln;
				if(i==0){mul=1}{mul=0};
				Synth.newPaused("vbapSoundObject"++vbapSpeakerArray.numSpeakers, [\outBus, outBus, \vbapBuf, vbapBuf, \azi, panLoc[0], \ele, panLoc[1], \lag, 0.1, \noiseLag, 20, \buf, bufs[i], \mul, 1]);

			});
		}.fork;
	}

	startPlayback{
		synths.do{|item| item.run; Main.elapsedTime.postln};
	}

	stopPlayback {
		group.set(\gate, 0);
	}

	setPanning {|panLocations, lag=24|
		"set panning".postln;
		files.size.do{|i|
			var panLoc;
			panLoc = vbapPoints.panPoints[panLocations[i.asSymbol].asInteger];
			panLoc.post;
			synths[i].set(\azi, panLoc[0], \ele, panLoc[1], \lag, lag);
		};
		"".postln;
	}



/*panNRT {|clusterData, outFile, lr|  //send me one side of the clusterData
		var buffers, nrtServer, vbapFile, panSlices, waitTime, nrtJam, panLoc;

		SynthDef("vbapSoundObject"++vbapSpeakerArray.numSpeakers, {|noiseLag=24, buf, vbapBuf, azi = 0, ele=0, lag=0.1|
			var aziNoise, eleNoise, out;

			eleNoise = LFNoise1.kr(1/noiseLag).range(0,9);
			aziNoise = LFNoise1.kr(1/noiseLag).range(-9,9);
			out = DiskIn.ar(1, buf);

			//out = VBAP.ar(vbapSpeakerArray.numSpeakers, out, vbapBuf, Lag.kr(azi, lag)+aziNoise, Lag.kr(ele, lag)+eleNoise);

			Out.ar(Rand(0,10), out);
		}).store;

		waitTime = soundFile.duration/clusterData.size;

		if(outFile == nil){outFile = folder.parentPath++"outfile"++".wav"};

		nrtServer = Server(("nrt"++NRT_Server_ID.next).asSymbol,
			options: Server.local.options
			.numOutputBusChannels_(vbapSpeakerArray.numSpeakers)
			.numInputBusChannels_(10)
		);

		// make the pan points for all the slices



		//    make pan points
		panSlices = clusterData.collect{|slice|
			var tempDict = ();
			slice.keys.do{|key|
				slice[key].do{|item|
					tempDict.put(item.asInteger.asSymbol, key);
				}
			};
			tempDict
		};

		nrtJam = Score.new();

		vbapFile = folder.parentPath++"vbapTemp"++lr++".wav";
		vbapFile.postln;
		vbapBuf.write(vbapFile, completionMessage:{
			"bufferWritten".postln;

			vbapBuf = Buffer.new(nrtServer, 0, 1);

			nrtJam.add([0.0, vbapBuf.allocReadMsg(vbapFile, 0, -1, 1)]);

			buffers = List.fill(files.size, {Buffer.new(nrtServer, 0, 1)});

			CtkBuffer

			buffers.do{|buffer, i| nrtJam.add([0.0, buffer.allocReadChannelMsg(files[i].fullPath, 0, -1, 1)])};

			panSlices.postln;

			panSlices[0]['0'].postln;

			buffers.do{|buffer, i|
				"count ".post; i.postln;
				panLoc = vbapPoints.panPoints[panSlices[0][i.asSymbol].asInteger.postln;].postln;

				nrtJam.add([0.0, Synth.basicNew("vbapSoundObject"++vbapSpeakerArray.numSpeakers, nrtServer, 1000+i).newMsg(args:
					[\outBus, 0, \vbapBuf, vbapBuf, \azi, panLoc[0], \ele, panLoc[1], \lag, 0.1, \noiseLag, 20, \buf, buffer])]);
			};

			panSlices.do{|item, i|
				buffers.size.do{|synthNum|
					panLoc = vbapPoints.panPoints[item[synthNum.asSymbol].asInteger.postln];
					nrtJam.add([waitTime/2+(waitTime*i), ['n_set', 1000+synthNum, \azi, panLoc[0], \ele, panLoc[1], \lag, waitTime]]);
				}
			};

			nrtJam.saveToFile(folder.parentPath++"scorefile"++lr);

			nrtJam.recordNRT(
				outputFilePath: outFile.standardizePath,
				sampleRate: soundFile.sampleRate,
				headerFormat: "caf",
				sampleFormat: "int24",
				options: group.server.options,
				duration: 300,//soundFile.duration+3,
				action: {"multiChannel File written!".postln}
			);
		});
	}*/
	panNRT {|clusterData, inFile, outFile, lr|  //send me one side of the clusterData
		var buffers, nrtServer, vbapFile, panSlices, waitTime, nrtJam, panLoc, startTime;

		startTime = Main.elapsedTime;

		SynthDef("vbapSoundObject"++vbapSpeakerArray.numSpeakers, {|soundInBus = 0, noiseLag=24, buf, vbapBuf, azi = 0, ele=0, lag=0.1|
			var aziNoise, eleNoise, out;

			eleNoise = LFNoise1.kr(1/noiseLag).range(0,9);
			aziNoise = LFNoise1.kr(1/noiseLag).range(-9,9);
			out = SoundIn.ar(soundInBus);

			out = VBAP.ar(vbapSpeakerArray.numSpeakers, out, vbapBuf, Lag.kr(azi, lag)+aziNoise, Lag.kr(ele, lag)+eleNoise);

			Out.ar(0, out);
		}).store;

		waitTime = soundFile.duration/clusterData.size;

		if(outFile == nil){outFile = folder.parentPath++"outfile"++".wav"};

		nrtServer = Server(("nrt"++NRT_Server_ID.next).asSymbol,
			options: Server.local.options
			.numOutputBusChannels_(vbapSpeakerArray.numSpeakers)
			.numInputBusChannels_(50)
		);

		// make the pan points for all the slices



		//    make pan points
		panSlices = clusterData.collect{|slice|
			var tempDict = ();
			slice.keys.do{|key|
				slice[key].do{|item|
					tempDict.put(item.asInteger.asSymbol, key);
				}
			};
			tempDict
		};

		nrtJam = Score.new();

		vbapFile = folder.parentPath++"vbapTemp"++lr++".wav";
		vbapFile.postln;
		vbapBuf.write(vbapFile, headerFormat:"wav", sampleFormat: "float", completionMessage:{
			"bufferWritten".postln;

			vbapBuf = Buffer.new(nrtServer, 0, 1);

			nrtJam.add([0.0, vbapBuf.allocReadMsg(vbapFile, 0, -1, 1)]);

			/*buffers = List.fill(files.size, {Buffer.new(nrtServer, 0, 1)});


			buffers.do{|buffer, i| nrtJam.add([0.0, buffer.allocReadChannelMsg(files[i].fullPath, 0, -1, 1)])};*/

			panSlices.postln;

			panSlices[0]['0'].postln;

			files.size.do{|i|
				"count ".post; i.postln;
				panLoc = vbapPoints.panPoints[panSlices[0][i.asSymbol].asInteger.postln;].postln;

				nrtJam.add([0.0, Synth.basicNew("vbapSoundObject"++vbapSpeakerArray.numSpeakers, nrtServer, 1000+i).newMsg(args:
					[\outBus, 0, \vbapBuf, vbapBuf, \azi, panLoc[0], \ele, panLoc[1], \lag, 0.1, \noiseLag, 20, \soundInBus, i])]);
			};

			panSlices.do{|item, i|
				files.size.do{|synthNum|
					panLoc = vbapPoints.panPoints[item[synthNum.asSymbol].asInteger.postln];
					nrtJam.add([waitTime/2+(waitTime*i), ['n_set', 1000+synthNum, \azi, panLoc[0], \ele, panLoc[1], \lag, waitTime]]);
				}
			};

			nrtJam.saveToFile(folder.parentPath++"scorefile"++lr);

			nrtJam.recordNRT(
				outputFilePath: outFile.standardizePath,
				inputFilePath: inFile,
				sampleRate: soundFile.sampleRate,
				headerFormat: "W64",
				sampleFormat: "int24",
				options: group.server.options,
				duration: soundFile.duration+3,
				action: {"multiChannel File written!".postln; (Main.elapsedTime-startTime).postln}
			);
		});
	}
}

VBAPPanPoints {
	var <>pan, <>panPoints;

	*new {|pan=1|
		^super.new.pan_(pan).init;
	}

	init {
		panPoints = List.newClear(0);
		5.do{|i| panPoints.add([18+(36*i)*pan, 0])};
		3.do{|i| panPoints.add([30+(60*i)*pan, 36])};
		2.do{|i| panPoints.add([45+(90*i)*pan, 72])};
	}

	swapPoints {|p1, p2|
		var temp;

		temp = panPoints[p1];
		panPoints[p1] = panPoints[p2];
		panPoints[p2] = temp;
		this.updatePanPointSynths;
	}

	randomizePoints {
		panPoints = panPoints.scramble;
	}
}