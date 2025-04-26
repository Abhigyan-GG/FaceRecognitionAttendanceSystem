@echo off 
echo Installing Python dependencies... 
cd PythonBackend 
pip install -r requirements.txt 
cd .. 
 
echo Building Java application... 
cd JavaWebApp 
mvn clean package 
cd .. 
echo Installation complete! 
