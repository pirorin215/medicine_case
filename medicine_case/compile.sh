#!/bin/bash

COMPILE_COMMAND="arduino-cli compile --fqbn Seeeduino:nrf52:xiaonRF52840Sense medicine_case.ino"
echo "Compiling..."
echo $COMPILE_COMMAND
time $COMPILE_COMMAND

