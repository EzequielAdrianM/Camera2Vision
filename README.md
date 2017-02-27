# Camera2Vision

This sample will let you use the basic functionalities of both Camera1 API and Camera2 API, but with a Google Vision Face Detector added.
You will get all the power of Google Vision Services on your Camera1/Camera2 application.
You can also switch between Front and Back cameras!

## Usage

To change between Camera1 and Camera2, just change the boolean variable "useCamera2" true or false. Or implement your own method!

## Pre-requisites

* Android SDK 25 (or higher)
* Android Build Tools v25.0.2 (or higher)
* Android Support Repository

## Issues

Maybe you fill find out that the captured picture is flipped on some devices. You will have to read the EXIF data embedded in the picture
to acquire rotation info and flip manually with a Matrix.

## Tests

Tested and working on:
* Samsung Galaxy S4
* LG Nexus 5

Please let me know if you test with success on your devices so I can update this list.

## License

Made by Ezequiel Adrian. UNLICENSED.
Keep in mind that licensed libraries were used in this Sample.
