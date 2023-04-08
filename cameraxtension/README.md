# CameraXtension

The CameraXtension library addresses some of the limitations of CameraX while providing additional
functionality.  Its advantages are:
<br>
1. Unlike CameraX, there are no [limitations](https://developer.android.com/training/camerax/architecture#combine-use-cases) on how many uses cases you can bind.
2. It provides GPU powered edge detection to make image analysis easier, as well as quality of life transformations like mirror and crop.
3. It provides images for analysis in a C friendly format for easy image processing, without having to deal with ImageProxy planes.
4. It handles rotation internally, so users don't need to account for it.  Regardless of device or camera orientation, input parameters are always relative to a top-left origin and images always come out the same way.
5. Works seamlessly with the CameraX PreviewView, so no degradation in functionality compared to CameraX.

## Integration
To use CameraXtension, add the dependency to your app's `build.gradle` file:
```
dependencies {
    implementation "io.github.jlynchsd.androidxtensions:cameraxtension:1.0.0"
}
```

Code samples available at: `AndroidXtensions/samples/src/main/java/com/androidxtensions/samples/cameraxtension/CameraSamples`

This library is inspired by the work in [Grafika](https://github.com/google/grafika) licensed under Apache 2.0
