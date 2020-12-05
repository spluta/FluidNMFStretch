
SynthDef("vbapSoundObjectTest", {|noiseLag=24, buf, vbapBuf, azi = 0, ele=0, lag=0.1|
	var aziNoise, eleNoise, out;

	eleNoise = LFNoise1.kr(1/noiseLag).range(0,9);
	aziNoise = LFNoise1.kr(1/noiseLag).range(-9,9);

	out = SoundIn.ar(49);

	out = VBAP.ar(10, out, vbapBuf, azi, azi);  //comment out this line and it sends White noise to channel 0

	Out.ar(0, out);
}).store;

//boot the server and make the temporary buffer file
s.boot;

~vbapTemp = VBAPSpeakerArray.new(3, [[-30, 0], [30, 0], [-90, 0], [90, 0], [-150, 0], [150, 0], [-45, 30], [45, 30], [-135, 30], [135, 30]]).loadToBuffer(s);

~vbapTemp.write(Platform.recordingsDir++"/vbapTemp_L.wav", headerFormat:"wav", sampleFormat: "float");


//do the nrt stuff

~nrtServer = Server(("nrt").asSymbol,
	options: Server.local.options
	.numOutputBusChannels_(10)
	.numInputBusChannels_(50)
);

~nrtJam = Score.new();

~vbapBuf = Buffer.new(~nrtServer, 0, 1);

~nrtJam.add([0.0, ~vbapBuf.allocReadMsg(Platform.recordingsDir++"/vbapTemp_L.wav", 0, -1)]);

~nrtJam.add([1.0, Synth.basicNew("vbapSoundObjectTest", ~nrtServer, 1000).newMsg(args:[\outBus, 0, \vbapBuf, ~vbapBuf, \azi, rrand(0, 180), \ele, rrand(0, 180), \lag, 0.1, \noiseLag, 20])]);

~nrtJam.recordNRT(
	outputFilePath: Platform.recordingsDir++"/testVbap2.caf",
	inputFilePath: "/Volumes/T2/NeverReallyOver/Main/Stretch100_24/NeverReallyOver_50_L.caf",
	sampleRate: 44100,
	headerFormat: "caf",
	sampleFormat: "int24",
	options: ~nrtServer.options,
	duration: 300,//soundFile.duration+3,
	action: {"multiChannel File written!".postln}
);

//have a looksy at the score file
~nrtJam.saveToFile(Platform.recordingsDir++"/scorefile_test")


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
}