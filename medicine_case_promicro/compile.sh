#!/opt/homebrew/bin/bash

COMPILE_COMMAND="arduino-cli compile --fqbn arduino:avr:leonardo medicine_case_promicro.ino"
echo "Compiling medicine_case_promicro..."
echo $COMPILE_COMMAND
time $COMPILE_COMMAND
