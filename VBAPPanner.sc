VBAPPlayback {
	var <>group, <>folder, <>vbapPoints, <>vbapSpeakerArray, <>panBus, <>location, <>azi, <>ele, files, synths, vbapBuf, bufs, <>soundFile, <>sliceFrames;

/*	*initClass {
		StartUp.add {

			SynthDef("vbapSoundObject4", {
				var aziNoise, eleNoise, out, env, azi, ele;

				eleNoise = LFNoise2.kr(1/\lag.kr(24)).range(0,9);
				aziNoise = 0;LFNoise2.kr(1/\lag.kr).range(-9,9);
				out = DiskIn.ar(1, \buf.kr, 0);

				out = VBAP.ar(4, out, \vbapBuf.kr, (\azi.kr(0, \lag.kr)+aziNoise), (\ele.kr(0, \lag.kr)+eleNoise));

				env = EnvGen.kr(Env.asr(0.01, 1, 0.01), \gate.kr(1), doneAction:2);

				Out.ar(\outBus.kr(0), out*env);
			}).writeDefFile;
		}
	}*/

	*new {arg group, folder, vbapPoints, vbapSpeakerArray;
		^super.new.group_(group).folder_(folder).vbapPoints_(vbapPoints).vbapSpeakerArray_(vbapSpeakerArray).init;
	}

	init {
		var temp;
		folder.postln;
		files = folder.files.select{|file| file.postln;(file.extension=="wav").postln};

		files.addAll(folder.files.select{|file| file.postln;(file.extension=="aif").postln});

		files.postln;
		//soundFiles = files.collect{|file| var temp = SoundFile.new; temp.openRead(file.fullPath); temp};
		//soundFiles.postln;
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
			panLoc.postln;
			synths[i].set(\azi, panLoc[0], \ele, panLoc[1], \lag, lag);
		};
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