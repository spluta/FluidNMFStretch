# FluidNMFStretch

Dependecies: FluCoMa Plugins for SuperCollider: www.flucoma.org
TimeStretch Quark: https://github.com/spluta/TimeStretch
SuperCollider VBAP Plugins: Part of distribution

Takes a mono or stereo audio file, uses FluidBufNMF to split the file into N*2 components, time stretches (optional) those componenents using the TimeStretch quark, analyses those components over time using FluidBufMFCC, clumps those components over time using FluidKMeans and finally diffuses those N*2 components over an M-channel speaker system using VBAP.


by Sam Pluta - sampluta.com - github.com/spluta
