FACULTY ASSIGNMENT TRACKER - NO MAVEN VERSION

Requirements:
- JDK 21 already installed on your computer.
- No Maven, database server, or additional Java library is needed.

Run:
1. Extract this ZIP file.
2. Open the extracted folder in VS Code.
3. Open Terminal > New Terminal.
4. Make sure terminal is in the folder containing this README.txt and the src and public folders.
5. Run:
      javac -d out src/Main.java
6. Then run:
      java -cp out Main
7. Keep the terminal open and open this URL in your browser:
      http://localhost:8080

Features:
- Java backend using built-in Java HttpServer.
- Browser frontend (HTML, CSS and JavaScript).
- Publish and delete assignments.
- Student demo submission form.
- Faculty can review submissions with marks and feedback.
- Dashboard counts update from backend data.
- Data is saved locally in the data folder and remains after closing/restarting the app.

Important:
- This is a local academic prototype, not a production system.
- It does not yet include account registration/login or secure file uploads.
- To stop the server, click the terminal and press Ctrl+C.
