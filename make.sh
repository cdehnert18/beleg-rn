#!/bin/bash

# clear old .class-Files
rm -f bin/*

# generate new .class-Files
javac -cp bin -d bin src/*.java