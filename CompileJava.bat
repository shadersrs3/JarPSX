@echo off

set CLASSPATH=class/

set Myvar=

for /r %%F in (*.java) do call set "Myvar=%%Myvar%% %%F"

echo Compiling%Myvar%

javac -d class -Xlint:unchecked %Myvar%

rem Execute a class
java Main