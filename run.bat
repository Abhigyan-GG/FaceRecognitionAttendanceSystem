@echo off
setlocal

echo Starting Python Backend...
start "" cmd /k "cd PythonBackend && python app.py"
echo Waiting for Python backend to initialize...
timeout /t 2 /nobreak > nul

echo Building and Starting Java Web Application...
start "" cmd /k "cd JavaWebApp && mvn clean install && mvn spring-boot:run"

echo Waiting for Spring Boot server to start on port 8080...

:wait_for_port
timeout /t 2 > nul
powershell -Command "$c=New-Object Net.Sockets.TcpClient;try{$c.Connect('localhost',8080)}catch{};if($c.Connected){exit 0}else{exit 1}"

if %errorlevel% neq 0 (
    goto wait_for_port
)

echo Spring Boot server is running!
echo Opening web application in your default browser...
start "" http://localhost:8080

echo Both services are now running!

endlocal
